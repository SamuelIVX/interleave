"""Strict PIT XML identities and conservative javap comparison, shared by CI and historical tools."""
from pathlib import Path
from collections import Counter
from dataclasses import dataclass
import hashlib
import os
import re
import subprocess
import xml.etree.ElementTree as ET

STATUSES = frozenset(("KILLED", "SURVIVED", "NO_COVERAGE"))


@dataclass(frozen=True)
class MethodCode:
    """Disassembly plus whole-class coordinate proof, or an explicit unresolved reason."""
    code: str | None
    class_digest: str
    reason: str | None = None


def require(condition: object, message: str) -> None:
    """Fail a trust-boundary check even when Python assertions are disabled."""
    if not condition:
        raise ValueError(message)


def read_report(path: Path | str, allowed: frozenset = STATUSES) -> dict:
    """Read unique ordered semantic identities; reject entities, missing fields and error statuses."""
    # Pinned PIT emits UTF-8; reject NUL/alternative encodings before checking declarations.
    data = Path(path).read_bytes().decode('utf-8-sig')
    require('\0' not in data and '<!DOCTYPE' not in data.upper() and '<!ENTITY' not in data.upper(),
            'XML declarations/unsupported encoding forbidden')
    root = ET.fromstring(data)
    require(root.tag == "mutations" and len(root) > 0, "Expected nonempty completed PIT XML")
    result = {}
    for item in root:
        require(item.tag == "mutation", "Unexpected PIT element")
        fields = tuple(item.findtext(name) for name in
                       ("mutatedClass", "mutatedMethod", "methodDescription", "mutator"))
        require(all(value and value.strip() == value for value in fields), "Missing mutant identity")
        coordinates = []
        for parent, child in (("indexes", "index"), ("blocks", "block")):
            values = item.findall(f"{parent}/{child}")
            require(values and all(re.fullmatch(r"\d+", v.text or "") for v in values), "Missing/invalid coordinates")
            coordinates.append(tuple(int(v.text) for v in values))
        key = fields + tuple(coordinates)
        require(key not in result, "Duplicate mutant identity")
        status = item.get("status")
        require(status in allowed, f"Unsupported/non-assertion status: {status}")
        result[key] = status
    return result


def normalized_code(lines: list[str]) -> str | None:
    """Normalize resolved javap operands/positions, returning None for unproven dynamic behavior."""
    instruction = re.compile(r'^\s*(\d+):\s+([a-z][a-z_0-9]*)(.*)$')
    matches = [m for line in lines if (m := instruction.match(line))]
    offsets = {int(m[1]): index for index, m in enumerate(matches)}

    def position(raw, end=False):
        """Map a branch or exclusive handler end without conflating unknown destinations."""
        value = int(raw)
        if value in offsets:
            return '@' + str(offsets[value])
        if end and offsets and value > max(offsets):
            return '@END'
        raise ValueError('Unresolved instruction position')

    result = []
    try:
        for line in lines:
            if m := instruction.match(line):
                op, arguments = m[2], m[3].lstrip()
                operand, separator, resolved = arguments.partition('//')
                if op == 'invokedynamic' or re.match(r'\s*(?:Dynamic\b|(?:float|double) NaN)', resolved):
                    return None
                if '#' in operand:
                    if not separator:
                        return None
                    operand = re.sub(r'#\d+', '#', operand)
                if op.startswith('if') or op in ('goto', 'goto_w', 'jsr', 'jsr_w'):
                    operand = re.sub(r'^-?\d+', lambda value: position(value[0]), operand)
                op = 'ldc' if op == 'ldc_w' else op
                result.append(op + ' ' + operand.strip() + (' // ' + resolved.lstrip() if separator else ''))
            elif m := re.match(r'^\s*(-?\d+|default):\s*(-?\d+)\s*$', line):
                result.append(m[1] + ':' + position(m[2]))
            elif m := re.match(r'^\s*(\d+)\s+(\d+)\s+(\d+)\s+(any|Class .*)$', line):
                result.append('handler ' + ','.join((position(m[1]), position(m[2], True), position(m[3]))) + ' ' + m[4])
    except ValueError:
        return None
    return '\n'.join(result)


