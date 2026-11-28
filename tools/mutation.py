"""Fresh PIT evidence, conservative method comparison and CI/local execution (Spec 102).

Only accepted execution manifests establish a complete census. XML-only helpers also serve the
historical 94C diagnostic, whose mixed-input reports are never promoted into CI acceptance.
"""
from pathlib import Path
from collections import Counter
import argparse
import fcntl
import fnmatch
import hashlib
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import time
import zipfile
import xml.etree.ElementTree as ET

from mutation_evidence import STATUSES, require, read_report, normalized_code, compiled_methods, compare_methods
SCOPE = ('dev.samhb.interleave.bytecode.*', 'dev.samhb.interleave.BytecodeTraceJson*',
         'dev.samhb.interleave.ResultJson*', 'dev.samhb.interleave.core.BytecodeEvidence*',
         'dev.samhb.interleave.InterleaveRunner*', 'dev.samhb.interleave.TestResult*',
         'dev.samhb.interleave.TraceRecord*', 'dev.samhb.interleave.search.TraceReplayer*',
         'dev.samhb.interleave.report.BenchmarkHarness*', 'dev.samhb.interleave.report.BenchmarkResult*',
         'dev.samhb.interleave.report.ReportWriter*', 'dev.samhb.interleave.report.SoundnessAttestation*',
         'dev.samhb.interleave.cli.Main*')
DEFAULT_SCOPE = ('dev.samhb.interleave.state.*', 'dev.samhb.interleave.cb.*')
FAMILY_WEIGHTS = {
    'dev.samhb.interleave.BytecodeTraceJson': 131,
    'dev.samhb.interleave.InterleaveRunner': 274,
    'dev.samhb.interleave.ResultJson': 27,
    'dev.samhb.interleave.TestResult': 172,
    'dev.samhb.interleave.TraceRecord': 108,
    'dev.samhb.interleave.bytecode.Actor': 12,
    'dev.samhb.interleave.bytecode.BytecodeAccessVerifier': 281,
    'dev.samhb.interleave.bytecode.BytecodeArrays': 117,
    'dev.samhb.interleave.bytecode.BytecodeChecks': 42,
    'dev.samhb.interleave.bytecode.BytecodeExplorer': 165,
    'dev.samhb.interleave.bytecode.BytecodeFrame': 19,
    'dev.samhb.interleave.bytecode.BytecodeHierarchy': 330,
    'dev.samhb.interleave.bytecode.BytecodeInput': 134,
    'dev.samhb.interleave.bytecode.BytecodeLimits': 7,
    'dev.samhb.interleave.bytecode.BytecodeLoadException': 4,
    'dev.samhb.interleave.bytecode.BytecodeLoader': 1024,
    'dev.samhb.interleave.bytecode.BytecodeLocation': 11,
    'dev.samhb.interleave.bytecode.BytecodeMethod': 114,
    'dev.samhb.interleave.bytecode.BytecodeObservations': 79,
    'dev.samhb.interleave.bytecode.BytecodePlatform': 81,
    'dev.samhb.interleave.bytecode.BytecodePrimitives': 165,
    'dev.samhb.interleave.bytecode.BytecodeProgram': 31,
    'dev.samhb.interleave.bytecode.BytecodeReplay': 126,
    'dev.samhb.interleave.bytecode.BytecodeRuntime': 185,
    'dev.samhb.interleave.bytecode.BytecodeScheduler': 98,
    'dev.samhb.interleave.bytecode.BytecodeSharing': 106,
    'dev.samhb.interleave.bytecode.BytecodeSnapshots': 178,
    'dev.samhb.interleave.bytecode.BytecodeStack': 45,
    'dev.samhb.interleave.bytecode.BytecodeState': 229,
    'dev.samhb.interleave.bytecode.BytecodeStrings': 69,
    'dev.samhb.interleave.bytecode.BytecodeThrowables': 302,
    'dev.samhb.interleave.bytecode.BytecodeVerifier': 322,
    'dev.samhb.interleave.bytecode.Check': 18,
    'dev.samhb.interleave.bytecode.Model': 6,
    'dev.samhb.interleave.bytecode.OpcodeExecutor': 485,
    'dev.samhb.interleave.cli.Main': 453,
    'dev.samhb.interleave.core.BytecodeEvidence': 269,
    'dev.samhb.interleave.report.BenchmarkHarness': 315,
    'dev.samhb.interleave.report.BenchmarkResult': 77,
    'dev.samhb.interleave.report.ReportWriter': 259,
    'dev.samhb.interleave.report.SoundnessAttestation': 233,
    'dev.samhb.interleave.search.TraceReplayer': 237,
}


def load_json(path: Path | str) -> dict:
    """Read JSON without silently accepting duplicate object keys."""
    def object_pairs(pairs):
        """Reject a duplicate instead of choosing whichever value came last."""
        result = {}
        for key, value in pairs:
            require(key not in result, 'Duplicate JSON key')
            result[key] = value
        return result
    def nonfinite(_value):
        """JSON protocol numbers must be finite, unlike Python's permissive default decoder."""
        raise ValueError('Nonfinite JSON number')
    value = json.loads(Path(path).read_text(), object_pairs_hook=object_pairs, parse_constant=nonfinite)
    require(isinstance(value, dict), 'Expected a JSON object')
    return value


