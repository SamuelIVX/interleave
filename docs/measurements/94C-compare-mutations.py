"""Reproduce the 94C per-class comparison from saved PIT XML and compiled snapshots.

This diagnostic does not run PIT, change its gates, or prove semantic equivalence.
It writes only derived comparison files beside the supplied evidence; raw reports
remain separate. The release ledger documents the required input filenames.
"""
from pathlib import Path
from collections import Counter
import argparse, json, xml.etree.ElementTree as E
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('evidence',type=Path,help='Directory containing the saved XML reports named in the ledger')
parser.add_argument('baseline_classes',type=Path,help='Baseline build/classes/java/main directory')
parser.add_argument('feature_classes',type=Path,help='Feature build/classes/java/main directory')
parser.add_argument('--javap',default='javap',help='JDK 26 javap executable')
parser.add_argument('--replacement-report',action='append',help='Whole-class replacement XML in evidence; repeat in chronological order (default: historical94C reports)')
args=parser.parse_args()
EVIDENCE=args.evidence

# Share the corrected parser/comparator with CI while retaining this historical CLI.
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from mutation_evidence import read_report as read, compiled_methods, compare_methods


def methods(directory, classes=None):
    """Keep the historical javap option while considering the full compiled inventory."""
    return compiled_methods(directory, classes, args.javap)

# Replace whole classes with the latest recorded run, never individual favorable mutants.
rows=list(E.parse(EVIDENCE/'affected-census.xml').getroot())
for name in (args.replacement_report or ('postfix-focused.xml','final-codec-dto.xml','final-report-pit.xml')):
    current=list(E.parse(EVIDENCE/name).getroot())
    assert current, name
    replaced={m.findtext('mutatedClass') for m in current}
    rows=[m for m in rows if m.findtext('mutatedClass') not in replaced]+current
assert all(m.get('status') in ('KILLED','SURVIVED','NO_COVERAGE') for m in rows)
assembled=E.Element('mutations',{'assembly':'latest per-class observations; not a single PIT run'})
assembled.extend(rows)
comparison=EVIDENCE/'comparison-affected.xml'
E.ElementTree(assembled).write(comparison,encoding='utf-8',xml_declaration=True)

for scope in ('default','affected'):
    final=EVIDENCE/'final-default.xml' if scope=='default' else comparison
    old=read(EVIDENCE/('baseline-'+scope+'.xml'));new=read(final)
    print(scope,'baseline',dict(Counter(old.values())),'current',dict(Counter(new.values())))
    if scope=='default':
        assert old==new,'Default fingerprints or statuses changed'
        continue
    before=methods(args.baseline_classes)
    after=methods(args.feature_classes)
    report=compare_methods(before,after,old,new)
    lost=report['lost_kills']
    # Retain the historical JSON field as an alias; the inventory now includes added methods.
    report['changed_or_moved_methods']=report['unmatched_methods']
    (EVIDENCE/'comparison-inherited.json').write_text(json.dumps(report,indent=2))
    print(json.dumps({k:v for k,v in report.items() if k!='changed_or_moved_methods'},indent=2))
    assert not lost,'Inherited kills regressed in unchanged compiled methods'
