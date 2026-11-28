"""Behavioral regression checks for mutation evidence and the supported execution interface."""
from pathlib import Path
import tempfile
import unittest
import json
import copy
import os
import signal
import subprocess
import shutil
import sys
import time
import zipfile
import mutation
from mutation_evidence import MethodCode


def resolved_fixture():
    """Literal resolved protocol for adversarial file-I/O tests; it is not execution evidence."""
    settings = dict.fromkeys(('excludedMethods', 'excludedClasses', 'excludedTestClasses', 'includedGroups',
                             'excludedGroups', 'includedTestMethods', 'skipFailingTests'))
    settings.update(targetTests=['dev.samhb.interleave.*'], failWhenNoMutations='true', mutators=[
        'CONDITIONALS_BOUNDARY', 'NEGATE_CONDITIONALS', 'MATH', 'INVERT_NEGS', 'INCREMENTS',
        'VOID_METHOD_CALLS', 'NON_VOID_METHOD_CALLS', 'PRIMITIVE_RETURNS', 'RETURNS', 'FALSE_RETURNS',
        'TRUE_RETURNS', 'EMPTY_RETURNS'])
    return {'protocol': {'javaMajor': 26, 'pit': '1.30.0', 'junit': '1.2.3', 'settings': settings, 'gradle': 'fixture', 'java': '26.0.1',
                         'vendor': 'fixture', 'childArgs': [], 'coordinatorArgs': [], 'launchJars': [], 'buildPlugins': [], 'features': [],
                         'mutablePaths': ['build/classes/java/main'],
                         'runtime': {'java.specification.version': '26', 'java.runtime.version': 'fixture', 'java.vendor': 'fixture'},
                         'coordinatorRuntime': {'java.specification.version': '26'}},
            'history': False, 'targets': sorted(mutation.DEFAULT_SCOPE),
            'classpathDirectories': ['build/classes/java/main', 'build/classes/java/test'],
            'classpathOrder': ['build/classes/java/main', 'build/classes/java/test'], 'runtimeArtifacts': {}, 'dependencies': []}