def save_json(path: Path | str, value: object) -> None:
    """Write deterministic derived evidence, creating its parent directory."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n')


def fingerprint(path: Path | str) -> str:
    """Hash bytes rather than timestamps or a directory's absolute location."""
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def tree_hashes(directory: Path | str) -> dict:
    """Snapshot regular input files; fail on symlinks rather than hash outside the input tree."""
    directory = Path(directory)
    result = {}
    for path in sorted(directory.rglob('*')):
        require(not path.is_symlink(), f'Symlink input unsupported: {path}')
        if path.is_file():
            result[path.relative_to(directory).as_posix()] = fingerprint(path)
    return result


def class_inventory(directory: Path | str) -> dict:
    """Return binary class names and exact bytes from a nonempty compiled production inventory."""
    values = {name[:-6].replace('/', '.'): digest for name, digest in tree_hashes(directory).items()
              if name.endswith('.class')}
    require(values, 'Missing compiled production inventory')
    return values


def partition(names: dict | list) -> list[list[str]]:
    """Select the frozen population and assign every selected class family deterministically via LPT bin-packing."""
    families = {}
    for name in sorted(names):
        if any(fnmatch.fnmatchcase(name, pattern) for pattern in SCOPE):
            family = name.split('$', 1)[0]
            families.setdefault(family, []).append(name)
    shards = [[] for _ in range(4)]
    weights = [0] * 4
    for family in sorted(families.keys(), key=lambda f: (-FAMILY_WEIGHTS.get(f, len(families[f])), f)):
        idx = weights.index(min(weights))
        shards[idx].extend(families[family])
        weights[idx] += FAMILY_WEIGHTS.get(family, len(families[family]))
    return [sorted(shard) for shard in shards]


def check_provenance(expected: dict, observed: dict) -> None:
    """Bind every report of a revision to its full test/production/dependency inputs and protocol."""
    for name in ('revision', 'protocol', 'inputs') + (('controller',) if 'controller' in expected else ()):
        require(name in expected and observed.get(name) == expected[name], f'Mismatched {name}')
    required = {'production', 'tests', 'testResources', 'mainResources', 'harness', 'dependencies',
                'classpathDirectories', 'classpathOrder', 'runtimeArtifacts'}
    require(set(observed['inputs']) == required, 'Missing/unexpected input groups')
    require(observed['inputs']['production'] and observed['inputs']['tests'] and observed['inputs']['harness'],
            'Missing compiled production/tests/harness inputs')


def make_plan(base: str, candidate: str, base_classes: Path | str, candidate_classes: Path | str,
              base_record: dict, candidate_record: dict, mode: str = 'full', calibrate: bool = False) -> dict:
    """Plan an exact pair from successful preflights; no source files are overlaid on either revision."""
    require(mode == 'full', 'Sharded acceptance remains disabled pending reviewed calibration')
    result = {'version': 1, 'mode': mode, 'calibrate': calibrate, 'scope': list(SCOPE), 'revisions': {}}
    for label, revision, directory, record in (
            ('baseline', base, base_classes, base_record),
            ('candidate', candidate, candidate_classes, candidate_record)):
        require(re.fullmatch(r'[0-9a-f]{40}', revision or ''), 'Require a full immutable SHA')
        require(type(record.get('version')) is int and record.get('version') == 1 and record.get('revision') == revision
                and record.get('scope') == 'build' and record.get('exit') == 0 and record.get('complete') is True,
                'Incomplete/wrong preflight')
        check_build(record)
        inventory = class_inventory(directory)
        require(inventory == record['inputs']['production'], 'Preflight class hashes differ')
        groups = partition(inventory)
        require(any(groups), 'Empty affected population')
        result['revisions'][label] = {key: record[key] for key in ('revision', 'protocol', 'inputs', 'controller')}
        result['revisions'][label]['shards'] = groups
    require(base_record['protocol'] == candidate_record['protocol'], 'Baseline/candidate protocols differ')
    require(base_record['controller'] == candidate_record['controller'], 'Controllers differ')
    return result


def expected_jobs(plan: dict) -> list[dict]:
    """List all acceptance jobs, including explicit planned empty assignments."""
    require(type(plan.get('version')) is int and plan.get('version') == 1 and plan.get('scope') == list(SCOPE), 'Unsupported manifest')
    require(type(plan.get('calibrate')) is bool, 'Missing calibration intent')
    require(plan.get('mode') in ('full', 'sharded') and set(plan.get('revisions', {})) == {'baseline', 'candidate'},
            'Incomplete analysis plan')
    jobs = []
    for label, entry in plan['revisions'].items():
        groups = entry['shards']
        require(len(groups) == 4 and groups == partition(entry['inputs']['production']), 'Invalid shard assignments')
        for shard in (['all'] if plan['mode'] == 'full' else ['0', '1', '2', '3']):
            classes = sorted(x for group in groups for x in group) if shard == 'all' else groups[int(shard)]
            jobs.append({'label': label, 'shard': shard, 'classes': classes, 'trial': 'acceptance'})
    return jobs


