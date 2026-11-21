"""Reproduce the 94C per-class comparison from saved PIT XML and compiled snapshots.

This diagnostic does not run PIT, change its gates, or prove semantic equivalence.
It writes only derived comparison files beside the supplied evidence; raw reports
remain separate. The release ledger documents the required input filenames.
"""
from pathlib import Path
from collections import Counter
import argparse, subprocess, re, json, xml.etree.ElementTree as E
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('evidence',type=Path,help='Directory containing the saved XML reports named in the ledger')
parser.add_argument('baseline_classes',type=Path,help='Baseline build/classes/java/main directory')
parser.add_argument('feature_classes',type=Path,help='Feature build/classes/java/main directory')
parser.add_argument('--javap',default='javap',help='JDK 26 javap executable')
parser.add_argument('--replacement-report',action='append',help='Whole-class replacement XML in evidence; repeat in chronological order (default: historical94C reports)')
args=parser.parse_args()
EVIDENCE=args.evidence

def read(path):
    """Read unique method/mutator/instruction/block identities, excluding unstable source lines."""
    result={}
    tree=E.parse(path).getroot()
    assert tree.tag=='mutations' and len(tree)>0, 'Expected a nonempty completed PIT report'

    for m in tree:
        key=tuple(m.findtext(x,'') for x in ('mutatedClass','mutatedMethod','methodDescription','mutator'))+(tuple(x.text for x in m.findall('indexes/index')),tuple(x.text for x in m.findall('blocks/block')))
        assert key not in result,key
        assert m.get('status') in ('KILLED','SURVIVED','NO_COVERAGE'), 'Non-assertion/resource status in comparison'
        result[key]=m.get('status')
    return result

def normalized_code(lines):
    """Normalize pool indexes/ldc widths and retain control-flow destinations as instruction ordinals."""
    # Constant-pool growth may widen ldc and move BCIs without changing instructions.
    # Preserve branch/switch/handler destinations as instruction ordinals instead.
    pattern=re.compile(r'^\s*(\d+):\s+([a-z][a-z_0-9]*)(.*)$')
    offsets={int(m[1]):n for n,m in enumerate(filter(None,(pattern.match(line) for line in lines)))}
    def position(value):
        """Name an instruction or exclusive end position for disassembly comparison."""
        return '@'+str(offsets.get(int(value),'END'))
    result=[]
    for line in lines:
        m=pattern.match(line)
        if m:
            op=m[2];args=m[3].strip()
            if op=='ldc_w':op='ldc'
            if op.startswith('if') or op in ('goto','goto_w','jsr','jsr_w'):
                args=re.sub(r'^-?\d+',lambda n:position(n[0]),args)
            result.append(op+' '+args)
        elif (m:=re.match(r'^\s*(-?\d+|default):\s*(-?\d+)\s*$',line)):
            result.append(m[1]+':'+position(m[2]))
        elif (m:=re.match(r'^\s*(\d+)\s+(\d+)\s+(\d+)\s+(any|Class .*)$',line)):
            result.append('handler '+','.join(position(m[n]) for n in (1,2,3))+' '+m[4])
    return '\n'.join(result)

def methods(directory, classes):
    """Disassemble existing baseline class names and index normalized code by method descriptor."""
    present=[c for c in sorted(classes) if (directory/(c.replace('.','/')+'.class')).is_file()]
    source=subprocess.check_output([args.javap,'-p','-c','-s','-classpath',str(directory),*present],text=True)
    result={}; owner=None; method=None; descriptor=None; body=[]
    def finish():
        """Retain the current descriptor-complete method before reading the next declaration."""
        if method is not None and descriptor is not None:
            result[(owner,method,descriptor)]=normalized_code(body)
    for line in source.splitlines():
        match=re.match(r'(?:public |final |abstract |sealed )*(?:class|interface|record) ([^ <]+)',line)
        if match:
            finish(); owner=match[1]; method=descriptor=None; body=[]
        elif re.match(r'  \S.*\(.*\).*;',line) or line=='  static {};':
            finish(); header=line.strip(); name=header.split('(')[0].split()[-1]
            method='<clinit>' if line=='  static {};' else '<init>' if name==owner else name
            descriptor=None; body=[]
        elif method is not None:
            if line.strip().startswith('descriptor:'):descriptor=line.strip().split(': ',1)[1]
            else:body.append(re.sub(r'#\d+', '#',line.rstrip()))
    finish()
    return result

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
    classes={k[0] for k in old}
    before=methods(args.baseline_classes,classes)
    after=methods(args.feature_classes,classes)
    unchanged={k for k,v in before.items() if k in after and v.strip()==after[k].strip()}
    comparable={k:v for k,v in old.items() if k[:3] in unchanged}
    lost=[(k,v,new.get(k)) for k,v in comparable.items() if v=='KILLED' and new.get(k)!='KILLED']
    changed=Counter((k[0],k[1],k[2]) for k in old if k[:3] not in unchanged)
    report={'unchanged_method_baseline':dict(Counter(comparable.values())), 'lost_kills':lost,
            'changed_or_moved_methods':[{'class':k[0],'method':k[1],'descriptor':k[2],'baseline_mutants':v} for k,v in sorted(changed.items())],
            'improvements':{str(k):v for k,v in Counter((v,new.get(k)) for k,v in comparable.items() if k in new and v!=new[k]).items()}}
    (EVIDENCE/'comparison-inherited.json').write_text(json.dumps(report,indent=2))
    print(json.dumps({k:v for k,v in report.items() if k!='changed_or_moved_methods'},indent=2))
    assert not lost,'Inherited kills regressed in unchanged compiled methods'