class EvidenceTest(unittest.TestCase):
    """Reject misleading PIT evidence before any comparison can call it preserved."""

    def test_report_preserves_ordered_identity_and_raw_status(self):
        """Source lines are diagnostics; instruction/block coordinates identify mutants."""
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "mutations.xml"
            report.write_text('<mutations><mutation status="NO_COVERAGE">'
                              '<mutatedClass>example.A</mutatedClass><mutatedMethod>run</mutatedMethod>'
                              '<methodDescription>()V</methodDescription><mutator>Math</mutator>'
                              '<indexes><index>2</index><index>1</index></indexes>'
                              '<blocks><block>3</block></blocks></mutation></mutations>')
            self.assertEqual({('example.A', 'run', '()V', 'Math', (2, 1), (3,)): 'NO_COVERAGE'},
                             mutation.read_report(report))

    def test_duplicate_truncated_empty_entity_and_resource_reports_fail(self):
        """No malformed or non-assertion evidence can become a green population."""
        row = ('<mutation status="KILLED"><mutatedClass>A</mutatedClass>'
               '<mutatedMethod>m</mutatedMethod><methodDescription>()V</methodDescription>'
               '<mutator>Math</mutator><indexes><index>1</index></indexes>'
               '<blocks><block>0</block></blocks></mutation>')
        invalid = [f'<mutations>{row}{row}</mutations>', '<mutations>', '<mutations/>',
                   '<!DOCTYPE mutations><mutations/>',
                   f'<mutations>{row.replace("KILLED", "TIMED_OUT")}</mutations>',
                   f'<mutations>{row.replace("<index>1</index>", "")}</mutations>']
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'bad.xml'
            for source in invalid:
                report.write_text(source)
                with self.subTest(source=source), self.assertRaises((ValueError, mutation.ET.ParseError)):
                    mutation.read_report(report)

    def test_entity_declarations_cannot_hide_in_utf16(self):
        """Alternative byte encodings must not bypass the declaration rejection."""
        source = ('<!DOCTYPE mutations [<!ENTITY owner "A">]><mutations><mutation status="KILLED">'
                  '<mutatedClass>&owner;</mutatedClass><mutatedMethod>run</mutatedMethod>'
                  '<methodDescription>()V</methodDescription><mutator>Math</mutator>'
                  '<indexes><index>1</index></indexes><blocks><block>0</block></blocks></mutation></mutations>')
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'declaration.xml'
            for encoding in ('utf-8', 'utf-16', 'utf-16-le'):
                report.write_bytes(source.encode(encoding))
                with self.subTest(encoding=encoding), self.assertRaises(ValueError):
                    mutation.read_report(report)

    def test_json_rejects_duplicate_keys_nonfinite_numbers_and_nonobjects(self):
        """A parser must not choose a favorable duplicate field or accept non-JSON numbers."""
        with tempfile.TemporaryDirectory() as directory:
            file = Path(directory) / 'bad.json'
            for source in ('{"version":1,"version":2}', '{"value":NaN}', 'null', '[]'):
                file.write_text(source)
                with self.subTest(source=source), self.assertRaises(ValueError):
                    mutation.load_json(file)

    def test_duplicate_scalar_identity_fields_are_not_a_valid_row(self):
        """A single counted row cannot choose one of two contradictory method identities."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = root / 'mutations.xml'
            report.write_text('<mutations><mutation status="KILLED">'
                '<mutatedClass>A</mutatedClass><mutatedMethod>first</mutatedMethod>'
                '<mutatedMethod>second</mutatedMethod><methodDescription>()V</methodDescription>'
                '<mutator>Math</mutator><indexes><index>1</index></indexes>'
                '<blocks><block>0</block></blocks></mutation></mutations>')
            (root / 'run.log').write_text('>> Generated 1 mutations Killed 1 (100%)\n'
                                         '>> Mutations with no coverage 0.\nBUILD SUCCESSFUL\n')
            record = {'version': 1, 'complete': True, 'exit': 0, 'scope': 'affected', 'targets': ['A'],
                      'reportHash': mutation.fingerprint(report)}
            with self.assertRaisesRegex(ValueError, 'scalar identity'):
                mutation.completed_rows(root, record)


class ComparisonTest(unittest.TestCase):
    """Compare actual operands without erasing target behavior or new methods."""

    def test_pool_normalization_preserves_hash_digit_strings(self):
        """Only the pool operand may change; resolved literal text remains significant."""
        before = mutation.normalized_code(['0: ldc #12 // String #12', '2: areturn'])
        relocation = mutation.normalized_code(['0: ldc_w #34 // String #12', '3: areturn'])
        changed = mutation.normalized_code(['0: ldc #12 // String #34', '2: areturn'])
        self.assertEqual(before, relocation)
        self.assertNotEqual(before, changed)

    def test_bootstrap_data_is_unmatched(self):
        """A descriptor alone cannot establish unchanged dynamic behavior."""
        self.assertIsNone(mutation.normalized_code(['0: invokedynamic #12, 0 // InvokeDynamic #0:run:()V']))
        self.assertIsNone(mutation.normalized_code(['0: ldc #12 // float NaNf', '2: freturn']))

    def test_added_methods_and_lost_kills_are_reported(self):
        """Both inventories matter, even when an added method generates no mutations."""
        method = ('A', 'run', '()V')
        key = method + ('Math', (1,), (0,))
        added = ('B', 'added', '()V')
        code = MethodCode('return', 'same')
        result = mutation.compare_methods({method: code}, {method: code, added: code},
                                          {key: 'KILLED'}, {key: 'SURVIVED'}, protocol_verified=True)
        self.assertEqual([key], result['lost_kills'])
        self.assertEqual({"('KILLED', 'SURVIVED')": 1}, result['improvements'])
        self.assertEqual([{'class': 'B', 'method': 'added', 'descriptor': '()V', 'change': 'added',
                           'baseline_mutants': 0, 'candidate_mutants': 0}], result['unmatched_methods'])

    def test_unverified_protocol_cannot_establish_a_lost_or_preserved_kill(self):
        """The historical four-argument comparison has no authority to match PIT coordinates."""
        method = ('A', 'run', '()V')
        key = method + ('Math', (1,), (0,))
        code = MethodCode('return', 'same')
        result = mutation.compare_methods({method: code}, {method: code}, {key: 'KILLED'}, {key: 'SURVIVED'})
        self.assertEqual([], result['lost_kills'])
        self.assertEqual({}, result['unchanged_method_baseline'])
        self.assertEqual('unresolved-protocol', result['unmatched_methods'][0]['change'])

    def test_historical_cli_reports_unverified_differences_without_failing(self):
        """Invoke the real legacy command with identical classes but unverified changed PIT identities."""
        home = Path(os.environ.get('JAVA_HOME', '/usr')) / 'bin'
        javac = str(home / 'javac') if (home / 'javac').exists() else 'javac'
        javap = str(home / 'javap') if (home / 'javap').exists() else 'javap'
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'A.java'
            source.write_text('public class A { public void run() {} }')
            subprocess.run([javac, '--release', '26', '-d', str(root), str(source)], check=True,
                           stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            before = ('<mutations><mutation status="KILLED"><mutatedClass>A</mutatedClass>'
                '<mutatedMethod>run</mutatedMethod><methodDescription>()V</methodDescription>'
                '<mutator>Math</mutator><indexes><index>1</index></indexes>'
                '<blocks><block>0</block></blocks></mutation></mutations>')
            after = before.replace('KILLED', 'SURVIVED').replace('<index>1', '<index>2')
            originals = {}
            for name, content in [('baseline-default.xml', before), ('baseline-affected.xml', before),
                                  ('final-default.xml', after), ('affected-census.xml', after), ('current.xml', after)]:
                path = root / name
                path.write_text(content)
                originals[name] = mutation.fingerprint(path)
            legacy = Path(mutation.__file__).resolve().parents[1] / 'docs/measurements/94C-compare-mutations.py'
            subprocess.run([sys.executable, '-B', str(legacy), str(root), str(root), str(root), '--javap', javap,
                            '--replacement-report', 'current.xml'], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            report = mutation.load_json(root / 'comparison-inherited.json')
            self.assertEqual([], report['lost_kills'])
            self.assertTrue(any(row['method'] == 'run' and row['change'] == 'unresolved-protocol'
                                for row in report['unmatched_methods']))
            self.assertEqual(originals, {name: mutation.fingerprint(root / name) for name in originals})

    def test_constructor_and_class_named_method_are_separate_inventory_entries(self):
        """Real javap declarations distinguish the constructor from an ordinary void A() method."""
        home = Path(os.environ.get('JAVA_HOME', '/usr')) / 'bin'
        javac = str(home / 'javac') if (home / 'javac').exists() else 'javac'
        javap = str(home / 'javap') if (home / 'javap').exists() else 'javap'
        with tempfile.TemporaryDirectory() as directory:
            inventories = []
            for label, extra in [('before', ''), ('after', 'public void A() {}')]:
                root = Path(directory) / label
                root.mkdir()
                source = root / 'A.java'
                source.write_text('public class A { public A() {} ' + extra + '}')
                subprocess.run([javac, '--release', '26', '-d', str(root), str(source)], check=True,
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                inventories.append(mutation.compiled_methods(root, javap=javap))
            self.assertEqual({('A', '<init>', '()V'), ('A', 'A', '()V')}, set(inventories[1]))
            result = mutation.compare_methods(*inventories, {}, {}, protocol_verified=True)
            added = [row for row in result['unmatched_methods'] if row['change'] == 'added']
            self.assertEqual([{'class': 'A', 'method': 'A', 'descriptor': '()V', 'change': 'added',
                               'baseline_mutants': 0, 'candidate_mutants': 0}], added)

    def test_actual_compiled_inventories_preserve_strings_and_added_methods(self):
        """Independent javac/javap fixtures test the real parser, not a fabricated disassembly shape."""
        home = Path(os.environ.get('JAVA_HOME', '/usr')) / 'bin'
        javac = str(home / 'javac') if (home / 'javac').exists() else 'javac'
        javap = str(home / 'javap') if (home / 'javap').exists() else 'javap'
        with tempfile.TemporaryDirectory() as directory:
            roots = []
            for label, literal, extra in [('before', '#12', ''), ('after', '#34', 'public void added() {}')]:
                root = Path(directory) / label
                root.mkdir()
                source = root / 'A.java'
                guard = 'public ' + ('synchronized ' if label == 'after' else '') + 'void guarded() {}'
                source.write_text('public class A { public String value(){ return "' + literal + '"; }' + extra + guard + '}')
                subprocess.run([javac, '--release', '26', '-d', str(root), str(source)], check=True,
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                roots.append(root)
            before, after = [mutation.compiled_methods(root, javap=javap) for root in roots]
            self.assertNotEqual(before[('A', 'value', '()Ljava/lang/String;')],
                                after[('A', 'value', '()Ljava/lang/String;')])
            self.assertIn(('A', 'added', '()V'), after)
            self.assertNotEqual(before[('A', 'guarded', '()V')], after[('A', 'guarded', '()V')])
            result = mutation.compare_methods(before, after, {}, {}, protocol_verified=True)
            self.assertEqual(['added', 'changed-code', 'changed-code', 'unresolved-coordinates'],
                             sorted(x['change'] for x in result['unmatched_methods']))

    def test_debug_coordinate_changes_and_unresolved_bootstraps_are_explicit(self):
        """Equal disassembly cannot prove raw ASM coordinates stable across changed class bytes."""
        method = ('A', 'run', '()V')
        key = method + ('Math', (1,), (0,))
        result = mutation.compare_methods({method: MethodCode('iadd', 'old')},
            {method: MethodCode('iadd', 'new')}, {key: 'KILLED'}, {}, protocol_verified=True)
        self.assertEqual([], result['lost_kills'])
        self.assertEqual('unresolved-coordinates', result['unmatched_methods'][0]['change'])
        unresolved = MethodCode(None, 'same', 'unresolved-bootstrap-or-constant')
        result = mutation.compare_methods({method: unresolved}, {method: unresolved}, {key: 'KILLED'}, {}, protocol_verified=True)
        self.assertEqual('unresolved-bootstrap-or-constant', result['unmatched_methods'][0]['change'])
        # A disassembler omission must remain visible rather than erase reported old kills.
        result = mutation.compare_methods({}, {}, {key: 'KILLED'}, {})
        self.assertEqual('unresolved-method-inventory', result['unmatched_methods'][0]['change'])

    def test_resolved_strings_retain_trailing_spaces(self):
        """Whitespace inside a constant is behavior, even when javap prints it at the line end."""
        self.assertNotEqual(mutation.normalized_code(['0: ldc #1 // String x']),
                            mutation.normalized_code(['0: ldc #1 // String x ']))

    def test_real_debug_line_changes_do_not_become_lost_kills(self):
        """Compile unchanged arithmetic with moved line nodes and a changed trailing-space constant."""
        home = Path(os.environ.get('JAVA_HOME', '/usr')) / 'bin'
        javac = str(home / 'javac') if (home / 'javac').exists() else 'javac'
        javap = str(home / 'javap') if (home / 'javap').exists() else 'javap'
        with tempfile.TemporaryDirectory() as directory:
            inventories = []
            for label, line, literal in [('before', ' ', 'x'), ('after', '\n', 'x ')]:
                root = Path(directory) / label
                root.mkdir()
                source = root / 'A.java'
                source.write_text('public class A { public int run(int x) { int y=x+1;' + line +
                    'return y*2; } public String value() { return "' + literal + '"; }}')
                subprocess.run([javac, '-g', '--release', '26', '-d', str(root), str(source)], check=True,
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                inventories.append(mutation.compiled_methods(root, javap=javap))
            method = ('A', 'run', '(I)I')
            self.assertEqual(inventories[0][method].code, inventories[1][method].code)
            value = ('A', 'value', '()Ljava/lang/String;')
            # javap may trim trailing literal whitespace; class bytes remain the proof boundary.
            self.assertNotEqual(inventories[0][value].class_digest, inventories[1][value].class_digest)
            key = method + ('Math', (4,), (0,))
            result = mutation.compare_methods(*inventories, {key: 'KILLED'}, {}, protocol_verified=True)
            self.assertEqual([], result['lost_kills'])
            self.assertIn('unresolved-coordinates', [row['change'] for row in result['unmatched_methods']])


class PlanningTest(unittest.TestCase):
    """Assign every compiled class while binding its revision and test inputs."""

    def test_partition_is_complete_and_nested_classes_share_a_shard(self):
        """Nested classes are co-located in the same shard as their enclosing class."""
        names = ['dev.samhb.interleave.bytecode.A', 'dev.samhb.interleave.bytecode.A$Nested',
                 'dev.samhb.interleave.report.ReportWriter', 'outside.Ignored']
        groups = mutation.partition(names)
        self.assertEqual(sorted(names[:-1]), sorted(x for group in groups for x in group))
        self.assertEqual(3, sum(len(group) for group in groups))
        nested_shards = [i for i, group in enumerate(groups) if 'dev.samhb.interleave.bytecode.A' in group]
        self.assertEqual(1, len(nested_shards))
        self.assertIn('dev.samhb.interleave.bytecode.A$Nested', groups[nested_shards[0]])

    def test_class_sizes_measures_regular_class_files_and_rejects_symlinks(self):
        """Class byte sizes are accurately measured and symlink inputs rejected."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cls_a = root / 'pkg' / 'A.class'
            cls_b = root / 'pkg' / 'B.class'
            cls_a.parent.mkdir(parents=True)
            cls_a.write_bytes(b'abc')
            cls_b.write_bytes(b'12345')
            sizes = mutation.class_sizes(root)
            self.assertEqual({'pkg.A': 3, 'pkg.B': 5}, sizes)

            sym = root / 'pkg' / 'Sym.class'
            sym.symlink_to(cls_a)
            with self.assertRaisesRegex(ValueError, 'Symlink input unsupported'):
                mutation.class_sizes(root)

    def test_partition_falls_back_cleanly_when_sizes_omitted(self):
        """Partitioning without explicit sizes uses family member count fallback."""
        names = ['dev.samhb.interleave.bytecode.A', 'dev.samhb.interleave.bytecode.B',
                 'dev.samhb.interleave.bytecode.C', 'dev.samhb.interleave.bytecode.D']
        groups = mutation.partition(names)
        self.assertEqual(4, sum(1 for g in groups if g))

    def test_partition_balances_known_class_families(self):
        """Dynamic sizes place families on least-loaded shards in descending size order."""
        names = ['dev.samhb.interleave.bytecode.A',
                 'dev.samhb.interleave.bytecode.B',
                 'dev.samhb.interleave.bytecode.C',
                 'dev.samhb.interleave.bytecode.D',
                 'dev.samhb.interleave.bytecode.E']
        sizes = {'dev.samhb.interleave.bytecode.A': 6,
                 'dev.samhb.interleave.bytecode.B': 9,
                 'dev.samhb.interleave.bytecode.C': 5,
                 'dev.samhb.interleave.bytecode.D': 8,
                 'dev.samhb.interleave.bytecode.E': 7}
        groups = mutation.partition(names, sizes)
        expected = [
            ['dev.samhb.interleave.bytecode.B'],
            ['dev.samhb.interleave.bytecode.D'],
            ['dev.samhb.interleave.bytecode.E'],
            ['dev.samhb.interleave.bytecode.A', 'dev.samhb.interleave.bytecode.C']
        ]
        self.assertEqual(expected, groups)

    def test_changed_tests_cannot_reuse_another_class_old_kills(self):
        """Even a class's unchanged production bytes do not make an old test result fresh."""
        expected = {'revision': 'a' * 40, 'protocol': {'tests': ['dev.samhb.interleave.*']},
                    'inputs': {'production': {'A': 'same', 'B': 'same'}, 'tests': {'Test': 'new'},
                               'harness': {'Agent': 'same'}, 'mainResources': {}, 'testResources': {},
                               'dependencies': [], 'classpathDirectories': {}, 'classpathOrder': [], 'runtimeArtifacts': {}}}
        stale = dict(expected, inputs=dict(expected['inputs'], tests={'Test': 'old'}))
        with self.assertRaisesRegex(ValueError, 'inputs'):
            mutation.check_provenance(expected, stale)
        mutation.check_provenance(expected, dict(expected))

    def test_resolved_protocol_rejects_narrowing_history_and_suppressed_errors(self):
        """Actual configured options, rather than requested task names, determine acceptance."""
        valid = resolved_fixture()
        mutation.check_metadata(valid)
        bad = copy.deepcopy(valid)
        bad['history'] = True
        cases = [bad]
        for name, value in [('targetTests', ['OnlyOneTest']), ('excludedClasses', ['*.Important']),
                            ('mutators', ['MATH']), ('skipFailingTests', 'true'), ('failWhenNoMutations', 'false')]:
            bad = copy.deepcopy(valid)
            bad['protocol']['settings'][name] = value
            cases.append(bad)
        for case in cases:
            with self.subTest(case=case), self.assertRaises(ValueError):
                mutation.check_metadata(case)
        bad = copy.deepcopy(valid)
        bad['protocol']['coordinatorRuntime']['java.specification.version'] = '25'
        with self.assertRaisesRegex(ValueError, 'coordinator'):
            mutation.check_metadata(bad)

    def test_feature_and_classpath_order_changes_invalidate_provenance(self):
        """Identical files in a different loader order or changed filters are distinct protocols."""
        expected = {'revision': 'a' * 40, 'protocol': dict(resolved_fixture()['protocol'],
                    features=['+A', '-B'], coordinatorArgs=['-Xint']),
                    'inputs': {'production': {'A': 'same'}, 'tests': {'Test': 'same'}, 'harness': {'Agent': 'same'},
                               'mainResources': {}, 'testResources': {}, 'dependencies': [], 'classpathDirectories': {},
                               'runtimeArtifacts': {}, 'classpathOrder': [{'name': 'first.jar', 'sha256': 'first'},
                                                                         {'name': 'second.jar', 'sha256': 'second'}]}}
        mutation.check_provenance(expected, copy.deepcopy(expected))
        for field, message in [('features', 'Mismatched protocol'), ('classpathOrder', 'Mismatched inputs'),
                               ('coordinatorArgs', 'Mismatched protocol')]:
            observed = copy.deepcopy(expected)
            if field == 'classpathOrder':
                observed['inputs'][field].reverse()
            elif field == 'features':
                observed['protocol'][field].reverse()
            else:
                observed['protocol'][field] = ['-Xbatch']
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, message):
                mutation.check_provenance(expected, observed)

    def test_runtime_jar_entries_bind_payloads_without_zip_timestamps(self):
        """Manifest/payload edits invalidate inputs; reproducible logical contents ignore ZIP dates."""
        with tempfile.TemporaryDirectory() as directory:
            paths = []
            for name, year, payload in [('a', 2020, b'one'), ('b', 2024, b'one'), ('c', 2024, b'two')]:
                path = Path(directory) / (name + '.jar')
                with zipfile.ZipFile(path, 'w') as archive:
                    archive.writestr(zipfile.ZipInfo('META-INF/MANIFEST.MF', (year, 1, 1, 0, 0, 0)), payload)
                paths.append(path)
            self.assertEqual(mutation.jar_entries(paths[0]), mutation.jar_entries(paths[1]))
            self.assertNotEqual(mutation.jar_entries(paths[0]), mutation.jar_entries(paths[2]))

    def test_pr_pair_path_decision_and_manual_ancestry(self):
        """Use real Git merge parents; document-only skips do not imply mutation execution."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)

            def git(*args):
                """Drive a private fixture repository without changing any real worktree."""
                return subprocess.check_output(['git', '-C', str(root), '-c', 'user.name=Test',
                    '-c', 'user.email=test@example.invalid', *args], text=True).strip()

            git('init', '-qb', 'main')
            git('config', 'diff.renames', 'true')
            (root / 'README.md').write_text('base')
            git('add', '.')
            git('commit', '-qm', 'base')
            base = git('rev-parse', 'HEAD')
            git('update-ref', 'refs/remotes/origin/main', base)
            git('checkout', '-qb', 'feature')
            (root / 'README.md').write_text('docs')
            git('commit', '-qam', 'docs')
            head = git('rev-parse', 'HEAD')
            git('checkout', '-q', 'main')
            git('merge', '--no-ff', '-qm', 'merge', 'feature')
            candidate = git('rev-parse', 'HEAD')
            self.assertFalse(mutation.select_revisions(root, base, candidate, head)['required'])
            self.assertTrue(mutation.select_revisions(root, base, candidate, manual=True)['required'])
            with self.assertRaisesRegex(ValueError, 'main'):
                mutation.select_revisions(root, base, candidate, manual=True, dispatch_ref='refs/heads/feature')
            with self.assertRaises(ValueError):
                mutation.select_revisions(root, base, candidate, base)
            with self.assertRaises(subprocess.CalledProcessError):
                mutation.select_revisions(root, head, base, manual=True)
            git('checkout', '-qb', 'code', base)
            (root / 'tools').mkdir()
            (root / 'tools/check.py').write_text('pass')
            git('add', '.')
            git('commit', '-qm', 'code')
            head = git('rev-parse', 'HEAD')
            git('checkout', '--detach', '-q', base)
            git('merge', '--no-ff', '-qm', 'merge code', 'code')
            candidate = git('rev-parse', 'HEAD')
            self.assertTrue(mutation.select_revisions(root, base, candidate, head)['required'])
            base = candidate
            git('checkout', '-qb', 'move-base')
            git('checkout', '-qb', 'move-feature')
            (root / 'docs').mkdir()
            git('mv', 'tools/check.py', 'docs/check.py')
            git('commit', '-qm', 'move out of watched tree')
            head = git('rev-parse', 'HEAD')
            git('checkout', '-q', 'move-base')
            git('merge', '--no-ff', '-qm', 'merge move', 'move-feature')
            candidate = git('rev-parse', 'HEAD')
            self.assertTrue(mutation.select_revisions(root, base, candidate, head)['required'])


class CompletionTest(unittest.TestCase):
    """A report's presence and class inventory are not evidence that PIT completed."""

    def test_engine_counts_and_completed_process_are_required(self):
        """Reject failed, stale, missing-summary and count-mismatched executions."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            row = ('<mutation status="KILLED"><mutatedClass>A</mutatedClass>'
                   '<mutatedMethod>m</mutatedMethod><methodDescription>()V</methodDescription>'
                   '<mutator>Math</mutator><indexes><index>1</index></indexes>'
                   '<blocks><block>0</block></blocks></mutation>')
            (root / 'mutations.xml').write_text('<mutations>' + row + '</mutations>')
            log = '>> Generated 1 mutations Killed 1 (100%)\n>> Mutations with no coverage 0.\nBUILD SUCCESSFUL\n'
            record = {'version': 1, 'complete': True, 'scope': 'affected', 'exit': 0, 'targets': ['A'],
                      'empty': False, 'reportHash': mutation.fingerprint(root / 'mutations.xml')}
            (root / 'run.log').write_text(log)
            self.assertEqual(1, len(mutation.completed_rows(root, record)))
            for broken in [dict(record, exit=1), dict(record, reportHash='stale'), dict(record, complete=False)]:
                with self.assertRaises(ValueError):
                    mutation.completed_rows(root, broken)
            for broken in [log.replace('Generated 1', 'Generated 2'), '', log.replace('BUILD SUCCESSFUL', '')]:
                (root / 'run.log').write_text(broken)
                with self.assertRaises(ValueError):
                    mutation.completed_rows(root, record)

    def test_default_equivalent_status_remains_a_nonkill(self):
        """Only the successful existing default ratchet may tolerate its adjudicated equivalent."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'mutations.xml').write_text('<mutations><mutation status="EQUIVALENT">'
                '<mutatedClass>A</mutatedClass><mutatedMethod>run</mutatedMethod>'
                '<methodDescription>()V</methodDescription><mutator>Math</mutator>'
                '<indexes><index>1</index></indexes><blocks><block>0</block></blocks></mutation></mutations>')
            (root / 'run.log').write_text('>> Generated 1 mutations Killed 1 (100%)\n'
                '>> Mutations with no coverage 0.\nMutation ratchet — assertion-backed coverage\n'
                '  RESULT: PASS — 0/1 = 0% >= floor 0%\nBUILD SUCCESSFUL\n')
            record = {'version': 1, 'complete': True, 'exit': 0, 'scope': 'default', 'targets': ['A'], 'empty': False,
                      'reportHash': mutation.fingerprint(root / 'mutations.xml')}
            self.assertEqual(['EQUIVALENT'], list(mutation.completed_rows(root, record).values()))
            record['scope'] = 'affected'
            with self.assertRaises(ValueError):
                mutation.completed_rows(root, record)

    def test_exact_default_selectors_do_not_make_scoped_ratchet_evidence_full(self):
        """The full target globs can still be supplied via an override that skips aggregate gates."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'mutations.xml').write_text('<mutations><mutation status="KILLED">'
                '<mutatedClass>dev.samhb.interleave.state.A</mutatedClass><mutatedMethod>run</mutatedMethod>'
                '<methodDescription>()V</methodDescription><mutator>Math</mutator>'
                '<indexes><index>1</index></indexes><blocks><block>0</block></blocks></mutation></mutations>')
            log = ('>> Generated 1 mutations Killed 1 (100%)\n>> Mutations with no coverage 0.\n'
                   'Mutation ratchet — assertion-backed coverage\n'
                   '  SCOPED RUN (-PpitestTargetOverride) — R5 (total mutants) and R7 (floor)\n'
                   '  are NOT enforced: both are full-scope checks and this run is a subset.\nBUILD SUCCESSFUL\n')
            (root / 'run.log').write_text(log)
            record = {'version': 1, 'complete': True, 'exit': 0, 'scope': 'default',
                      'targets': sorted(mutation.DEFAULT_SCOPE), 'reportHash': mutation.fingerprint(root / 'mutations.xml')}
            with self.assertRaisesRegex(ValueError, 'full-scope ratchet'):
                mutation.completed_rows(root, record)

    def test_artifact_collection_failure_cannot_commit_gradle_success(self):
        """Run an external fixture executable that exits zero but leaves an uncopyable class archive."""
        with tempfile.TemporaryDirectory() as directory:
            root, output = Path(directory) / 'repo', Path(directory) / 'evidence'
            root.mkdir()
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            metadata = resolved_fixture()
            metadata.update(targets=['A'], threads=1)
            script = ('#!' + sys.executable + '\nimport pathlib,json,sys\n'
                'out=pathlib.Path(next(x.split("=",1)[1] for x in sys.argv if x.startswith("-PmutationMetadata=")))\n'
                'out.write_text(' + repr(json.dumps(metadata)) + ')\n'
                'pathlib.Path("build/reports/pitest").mkdir(parents=True)\n'
                'pathlib.Path("build/reports/pitest/mutations.xml").write_text("<mutations/>")\n'
                'pathlib.Path(out.parent / "classes").write_text("blocks class artifact copy")\n'
                'print("BUILD SUCCESSFUL")\n')
            (root / 'gradlew').write_text(script)
            (root / 'gradlew').chmod(0o755)
            for name in ('main/A', 'test/Test', 'mutationHarness/Agent'):
                path = root / ('build/classes/java/' + name + '.class')
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b'fixture')
            subprocess.run(['git', '-C', str(root), 'add', 'gradlew'], check=True)
            subprocess.run(['git', '-C', str(root), '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                            'commit', '-qm', 'fixture'], check=True)
            revision = mutation.git(root, 'rev-parse', 'HEAD')
            result = subprocess.run([sys.executable, '-B', str(Path(mutation.__file__)), 'ci-run',
                '--checkout', str(root), '--revision', revision, '--label', 'candidate', '--scope', 'default',
                '--output', str(output)], env=dict(os.environ, GITHUB_ACTIONS='true'), capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            record = mutation.load_json(output / 'record.json')
            self.assertEqual(0, record['exit'])
            self.assertFalse(record['complete'])
            self.assertTrue((output / 'mutations.xml').exists())
            with self.assertRaisesRegex(ValueError, 'Incomplete'):
                mutation.completed_rows(output, record)

    def test_gradle_failure_preserves_exit_code_and_collects_diagnostics(self):
        """A failing Gradle run records its exit code and collects any existing diagnostic report."""
        with tempfile.TemporaryDirectory() as directory:
            root, output = Path(directory) / 'repo', Path(directory) / 'evidence'
            root.mkdir()
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            script = ('#!' + sys.executable + '\nimport pathlib,sys\n'
                'pathlib.Path("build/reports/pitest").mkdir(parents=True, exist_ok=True)\n'
                'pathlib.Path("build/reports/pitest/mutations.xml").write_text("<mutations/>")\n'
                'print("PIT execution failed")\n'
                'sys.exit(1)\n')
            (root / 'gradlew').write_text(script)
            (root / 'gradlew').chmod(0o755)
            subprocess.run(['git', '-C', str(root), 'add', 'gradlew'], check=True)
            subprocess.run(['git', '-C', str(root), '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                            'commit', '-qm', 'fixture'], check=True)
            revision = mutation.git(root, 'rev-parse', 'HEAD')
            result = subprocess.run([sys.executable, '-B', str(Path(mutation.__file__)), 'ci-run',
                '--checkout', str(root), '--revision', revision, '--label', 'candidate', '--scope', 'default',
                '--output', str(output)], env=dict(os.environ, GITHUB_ACTIONS='true'), capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('default Gradle execution failed', result.stderr)
            record = mutation.load_json(output / 'record.json')
            self.assertEqual(1, record['exit'])
            self.assertFalse(record['complete'])
            self.assertTrue((output / 'mutations.xml').exists())
            self.assertEqual(mutation.fingerprint(output / 'mutations.xml'), record['reportHash'])

    def test_gradle_failure_before_metadata_raises_cleanly(self):
        """A Gradle failure before writing metadata fails closed without crashing on missing JSON."""
        with tempfile.TemporaryDirectory() as directory:
            root, output = Path(directory) / 'repo', Path(directory) / 'evidence'
            root.mkdir()
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            script = ('#!' + sys.executable + '\nimport sys\n'
                'print("Compilation error")\n'
                'sys.exit(2)\n')
            (root / 'gradlew').write_text(script)
            (root / 'gradlew').chmod(0o755)
            subprocess.run(['git', '-C', str(root), 'add', 'gradlew'], check=True)
            subprocess.run(['git', '-C', str(root), '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                            'commit', '-qm', 'fixture'], check=True)
            revision = mutation.git(root, 'rev-parse', 'HEAD')
            result = subprocess.run([sys.executable, '-B', str(Path(mutation.__file__)), 'ci-run',
                '--checkout', str(root), '--revision', revision, '--label', 'candidate', '--scope', 'build',
                '--output', str(output)], env=dict(os.environ, GITHUB_ACTIONS='true'), capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('build Gradle execution failed', result.stderr)
            record = mutation.load_json(output / 'record.json')
            self.assertEqual(2, record['exit'])
            self.assertFalse(record['complete'])
            self.assertFalse((output / 'resolved.json').exists())


class AggregateTest(unittest.TestCase):
    """Exercise complete paired evidence, including intentional corruption, through the validator."""

    def setUp(self):
        """Create bounded synthetic I/O evidence, not mocked checker or PIT execution."""
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.reports = self.root / 'reports'
        self.a = 'dev.samhb.interleave.bytecode.A'
        self.b = 'dev.samhb.interleave.bytecode.B'
        self.default = 'dev.samhb.interleave.state.A'
        self.records = {}
        for label in ('baseline', 'candidate'):
            directory = self.reports / (label + '-build')
            directory.mkdir(parents=True)
            for name in (self.a, self.b, self.default):
                file = directory / 'classes' / (name.replace('.', '/') + '.class')
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(name.encode())
            record = {'version': 1, 'complete': True, 'revision': ('a' if label == 'baseline' else 'b') * 40,
                      'controller': 'c' * 40, 'label': label, 'scope': 'build', 'exit': 0,
                      'checks': {'build': 'EXECUTED', 'javadoc': 'UP-TO-DATE', 'test': 'EXECUTED'},
                      'protocol': dict(resolved_fixture()['protocol'], harness={'Agent': 'same'}),
                      'targets': sorted(mutation.DEFAULT_SCOPE),
                      'inputs': {'production': mutation.class_inventory(directory / 'classes'),
                                 'tests': {'Test': 'same'}, 'harness': {'Agent': 'same'}, 'mainResources': {},
                                 'testResources': {}, 'dependencies': [], 'classpathDirectories': {},
                                 'classpathOrder': [], 'runtimeArtifacts': {}}}
            self.records[label] = record
            mutation.save_json(directory / 'record.json', record)
            (directory / 'run.log').write_text('BUILD SUCCESSFUL\n')
            mutation.save_json(directory / 'resolved.json', resolved_fixture())
        self.plan = mutation.make_plan('a' * 40, 'b' * 40, self.reports / 'baseline-build/classes',
                                      self.reports / 'candidate-build/classes', self.records['baseline'],
                                      self.records['candidate'])
        for label in self.records:
            self.write_run(label, 'default', 'all', [self.default])
            self.write_run(label, 'affected', 'all', [self.a, self.b])

    def write_run(self, label, scope, shard, classes, trial='acceptance'):
        """Emit an explicit completed summary/XML pair with one literal kill per class."""
        directory = self.reports / f'{label}-{scope}-{shard}-{trial}'
        directory.mkdir(parents=True, exist_ok=True)
        record = {**self.records[label], 'scope': scope, 'shard': shard, 'trial': trial,
                  'targets': sorted(mutation.DEFAULT_SCOPE) if scope == 'default' else classes,
                  'empty': not classes}
        if classes:
            rows = ''.join('<mutation status="KILLED"><mutatedClass>' + name + '</mutatedClass>'
                           '<mutatedMethod>run</mutatedMethod><methodDescription>()V</methodDescription>'
                           '<mutator>Math</mutator><indexes><index>1</index></indexes>'
                           '<blocks><block>0</block></blocks></mutation>' for name in classes)
            (directory / 'mutations.xml').write_text('<mutations>' + rows + '</mutations>')
            record['reportHash'] = mutation.fingerprint(directory / 'mutations.xml')
            (directory / 'run.log').write_text(f'>> Generated {len(classes)} mutations Killed {len(classes)} (100%)\n'
                '>> Mutations with no coverage 0.\nMutation ratchet — assertion-backed coverage\n'
                f'  RESULT: PASS — {len(classes)}/{len(classes)} = 100% >= floor 94%\nBUILD SUCCESSFUL\n')
        mutation.save_json(directory / 'record.json', record)
        return directory

    def test_aggregate_sums_raw_counts_and_requires_both_baseline_checks(self):
        """A complete pair has two affected kills per revision and separate default evidence."""
        result = mutation.validate_plan(self.plan, self.reports)
        self.assertEqual({'KILLED': 2}, result['candidate']['counts'])
        self.assertEqual(1, len(result['baseline']['defaultRows']))
        path = self.reports / 'baseline-build/record.json'
        record = mutation.load_json(path)
        record['checks'] = ['build']
        mutation.save_json(path, record)
        with self.assertRaisesRegex(ValueError, 'baseline|checks'):
            mutation.validate_plan(self.plan, self.reports)

    def test_plan_validate_cli_roundtrip_uses_all_artifacts(self):
        """The real commands write consistent aggregate files and copy verified class snapshots."""
        plan = self.root / 'plan.json'
        command = [sys.executable, '-B', str(Path(mutation.__file__))]
        subprocess.run(command + ['plan', '--base', 'a' * 40, '--candidate', 'b' * 40,
            '--base-classes', str(self.reports / 'baseline-build/classes'), '--candidate-classes',
            str(self.reports / 'candidate-build/classes'), '--base-record', str(self.reports / 'baseline-build/record.json'),
            '--candidate-record', str(self.reports / 'candidate-build/record.json'), '--output', str(plan)],
            check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        subprocess.run(command + ['validate', '--manifest', str(plan), '--reports', str(self.reports),
            '--output', str(self.root / 'validated')], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        result = mutation.load_json(self.root / 'validated/candidate/aggregate.json')
        self.assertEqual({'KILLED': 2}, result['counts'])
        self.assertEqual(self.records['candidate']['inputs']['production'],
                         mutation.class_inventory(self.root / 'validated/candidate/classes'))

    def test_sharded_population_requires_even_explicit_empty_jobs(self):
        """Empty assignments are accounted for rather than inferred from missing artifacts."""
        self.plan['mode'] = 'sharded'
        for label in self.records:
            shutil.rmtree(self.reports / f'{label}-affected-all-acceptance')
            for shard, names in enumerate(self.plan['revisions'][label]['shards']):
                self.write_run(label, 'affected', str(shard), names)
        self.assertEqual({'KILLED': 2}, mutation.validate_plan(self.plan, self.reports)['baseline']['counts'])
        empty = next(index for index, names in enumerate(self.plan['revisions']['baseline']['shards']) if not names)
        (self.reports / f'baseline-affected-{empty}-acceptance/record.json').unlink()
        with self.assertRaisesRegex(ValueError, 'Missing'):
            mutation.validate_plan(self.plan, self.reports)

    def test_overlapping_or_omitted_manifest_assignments_fail(self):
        """A manifest is checked against the inventory, not trusted because it is well-formed JSON."""
        bad = copy.deepcopy(self.plan)
        bad['revisions']['baseline']['shards'][0].append(self.a)
        with self.assertRaisesRegex(ValueError, 'assignments'):
            mutation.validate_plan(bad, self.reports)

    def test_missing_duplicate_stale_and_wrong_protocol_records_fail(self):
        """An existing report with the wrong execution identity cannot fill a required job."""
        path = self.reports / 'candidate-affected-all-acceptance/record.json'
        original = mutation.load_json(path)
        for change in ({'revision': 'd' * 40}, {'protocol': {}}, {'inputs': {}}, {'exit': 1}, {'version': 2}, {'version': True}):
            mutation.save_json(path, dict(original, **change))
            with self.subTest(change=change), self.assertRaises(ValueError):
                mutation.validate_plan(self.plan, self.reports)
        mutation.save_json(path, original)
        mutation.save_json(self.reports / 'duplicate/record.json', original)
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            mutation.validate_plan(self.plan, self.reports)
        (self.reports / 'duplicate/record.json').unlink()
        path.unlink()
        with self.assertRaisesRegex(ValueError, 'Missing'):
            mutation.validate_plan(self.plan, self.reports)

    def test_mixed_tests_reject_stale_other_class_kill(self):
        """A test edit invalidates the entire candidate aggregate, including the other class."""
        path = self.reports / 'candidate-affected-all-acceptance/record.json'
        record = mutation.load_json(path)
        record['inputs'] = dict(record['inputs'], tests={'Test': 'edited to stop checking B'})
        mutation.save_json(path, record)
        with self.assertRaisesRegex(ValueError, 'inputs'):
            mutation.validate_plan(self.plan, self.reports)

    def test_candidate_reuse_requires_full_revision_controller_inputs_and_checks(self):
        """A cached candidate build is reusable only at its exact immutable execution identity."""
        directory = self.reports / 'candidate-build'
        original = mutation.load_json(directory / 'record.json')
        self.assertEqual(original, mutation.candidate_reuse(directory, 'b' * 40, 'c' * 40, 'build'))
        for change in ({'revision': 'd' * 40}, {'controller': 'd' * 40}, {'checks': ['test']}, {'exit': 1},
                       {'complete': False}, {'checks': {'build': 'EXECUTED', 'test': 'EXECUTED', 'javadoc': 'SKIPPED'}}):
            mutation.save_json(directory / 'record.json', dict(original, **change))
            with self.subTest(change=change), self.assertRaises(ValueError):
                mutation.candidate_reuse(directory, 'b' * 40, 'c' * 40, 'build')

    def test_candidate_reuse_rejects_baseline_record(self):
        """Baseline evidence cannot be reused as candidate evidence."""
        directory = self.reports / 'baseline-build'
        with self.assertRaisesRegex(ValueError, 'Candidate evidence identity/completion differs'):
            mutation.candidate_reuse(directory, 'a' * 40, 'c' * 40, 'build')

    def test_candidate_reuse_rejects_mismatched_runtime_protocol(self):
        """Candidate reuse rejects cached records when runtime protocol differs."""
        directory = self.reports / 'candidate-build'
        controller = mutation.git(Path(mutation.__file__).parent, 'rev-parse', 'HEAD')
        original = mutation.load_json(directory / 'record.json')
        mutation.save_json(directory / 'record.json', dict(original, controller=controller))
        self.assertEqual(controller, mutation.candidate_reuse(directory, 'b' * 40, controller, 'build')['controller'])
        resolved_path = directory / 'resolved.json'
        resolved = mutation.load_json(resolved_path)
        changed = copy.deepcopy(resolved)
        changed['protocol']['runtime']['java.runtime.version'] = 'different-runtime'
        mutation.save_json(resolved_path, changed)
        try:
            with self.assertRaisesRegex(ValueError, 'Resolved protocol differs'):
                mutation.candidate_reuse(directory, 'b' * 40, controller, 'build')
        finally:
            mutation.save_json(resolved_path, resolved)
            mutation.save_json(directory / 'record.json', original)

    def test_sharded_acceptance_rejected_pending_reviewed_calibration(self):
        """Creating an acceptance plan with mode='sharded' is rejected until reviewed calibration."""
        with self.assertRaisesRegex(ValueError, 'Sharded acceptance remains disabled pending reviewed calibration'):
            mutation.make_plan('a' * 40, 'b' * 40, self.reports / 'baseline-build/classes',
                               self.reports / 'candidate-build/classes', self.records['baseline'],
                               self.records['candidate'], mode='sharded')

    def test_calibration_requires_two_complete_equal_trials(self):
        """Missing trials cannot enable calibration merely because acceptance passed."""
        self.plan['calibrate'] = True
        with self.assertRaisesRegex(ValueError, 'calibration'):
            mutation.validate_plan(self.plan, self.reports)
        for trial in ('calibration-1', 'calibration-2'):
            for shard, names in enumerate(self.plan['revisions']['candidate']['shards']):
                self.write_run('candidate', 'affected', str(shard), names, trial)
        self.assertEqual({'KILLED': 2}, mutation.validate_plan(self.plan, self.reports)['candidate']['counts'])
        for path in self.reports.glob('*calibration-2/mutations.xml'):
            path.write_text(path.read_text().replace('KILLED', 'SURVIVED'))
            record = mutation.load_json(path.parent / 'record.json')
            record['reportHash'] = mutation.fingerprint(path)
            mutation.save_json(path.parent / 'record.json', record)
            (path.parent / 'run.log').write_text('>> Generated 1 mutations Killed 0 (0%)\n'
                                               '>> Mutations with no coverage 0.\nBUILD SUCCESSFUL\n')
            break
        with self.assertRaisesRegex(ValueError, 'Sharded calibration differs from full reference'):
            mutation.validate_plan(self.plan, self.reports)


class LocalExecutionTest(unittest.TestCase):
    """Drive real fixture processes across worktrees instead of launching expensive PIT."""

    def test_cancellation_before_first_ownership_refresh_still_cleans_the_launcher(self):
        """Pause the external ps boundary during launch; TERM must be deferred until ownership is safe."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            (root / 'gradlew').write_text('#!' + sys.executable + '\nimport os,pathlib,time\n'
                'pathlib.Path("launcher.pid").write_text(str(os.getpid()))\ntime.sleep(30)\n')
            (root / 'gradlew').chmod(0o755)
            real_ps = shutil.which('ps')
            binary = root / 'bin'
            binary.mkdir()
            # The first ps is preflight; the second is the first post-Popen refresh.
            (binary / 'ps').write_text('#!' + sys.executable + '\nimport os,pathlib,time\n'
                'counter=pathlib.Path("ps.count")\nn=int(counter.read_text())+1 if counter.exists() else 1\n'
                'counter.write_text(str(n))\n'
                'if n==2:\n deadline=time.monotonic()+5\n'
                ' while not pathlib.Path("launcher.pid").exists() and time.monotonic()<deadline: time.sleep(.01)\n'
                ' pathlib.Path("refresh.paused").touch()\n'
                ' while not pathlib.Path("refresh.release").exists() and time.monotonic()<deadline: time.sleep(.01)\n'
                'os.execv(' + repr(real_ps) + ', [' + repr(real_ps) + ',"-axo","pid=,ppid=,pgid=,lstart=,stat="])\n')
            (binary / 'ps').chmod(0o755)
            command = [sys.executable, '-B', str(Path(mutation.__file__)), 'local', '--repo', str(root), '--classes', 'A']
            process = subprocess.Popen(command, cwd=root, env=dict(os.environ, PATH=str(binary) + os.pathsep + os.environ['PATH']),
                                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            try:
                deadline = time.monotonic() + 5
                while not (root / 'refresh.paused').exists() and time.monotonic() < deadline:
                    time.sleep(.01)
                self.assertTrue((root / 'refresh.paused').exists())
                process.send_signal(signal.SIGTERM)
                (root / 'refresh.release').touch()
                self.assertEqual(130, process.wait(timeout=8))
                pid = int((root / 'launcher.pid').read_text())
                live = mutation.process_snapshot().get(pid)
                self.assertTrue(live is None or live['zombie'])
                self.assertEqual('', (root / '.git/interleave-pit.lock').read_text())
            finally:
                (root / 'refresh.release').touch()
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=5)
                if (root / 'launcher.pid').exists():
                    try:
                        os.kill(int((root / 'launcher.pid').read_text()), signal.SIGKILL)
                    except ProcessLookupError:
                        pass

    def test_normal_local_launch_completes_without_cancellation(self):
        """Installing cancellation handlers before launch must preserve ordinary successful execution."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            (root / 'gradlew').write_text('#!' + sys.executable + '\nimport time\ntime.sleep(.2)\n')
            (root / 'gradlew').chmod(0o755)
            result = subprocess.run([sys.executable, '-B', str(Path(mutation.__file__)), 'local', '--repo', str(root),
                                     '--classes', 'A'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=8)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual('', (root / '.git/interleave-pit.lock').read_text())

    def test_local_lock_rejects_overlap_and_handles_interrupt(self):
        """The argument vector is bounded, the other worktree is locked, and cancellation reaps."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'repo'
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            subprocess.run(['git', '-C', str(root), '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                            'commit', '--allow-empty', '-qm', 'fixture'], check=True)
            other = Path(directory) / 'other'
            subprocess.run(['git', '-C', str(root), 'worktree', 'add', '--detach', '-q', str(other)], check=True)
            runner = ('#!' + sys.executable + '\nimport pathlib,sys,time,json\n'
                      'pathlib.Path("args.tmp").write_text(json.dumps(sys.argv[1:]))\n'
                      'pathlib.Path("args.tmp").replace("args.json")\ntime.sleep(30)\n')
            for repo in (root, other):
                (repo / 'gradlew').write_text(runner)
                (repo / 'gradlew').chmod(0o755)
            command = [sys.executable, '-B', str(Path(mutation.__file__)), 'local', '--classes', 'example.A', '--repo']
            process = subprocess.Popen(command + [str(root)], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            try:
                deadline = time.monotonic() + 5
                while not (root / 'args.json').exists() and time.monotonic() < deadline:
                    time.sleep(0.02)
                self.assertTrue((root / 'args.json').exists())
                self.assertEqual(['--max-workers=1', '-PpitestThreads=1', 'pitest',
                                  '-PpitestTargetOverride=example.A', '--no-daemon'],
                                 json.loads((root / 'args.json').read_text()))
                second = subprocess.run(command + [str(other)], capture_output=True, text=True, timeout=5)
                self.assertNotEqual(0, second.returncode)
                self.assertIn('Another local', second.stderr)
                process.send_signal(signal.SIGTERM)
                process.communicate(timeout=5)
                self.assertEqual(130, process.returncode)
                self.assertEqual('', (root / '.git/interleave-pit.lock').read_text())
            finally:
                if process.poll() is None:
                    process.send_signal(signal.SIGTERM)
                    process.communicate(timeout=5)

    def test_empty_or_shell_selector_fails_before_process_launch(self):
        """Selectors are data and cannot add commands or accidentally request a default scope."""
        for value in ('', 'example.A,', 'example.A; touch leaked', '--help'):
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, 'selectors'):
                mutation.local_run(Path('/nonexistent-fixture-root'), value)

    def test_launcher_exit_cannot_leave_a_detached_daemon_or_release_its_lock(self):
        """A real fixture daemon leaves the launch group, ignores TERM, and outlives its launcher."""
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            daemon = ('import signal,time; signal.signal(signal.SIGTERM, signal.SIG_IGN); time.sleep(30)')
            (root / 'gradlew').write_text('#!' + sys.executable + '\nimport subprocess,sys,time,pathlib\n'
                'p=subprocess.Popen([sys.executable,"-c",' + repr(daemon) + '],start_new_session=True)\n'
                'pathlib.Path("daemon.pid").write_text(str(p.pid))\ntime.sleep(0.6)\n')
            (root / 'gradlew').chmod(0o755)
            command = [sys.executable, '-B', str(Path(mutation.__file__)), 'local', '--repo', str(root), '--classes', 'A']
            process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            try:
                process.communicate(timeout=15)
                self.assertNotEqual(0, process.returncode)
                pid = int((root / 'daemon.pid').read_text())
                live = mutation.process_snapshot().get(pid)
                self.assertTrue(live is None or live['zombie'])
                self.assertEqual('', (root / '.git/interleave-pit.lock').read_text())
            finally:
                if process.poll() is None:
                    process.kill()
                    process.communicate(timeout=5)
                if (root / 'daemon.pid').exists():
                    try:
                        os.kill(int((root / 'daemon.pid').read_text()), signal.SIGKILL)
                    except ProcessLookupError:
                        pass


if __name__ == "__main__":
    unittest.main()