def completed_rows(directory: Path | str, record: dict) -> dict:
    """Require successful execution and matching complete PIT totals before trusting report rows."""
    directory = Path(directory)
    require(type(record.get('version')) is int and record.get('version') == 1 and record.get('exit') == 0
            and record.get('complete') is True, 'Incomplete/unsupported execution')
    require(record.get('scope') in ('affected', 'default'), 'Not a mutation execution')
    if record.get('empty'):
        require(record['scope'] == 'affected' and record.get('targets') == [], 'Invalid empty shard')
        require(not (directory / 'mutations.xml').exists(), 'Empty shard contains a report')
        return {}
    require(record.get('targets'), 'Missing executed targets')
    report = directory / 'mutations.xml'
    require(record.get('reportHash') == fingerprint(report), 'Stale report hash')
    rows = read_report(report, STATUSES | {'EQUIVALENT'} if record['scope'] == 'default' else STATUSES)
    log = (directory / 'run.log').read_text(errors='replace')
    summary = re.findall(r'^>> Generated (\d+) mutations Killed (\d+)\b', log, re.M)
    uncovered = re.findall(r'^>> Mutations with no coverage (\d+)\b', log, re.M)
    require('BUILD SUCCESSFUL' in log and len(summary) == 1 and len(uncovered) == 1,
            'Missing completed PIT summary')
    counts = Counter(rows.values())
    # PIT's detected headline includes EQUIVALENT; only the existing default ratchet may allow it.
    detected = counts['KILLED'] + counts['EQUIVALENT']
    require(tuple(map(int, summary[0])) == (len(rows), detected)
            and int(uncovered[0]) == counts['NO_COVERAGE'], 'PIT summary/report counts differ')
    require(all(any(fnmatch.fnmatchcase(key[0], pattern) for pattern in record['targets']) for key in rows),
            'Reported class outside executed assignment')
    if record['scope'] == 'default':
        require('Mutation ratchet — assertion-backed coverage' in log, 'Default ratchet did not complete')
        require(re.search(r'^  RESULT: PASS — \d+/\d+ = .* >= floor \d+%', log, re.M),
                'Default full-scope ratchet did not pass')
    return rows


def encode_rows(rows: dict) -> list:
    """Encode tuple identities losslessly as deterministic JSON-friendly pairs."""
    return [[list(key[:4]) + [list(key[4]), list(key[5])], rows[key]] for key in sorted(rows)]


def check_build(record: dict) -> None:
    """Require observed successful task outcomes, not a list of requested Gradle tasks."""
    checks = record.get('checks')
    require(isinstance(checks, dict) and set(checks) == {'build', 'javadoc', 'test'}
            and all(value in ('EXECUTED', 'UP-TO-DATE') for value in checks.values()),
            'Build checks incomplete: build, strict javadoc and test must execute or be up-to-date')


def decode_rows(entries: list) -> dict:
    """Read derived aggregate identities, rejecting duplicates or malformed coordinate arrays."""
    result = {}
    for raw, status in entries:
        require(len(raw) == 6 and all(isinstance(x, str) and x for x in raw[:4])
                and all(isinstance(x, list) and x and all(type(n) is int and n >= 0 for n in x) for x in raw[4:]),
                'Invalid aggregate identity')
        key = tuple(raw[:4]) + tuple(tuple(x) for x in raw[4:])
        require(key not in result and status in STATUSES | {'EQUIVALENT'}, 'Duplicate/invalid aggregate row')
        result[key] = status
    return result


def validate_plan(plan: dict, reports: Path | str) -> dict:
    """Validate all paired preflight/default/affected jobs; never average percentages or mix inputs."""
    jobs = expected_jobs(plan)
    records = {}
    for path in Path(reports).rglob('record.json'):
        record = load_json(path)
        key = (record.get('label'), record.get('scope'), record.get('shard', 'all'), record.get('trial', 'acceptance'))
        require(key not in records, 'Duplicate execution record')
        records[key] = (path.parent, record)
    aggregates = {}
    for label, expected in plan['revisions'].items():
        combined = {}
        for scope in ('build', 'default'):
            key = (label, scope, 'all', 'acceptance')
            require(key in records, f'Missing {label} {scope}')
            directory, record = records.pop(key)
            check_provenance(expected, record)
            require(type(record.get('version')) is int and record.get('version') == 1 and record.get('exit') == 0
                    and record.get('complete') is True, f'Failed {scope} preflight')
            if scope == 'build':
                check_build(record)
                require('BUILD SUCCESSFUL' in (directory / 'run.log').read_text(), 'Build did not complete')
            else:
                require(record.get('targets') == sorted(DEFAULT_SCOPE), 'Not the complete default population')
                default_rows = completed_rows(directory, record)
        for job in (value for value in jobs if value['label'] == label):
            key = (label, 'affected', job['shard'], 'acceptance')
            require(key in records, f'Missing {label} shard {job["shard"]}')
            directory, record = records.pop(key)
            check_provenance(expected, record)
            require(record.get('targets') == job['classes'], 'Resolved assignment differs from plan')
            require(bool(record.get('empty')) == (not job['classes']), 'Incorrect empty assignment')
            rows = completed_rows(directory, record)
            require(not (combined.keys() & rows.keys()), 'Duplicate mutant across shards')
            combined.update(rows)
        aggregates[label] = {'version': 1, **expected, 'rows': encode_rows(combined),
                             'defaultRows': encode_rows(default_rows), 'counts': dict(Counter(combined.values()))}
    # Calibration jobs have their own exact-equality check, never enter acceptance totals.
    require(all(key[3] in ('calibration-1', 'calibration-2') for key in records), 'Unexpected execution records')
    require(bool(records) == bool(plan.get('calibrate')), 'Missing/unexpected calibration population')
    if plan.get('calibrate'):
        expected = plan['revisions']['candidate']
        reference = decode_rows(aggregates['candidate']['rows'])
        require(plan['mode'] == 'full', 'Calibration requires an unsharded reference')
        for trial in ('calibration-1', 'calibration-2'):
            combined = {}
            for shard, classes in enumerate(expected['shards']):
                key = ('candidate', 'affected', str(shard), trial)
                require(key in records, 'Missing calibration shard')
                directory, record = records.pop(key)
                check_provenance(expected, record)
                require(record.get('targets') == classes and bool(record.get('empty')) == (not classes),
                        'Wrong calibration assignment')
                rows = completed_rows(directory, record)
                require(not (combined.keys() & rows.keys()), 'Duplicate calibration mutants')
                combined.update(rows)
            require(combined == reference, 'Sharded calibration differs from full reference')
        require(not records, 'Unexpected calibration records')
    return aggregates