def compiled_methods(directory: Path | str, classes: set[str] | None = None, javap: str = 'javap') -> dict:
    """Read both complete class inventories with javap; never load target classes into this process."""
    directory = Path(directory)
    available = {str(p.relative_to(directory).with_suffix('')).replace(os.sep, '.')
                 for p in directory.rglob('*.class')}
    selected = sorted(available if classes is None else available.intersection(classes))
    digests = {name: hashlib.sha256((directory / (name.replace('.', '/') + '.class')).read_bytes()).hexdigest()
               for name in selected}
    result = {}
    # Bound argv length for growing inventories; keep each class's methods together.
    for start in range(0, len(selected), 50):
        source = subprocess.check_output([javap, '-p', '-c', '-s', '-classpath', str(directory),
                                          *selected[start:start + 50]], text=True)
        owner = method = descriptor = header = None
        body = []

        def finish():
            """Retain one complete declaration, including methods with no generated mutants."""
            if method is not None and descriptor is not None:
                normalized = normalized_code(body)
                result[(owner, method, descriptor)] = MethodCode(
                    None if normalized is None else header + '\n' + normalized, digests[owner],
                    'unresolved-bootstrap-or-constant' if normalized is None else None)

        for line in source.splitlines():
            declaration = re.match(r'^(?:(?:public|final|abstract|sealed|non-sealed|static) )*(?:class|interface|record|enum) ([^ <]+)', line)
            if declaration:
                finish()
                owner = declaration[1]
                method = descriptor = None
                body = []
            elif re.match(r'  \S.*\(.*\).*;', line) or line == '  static {};':
                finish()
                header = line.strip()
                name = header.split('(')[0].split()[-1]
                method = '<clinit>' if line == '  static {};' else '<init>' if name == owner else name
                descriptor = None
                body = []
            elif method is not None:
                if line.strip().startswith('descriptor:'):
                    descriptor = line.strip().split(': ', 1)[1]
                else:
                    body.append(line)
        finish()
    return result


def compare_methods(before: dict, after: dict, baseline: dict, candidate: dict) -> dict:
    """Report lost matched kills and all unmatched methods; leave their dispositions to reviewers."""
    # shortcut: raw ASM indexes are proved stable only for identical defining class bytes;
    # use an independently validated instruction mapper before widening this proven subset.
    unchanged = {key for key in before.keys() & after.keys()
                 if before[key].code is not None and before[key] == after[key]}
    comparable = {key: status for key, status in baseline.items() if key[:3] in unchanged}
    unmatched = []
    methods = before.keys() | after.keys() | {key[:3] for key in baseline} | {key[:3] for key in candidate}
    for key in sorted(methods - unchanged):
        old, new = before.get(key), after.get(key)
        if old is None and new is None:
            change = 'unresolved-method-inventory'
        elif old is None:
            change = 'added'
        elif new is None:
            change = 'removed'
        else:
            change = old.reason or new.reason or ('changed-code' if old.code != new.code else 'unresolved-coordinates')
        unmatched.append(dict(zip(('class', 'method', 'descriptor'), key),
                              change=change,
                              baseline_mutants=sum(k[:3] == key for k in baseline),
                              candidate_mutants=sum(k[:3] == key for k in candidate)))
    return {'unchanged_method_baseline': dict(Counter(comparable.values())),
            'lost_kills': [key for key, status in comparable.items()
                           if status == 'KILLED' and candidate.get(key) != 'KILLED'],
            'improvements': {str(key): count for key, count in Counter(
                (status, candidate[key]) for key, status in comparable.items()
                if key in candidate and status != candidate[key]).items()},
            'unmatched_methods': unmatched}