def git(root: Path | str, *arguments: str) -> str:
    """Run a fixed Git argument vector against one checkout, never a shell expression."""
    return subprocess.check_output(['git', '-C', str(root), *arguments], text=True).strip()


def process_snapshot() -> dict:
    """Read POSIX process identities/parentage without exposing command-line arguments or secrets."""
    source = subprocess.check_output(['ps', '-axo', 'pid=,ppid=,pgid=,lstart=,stat='], text=True)
    result = {}
    for line in source.splitlines():
        parts = line.split()
        require(len(parts) == 9, 'Unsupported ps process identity format')
        pid, parent, group = map(int, parts[:3])
        result[pid] = {'parent': parent, 'group': group, 'started': ' '.join(parts[3:8]), 'zombie': 'Z' in parts[8]}
    return result


def owned_processes(owned: dict, launcher: int) -> dict:
    """Refresh observed descendants, including Gradle's detached single-use daemon, with PID reuse guards."""
    snapshot = process_snapshot()
    leader = snapshot.get(launcher)
    group_owned = leader is None or launcher not in owned or owned[launcher] == leader['started']
    live = {pid: row for pid, row in snapshot.items() if not row['zombie'] and
            (owned.get(pid) == row['started'] or (group_owned and row['group'] == launcher))}
    while True:
        children = {pid: row for pid, row in snapshot.items() if not row['zombie'] and row['parent'] in live}
        if children.keys() <= live.keys():
            break
        live.update(children)
    owned.update({pid: row['started'] for pid, row in live.items()})
    return live


def local_run(root: Path | str, selectors: str) -> int:
    """Serialize cooperating local PIT processes, forward cancellation and retain orphan diagnostics."""
    root = Path(root).resolve()
    classes = selectors.split(',')
    require(classes and all(re.fullmatch(r'[\w$]+(?:\.[\w$]+)*(?:\.\*|\*)?', name, re.ASCII) for name in classes),
            'Provide nonempty explicit binary class selectors')
    common = Path(git(root, 'rev-parse', '--path-format=absolute', '--git-common-dir'))
    with (common / 'interleave-pit.lock').open('a+') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise ValueError('Another local Interleave PIT run is active; wait for it to finish') from error
        lock.seek(0)
        previous = lock.read().strip()
        if previous:
            snapshot = process_snapshot()
            marker = json.loads(previous)
            require(isinstance(marker, dict), 'Invalid previous process marker; diagnose before retrying')
            require(not any((row := snapshot.get(int(pid))) and not row['zombie'] and row['started'] == started
                            for pid, started in marker.items()),
                    'Previous PIT process still exists; diagnose the orphan before retrying')
        command = [str(root / 'gradlew'), '--max-workers=1', '-PpitestThreads=1', 'pitest',
                   '-PpitestTargetOverride=' + ','.join(classes), '--no-daemon']
        process_snapshot()  # Fail before launch when the host forbids process-tree supervision.
        print('Scoped feedback only; complete remote acceptance is still required.', flush=True)
        child = None
        owned = {}

        def refresh():
            """Persist live owned identities before releasing the OS lock, retaining orphan diagnostics."""
            live = owned_processes(owned, child.pid)
            lock.seek(0)
            lock.truncate()
            if live:
                lock.write(json.dumps({pid: row['started'] for pid, row in live.items()}))
            lock.flush()
            return live

        cancelled = False

        def interrupt(_signal, _frame):
            """Defer cancellation until launch/ownership recording finishes; never unwind Popen."""
            nonlocal cancelled
            cancelled = True

        old_handler = signal.signal(signal.SIGTERM, interrupt)
        old_int = signal.signal(signal.SIGINT, interrupt)
        try:
            if cancelled:
                return 130
            child = subprocess.Popen(command, cwd=root, start_new_session=True)
            refresh()
            while not cancelled and child.poll() is None:
                refresh()
                time.sleep(0.1)
        finally:
            # Ignore a second interruption while cleaning only observed owned processes.
            signal.signal(signal.SIGTERM, signal.SIG_IGN)
            signal.signal(signal.SIGINT, signal.SIG_IGN)
            try:
                if child is not None:
                    live = refresh()
                    orphaned = bool(live) and not cancelled
                    for shutdown_signal in (signal.SIGTERM, signal.SIGKILL):
                        for pid in live:
                            try:
                                os.kill(pid, shutdown_signal)
                            except ProcessLookupError:
                                pass
                        deadline = time.monotonic() + 2
                        while live and time.monotonic() < deadline:
                            child.poll()  # Reap the launcher; orphan zombies are not running work.
                            time.sleep(0.05)
                            live = refresh()
                        if not live:
                            break
            finally:
                signal.signal(signal.SIGTERM, old_handler)
                signal.signal(signal.SIGINT, old_int)
        require(not live, 'Owned PIT descendants remain; process marker retained, diagnose before retrying')
        require(not orphaned, 'Launcher exited while descendants remained; cleaned them; scoped run failed')
        return 130 if cancelled else child.returncode


def jar_entries(path: Path | str) -> dict:
    """Hash logical runtime JAR entries without unstable ZIP timestamps/order; reject duplicates."""
    result = {}
    with zipfile.ZipFile(path) as archive:
        for entry in archive.infolist():
            require(entry.filename not in result, 'Duplicate runtime JAR entry')
            with archive.open(entry) as stream:
                digest = hashlib.sha256()
                for chunk in iter(lambda: stream.read(65536), b''):
                    digest.update(chunk)
            result[entry.filename] = digest.hexdigest()
    require(result, 'Empty runtime JAR')
    return result


def snapshot_inputs(root: Path | str, metadata: dict) -> dict:
    """Bind production, all compiled tests/resources, harness and dependencies to one execution."""
    root = Path(root)
    directories = {}
    for name in metadata['classpathDirectories']:
        path = (root / name).resolve()
        require(path.is_relative_to(root.resolve()), 'External classpath directory unsupported')
        directories[name] = tree_hashes(path)
    artifacts = {}
    for role, name in metadata['runtimeArtifacts'].items():
        path = (root / name).resolve()
        require(path.is_relative_to(root.resolve()), 'External runtime artifact unsupported')
        artifacts[role] = jar_entries(path)
    return {'production': class_inventory(root / 'build/classes/java/main'),
            'tests': tree_hashes(root / 'build/classes/java/test'),
            'testResources': tree_hashes(root / 'build/resources/test'),
            'mainResources': tree_hashes(root / 'build/resources/main'),
            'harness': tree_hashes(root / 'build/classes/java/mutationHarness'),
            'dependencies': metadata['dependencies'], 'classpathDirectories': directories,
            'classpathOrder': metadata['classpathOrder'], 'runtimeArtifacts': artifacts}


def check_metadata(metadata: dict) -> None:
    """Reject disabled history/coverage safeguards or narrowed test selection before accepting a run."""
    protocol = metadata['protocol']
    require({'gradle', 'java', 'vendor', 'javaMajor', 'pit', 'junit', 'settings', 'childArgs',
             'launchJars', 'buildPlugins', 'mutablePaths', 'runtime', 'coordinatorRuntime', 'features', 'coordinatorArgs'} <= protocol.keys(),
            'Missing resolved protocol fields')
    require(protocol['runtime']['java.specification.version'] == '26', 'Actual PIT runtime differs')
    require(protocol['coordinatorRuntime']['java.specification.version'] == '26', 'Actual PIT coordinator runtime differs')
    require(isinstance(protocol['features'], list) and all(isinstance(value, str) for value in protocol['features']),
            'Missing/invalid effective PIT features')
    require(protocol['javaMajor'] == 26 and protocol['pit'] == '1.30.0' and protocol['junit'] == '1.2.3',
            'Unsupported toolchain; review and calibrate the analysis protocol')
    settings = protocol['settings']
    require(settings['targetTests'] == ['dev.samhb.interleave.*'], 'Covering tests were narrowed')
    for name in ('excludedMethods', 'excludedClasses', 'excludedTestClasses', 'includedGroups',
                 'excludedGroups', 'includedTestMethods'):
        require(settings[name] in (None, []), f'Unexpected filtering: {name}')
    require(settings['skipFailingTests'] in (None, 'false') and settings['failWhenNoMutations'] == 'true',
            'Fail-closed PIT options changed')
    require(not metadata['history'], 'PIT history cannot establish fresh acceptance')
    require(protocol['mutablePaths'] == ['build/classes/java/main'], 'Mutable class population changed')
    require({'build/classes/java/main', 'build/classes/java/test'} <= set(metadata['classpathDirectories']),
            'Production/covering test class directories missing')
    require(set(settings['mutators']) == {'CONDITIONALS_BOUNDARY', 'NEGATE_CONDITIONALS', 'MATH',
            'INVERT_NEGS', 'INCREMENTS', 'VOID_METHOD_CALLS', 'NON_VOID_METHOD_CALLS', 'PRIMITIVE_RETURNS',
            'RETURNS', 'FALSE_RETURNS', 'TRUE_RETURNS', 'EMPTY_RETURNS'}, 'Mutation operators changed')


def ci_run(root: Path | str, revision: str, label: str, scope: str, output: Path | str,
           targets: list[str] | None = None, shard: str = 'all', trial: str = 'acceptance',
           expected: dict | None = None) -> dict:
    """Execute one fresh checked CI job; always leave failure/provenance artifacts for collection."""
    root, output = Path(root).resolve(), Path(output).resolve()
    require(os.environ.get('GITHUB_ACTIONS') == 'true', 'CI execution only; use local for Mac feedback')
    require(scope in ('build', 'default', 'affected'), 'Unknown execution scope')
    require(re.fullmatch(r'[0-9a-f]{40}', revision or ''), 'Require full immutable revision')
    require(git(root, 'rev-parse', 'HEAD') == revision, 'Checkout revision differs')
    require(not git(root, 'status', '--porcelain', '--untracked-files=no'), 'Tracked checkout inputs are dirty')
    require(not output.exists(), 'Fresh evidence directory required')
    output.mkdir(parents=True)
    started = time.monotonic()
    record = {'version': 1, 'revision': revision, 'label': label, 'scope': scope, 'shard': shard,
              'trial': trial, 'exit': None, 'complete': False,
              'controller': git(Path(__file__).parent, 'rev-parse', 'HEAD')}
    try:
        command = [str(root / 'gradlew'), '--console=plain', '--no-daemon', '--no-build-cache', '--no-configuration-cache',
                   '--init-script', str(Path(__file__).with_name('mutation-inputs.init.gradle')),
                   '-PmutationMetadata=' + str(output / 'resolved.json'),
                   '-PmutationTaskOutcomes=' + str(output / 'tasks.json')]
        if expected is not None:
            save_json(output / 'expected.json', dict(expected, targets=targets))
            command += ['-PmutationExpected=' + str(output / 'expected.json'),
                        '-PmutationController=' + str(Path(__file__).resolve()), '-PmutationPython=' + sys.executable]
        # These flags limit analysis jobs, not the independently parallel existing CI gates.
        if scope == 'affected':
            require(targets, 'Use the explicit empty-shard path for an empty assignment')
            command += ['-PpitestThreads=1', '-PpitestTargetOverride=' + ','.join(targets)]
        tasks = ['build', 'javadoc', 'exportMutationInputs'] if scope == 'build' else ['pitest']
        report_dir = root / 'build/reports/pitest'
        if scope != 'build' and report_dir.exists():
            shutil.rmtree(report_dir)
        with (output / 'run.log').open('w') as log:
            result = subprocess.run(command + tasks, cwd=root, stdout=log, stderr=subprocess.STDOUT)
        record['exit'] = result.returncode
        if scope != 'build':
            report = report_dir / 'mutations.xml'
            if report.exists():
                shutil.copy2(report, output / 'mutations.xml')
                record['reportHash'] = fingerprint(report)
        classes_dir = root / 'build/classes/java/main'
        if classes_dir.exists():
            shutil.copytree(classes_dir, output / 'classes')
        require(result.returncode == 0, f'{scope} Gradle execution failed; inspect {output / "run.log"}')
        metadata = load_json(output / 'resolved.json')
        check_metadata(metadata)
        record.update(protocol=metadata['protocol'], inputs=snapshot_inputs(root, metadata),
                      targets=metadata['targets'], threads=metadata['threads'], empty=False)
        record['protocol']['harness'] = record['inputs']['harness']
        if scope == 'build':
            record['checks'] = load_json(output / 'tasks.json')
            check_build(record)
        # Commit completion only after every required artifact/guard succeeds, independently of Gradle's exit.
        if scope != 'build':
            completed_rows(output, dict(record, complete=True))
        if expected is not None:
            check_provenance(expected, record)
            require(record['targets'] == targets and record['threads'] == 1, 'Worker assignment/threads differ')
        record['complete'] = True
        return record
    finally:
        record['elapsedSeconds'] = round(time.monotonic() - started, 3)
        # JVM/runner peak RSS remains unmeasured; a heap cap is not a resident-memory observation.
        record['peakRssBytes'] = None
        save_json(output / 'record.json', record)


def candidate_reuse(directory: Path | str, revision: str, controller: str, scope: str,
                    expected: dict | None = None) -> dict:
    """Validate one existing check without executing Gradle or trusting a filename."""
    directory = Path(directory)
    record = load_json(directory / 'record.json')
    require(type(record['version']) is int and record['version'] == 1 and record['revision'] == revision and record['controller'] == controller
            and record['scope'] == scope and record['label'] == 'candidate' and record['exit'] == 0
            and record.get('complete') is True,
            'Candidate evidence identity/completion differs')
    metadata = load_json(directory / 'resolved.json')
    check_metadata(metadata)
    protocol = dict(metadata['protocol'], harness=record['inputs']['harness'])
    require(protocol == record['protocol'] and record['targets'] == metadata['targets'], 'Resolved protocol differs')
    require(class_inventory(directory / 'classes') == record['inputs']['production'], 'Archived classes differ')
    if scope == 'build':
        check_build(record)
        require('BUILD SUCCESSFUL' in (directory / 'run.log').read_text(), 'Build checks incomplete')
    else:
        check_provenance(expected, record)
        require(record['targets'] == sorted(DEFAULT_SCOPE), 'Default scope changed')
        completed_rows(directory, record)
    return record


def ensure_candidate_checks(root: Path | str, revision: str, evidence: Path | str) -> None:
    """Reuse only successful exact candidate evidence; rerun absent/incompatible checks once."""
    evidence = Path(evidence).resolve()
    controller = git(Path(__file__).parent, 'rev-parse', 'HEAD')
    expected = None
    for scope in ('build', 'default'):
        directory = evidence / ('candidate-' + scope)
        try:
            record = candidate_reuse(directory, revision, controller, scope, expected)
        except (ValueError, OSError, KeyError, TypeError, ET.ParseError) as error:
            print(f'Rerunning candidate {scope}: {error}', flush=True)
            if directory.exists():
                rejected = evidence.parent / 'rejected-reuse' / directory.name
                require(not rejected.exists(), 'Previous rejected reuse diagnostics already exist')
                rejected.parent.mkdir(parents=True, exist_ok=True)
                directory.rename(rejected)
            record = ci_run(root, revision, 'candidate', scope, directory)
        if scope == 'build':
            expected = record


def select_revisions(root: Path | str, base: str, candidate: str, head: str | None = None,
                     manual: bool = False, dispatch_ref: str = 'refs/heads/main') -> dict:
    """Bind automatic evidence to the actual merge pair, or constrain manual refs/ancestry."""
    for value in (base, candidate, head or candidate):
        require(re.fullmatch(r'[0-9a-f]{40}', value or ''), 'Full immutable SHA required')
        git(root, 'cat-file', '-e', value + '^{commit}')
    if manual:
        require(dispatch_ref == 'refs/heads/main', 'Manual dispatch must use main workflow')
        for value in (base, candidate):
            require(git(root, 'for-each-ref', '--contains', value, '--format=%(refname)'), 'Unreachable manual revision')
        subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', base, 'origin/main'], check=True)
        subprocess.run(['git', '-C', str(root), 'merge-base', '--is-ancestor', base, candidate], check=True)
    else:
        parents = git(root, 'rev-list', '--parents', '-n', '1', candidate).split()
        require(parents == [candidate, base, head], 'Candidate is not the recorded PR merge revision')
    paths = subprocess.check_output(['git', '-C', str(root), 'diff', '--no-renames', '--name-only', '-z', base, candidate]).decode().split('\0')
    relevant = ('src/main/java/', 'src/test/java/', 'src/test/resources/', 'src/mutationHarness/',
                'src/main/resources/', 'tools/', 'gradle/', '.github/workflows/')
    names = {'build.gradle.kts', 'build.gradle', 'settings.gradle.kts', 'settings.gradle', 'gradle.properties',
             'gradlew', 'gradlew.bat'}
    required = manual or any(path.startswith(relevant) or path in names for path in paths)
    return {'base': base, 'candidate': candidate, 'head': head, 'required': required,
            'reason': 'relevant changed paths or explicit dispatch' if required else 'documentation-only; no mutation run claimed'}


def matrix_jobs(plan: dict) -> dict:
    """Describe acceptance and optional two calibration trials as one bounded CI matrix."""
    jobs = [{key: value for key, value in job.items() if key != 'classes'} for job in expected_jobs(plan)]
    if plan.get('calibrate'):
        jobs.extend({'label': 'candidate', 'shard': str(shard), 'trial': trial}
                    for trial in ('calibration-1', 'calibration-2') for shard in range(4))
    return {'include': jobs}


def emit_output(name: str, value: object) -> None:
    """Write compact, single-line controller output to GitHub's file interface when present."""
    text = json.dumps(value, separators=(',', ':')) if not isinstance(value, str) else value
    require('\n' not in text and '\r' not in text, 'Multiline job output forbidden')
    if path := os.environ.get('GITHUB_OUTPUT'):
        with open(path, 'a') as output:
            output.write(name + '=' + text + '\n')
    print(name + '=' + text)


def main() -> int:
    """Provide the supported local/controller interfaces; invalid evidence exits nonzero."""
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    local = commands.add_parser('local')
    local.add_argument('--classes', required=True)
    local.add_argument('--repo', type=Path, default=Path.cwd())
    select = commands.add_parser('select', help='CI revision/path decision')
    select.add_argument('--repo', type=Path, default=Path.cwd())
    select.add_argument('--base', required=True)
    select.add_argument('--candidate', required=True)
    select.add_argument('--head')
    select.add_argument('--head-repository')
    select.add_argument('--manual', action='store_true')
    select.add_argument('--output', type=Path, required=True)
    plan_parser = commands.add_parser('plan')
    for name in ('base', 'candidate', 'base-classes', 'candidate-classes', 'base-record', 'candidate-record', 'output'):
        plan_parser.add_argument('--' + name, required=True)
    plan_parser.add_argument('--mode', choices=('full', 'sharded'), default='full')
    plan_parser.add_argument('--calibrate', action='store_true')
    worker = commands.add_parser('ci-run', help='Execute one GitHub Actions analysis, not local PIT')
    worker.add_argument('--checkout', type=Path, required=True)
    worker.add_argument('--revision', required=True)
    worker.add_argument('--label', choices=('baseline', 'candidate'), required=True)
    worker.add_argument('--scope', choices=('build', 'default', 'affected'), required=True)
    worker.add_argument('--output', type=Path, required=True)
    worker.add_argument('--plan', type=Path)
    worker.add_argument('--shard', choices=('all', '0', '1', '2', '3'), default='all')
    worker.add_argument('--trial', choices=('acceptance', 'calibration-1', 'calibration-2'), default='acceptance')
    reuse = commands.add_parser('candidate-checks', help='Validate reuse or execute missing candidate preflights')
    reuse.add_argument('--checkout', type=Path, required=True)
    reuse.add_argument('--revision', required=True)
    reuse.add_argument('--evidence', type=Path, required=True)
    verify = commands.add_parser('verify-inputs', help='Fail before PIT if resolved worker inputs differ')
    verify.add_argument('--checkout', type=Path, required=True)
    verify.add_argument('--resolved', type=Path, required=True)
    verify.add_argument('--expected', type=Path, required=True)
    validate = commands.add_parser('validate')
    validate.add_argument('--manifest', type=Path, required=True)
    validate.add_argument('--reports', type=Path, required=True)
    validate.add_argument('--output', type=Path, required=True)
    compare = commands.add_parser('compare')
    compare.add_argument('--baseline', type=Path, required=True)
    compare.add_argument('--candidate', type=Path, required=True)
    compare.add_argument('--output', type=Path, required=True)
    compare.add_argument('--javap', default='javap')
    args = parser.parse_args()
    if args.command == 'local':
        return local_run(args.repo, args.classes)
    if args.command == 'select':
        selection = select_revisions(args.repo, args.base, args.candidate, args.head, args.manual,
                                     os.environ.get('GITHUB_REF', ''))
        require(args.head_repository and re.fullmatch(r'[\w.-]+/[\w.-]+', args.head_repository, re.ASCII),
                'Record the head repository explicitly')
        selection['headRepository'] = args.head_repository
        save_json(args.output, selection)
        emit_output('required', str(selection['required']).lower())
        return 0
    if args.command == 'candidate-checks':
        ensure_candidate_checks(args.checkout, args.revision, args.evidence)
        return 0
    if args.command == 'verify-inputs':
        metadata = load_json(args.resolved)
        check_metadata(metadata)
        inputs = snapshot_inputs(args.checkout, metadata)
        observed = {'revision': git(args.checkout, 'rev-parse', 'HEAD'), 'inputs': inputs,
                    'protocol': dict(metadata['protocol'], harness=inputs['harness']),
                    'controller': git(Path(__file__).parent, 'rev-parse', 'HEAD')}
        expected = load_json(args.expected)
        check_provenance(expected, observed)
        require(metadata['targets'] == expected['targets'] and metadata['threads'] == 1, 'Worker assignment/threads differ')
        return 0
    if args.command == 'plan':
        plan = make_plan(args.base, args.candidate, args.base_classes, args.candidate_classes,
                         load_json(args.base_record), load_json(args.candidate_record), args.mode, args.calibrate)
        save_json(args.output, plan)
        emit_output('matrix', matrix_jobs(plan))
        return 0
    if args.command == 'ci-run':
        targets = None
        if args.scope == 'affected':
            require(args.plan, 'Affected workers require a plan')
            plan = load_json(args.plan)
            expected_jobs(plan)
            entry = plan['revisions'][args.label]
            require(args.revision == entry['revision'], 'Worker revision differs from plan')
            targets = sorted(x for group in entry['shards'] for x in group) if args.shard == 'all' else entry['shards'][int(args.shard)]
            if not targets:
                require(not args.output.exists(), 'Fresh evidence directory required')
                save_json(args.output / 'record.json', {'version': 1, **entry, 'scope': 'affected',
                    'label': args.label, 'shard': args.shard, 'trial': args.trial, 'empty': True,
                    'targets': [], 'exit': 0, 'complete': True})
                return 0
        record = ci_run(args.checkout, args.revision, args.label, args.scope, args.output, targets, args.shard,
                        args.trial, entry if args.scope == 'affected' else None)
        if args.scope == 'affected':
            check_provenance(entry, record)
        return 0
    if args.command == 'validate':
        aggregates = validate_plan(load_json(args.manifest), args.reports)
        for label, aggregate in aggregates.items():
            save_json(args.output / label / 'aggregate.json', aggregate)
            source = next(path.parent for path in args.reports.rglob('record.json')
                          if (value := load_json(path)).get('label') == label and value.get('scope') == 'build')
            require(class_inventory(source / 'classes') == aggregate['inputs']['production'], 'Archived classes differ')
            shutil.copytree(source / 'classes', args.output / label / 'classes')
        save_json(args.output / 'measurements.json', [load_json(path) for path in args.reports.rglob('record.json')])
        return 0
    if args.command == 'compare':
        old, new = load_json(args.baseline / 'aggregate.json'), load_json(args.candidate / 'aggregate.json')
        require(old['version'] == new['version'] == 1 and old['protocol'] == new['protocol'], 'Protocols differ')
        require(class_inventory(args.baseline / 'classes') == old['inputs']['production']
                and class_inventory(args.candidate / 'classes') == new['inputs']['production'], 'Comparison class hashes differ')
        old_methods = compiled_methods(args.baseline / 'classes', javap=args.javap)
        new_methods = compiled_methods(args.candidate / 'classes', javap=args.javap)
        reports = {}
        for population, field in (('affected', 'rows'), ('default', 'defaultRows')):
            before, after = decode_rows(old[field]), decode_rows(new[field])
            patterns = SCOPE if population == 'affected' else DEFAULT_SCOPE
            selected = lambda methods: {key: value for key, value in methods.items()
                                        if any(fnmatch.fnmatchcase(key[0], pattern) for pattern in patterns)}
            result = compare_methods(selected(old_methods), selected(new_methods), before, after, protocol_verified=True)
            result.update(baseline_counts=dict(Counter(before.values())), candidate_counts=dict(Counter(after.values())))
            reports[population] = result
        save_json(args.output, reports)
        require(not any(value['lost_kills'] for value in reports.values()), 'Inherited kills regressed; inspect comparison')
        print('Matched kills preserved; unmatched methods still require recorded human dispositions.')
        return 0
    return 1



if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, OSError, ET.ParseError, KeyError, TypeError, subprocess.CalledProcessError) as error:
        print('Mutation verification failed: ' + str(error), file=sys.stderr)
        sys.exit(1)
