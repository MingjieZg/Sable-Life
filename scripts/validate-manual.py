"""Validate coverage and references without starting or modifying a world."""
from pathlib import Path
import json, re

root=Path(__file__).resolve().parents[1]
assets=root/'src/main/resources/assets/sablelife'
catalog=json.loads((assets/'manual/catalog.json').read_text(encoding='utf-8'))
languages={lang:json.loads((assets/f'lang/{lang}.json').read_text(encoding='utf-8')) for lang in ('en_us','zh_cn')}
source=(root/'src/main/java/dev/sablelife/module/VirtualModule.java').read_text(encoding='utf-8')
types=set(re.findall(r'"([a-z_]+)"',source.split('AVAILABLE_TYPES = List.of(')[1].split(');')[0]))
ids=[entry['id'] for entry in catalog['entries']]
assert len(ids)==len(set(ids)), 'Duplicate chapter ID'
assert ids[:2]==['getting_started','simple_start'], 'Core introduction must precede module introduction'
assert 'core_operations' not in ids, 'Duplicate core introduction chapter'
assert {e['module'] for e in catalog['entries'] if 'module' in e}==types, 'Module coverage differs from Add module catalog'
assert 'dispatch' not in ids, 'Deprecated dispatcher advertised'
assert 'combo_flame' not in ids, 'Withdrawn provisional example still advertised'
assert {'getting_started','simple_start','basic_operations','module_management','marking_system','preview_system'}.issubset(ids), 'Missing basic operation guide'
for entry in catalog['entries']:
    assert set(entry['sections'])=={'usage','settings','example','troubleshooting'}
    keys=[entry['title']]+[k for values in entry['sections'].values() for k in values]
    for lang,strings in languages.items():
        assert all(k in strings and strings[k].strip() for k in keys), (lang,entry['id'])
    for bar in entry.get('timeline',[]):
        assert bar['module'] in types and bar['start']>=0 and bar['duration']>0
assert catalog['revision']>=1
layout=json.loads((root/'scripts/manual-paragraphs.json').read_text(encoding='utf-8'))
polished=json.loads((root/'scripts/chinese-polish.json').read_text(encoding='utf-8'))
for suffix,boundaries in layout.items():
    for lang,starts in boundaries.items():
        if lang=="zh_cn" and "manual.sablelife."+suffix in polished:continue
        value=languages[lang]['manual.sablelife.'+suffix]
        if lang == 'en_us':
            assert all('\n\n'+start in value for start in starts), (lang,suffix,'Missing reviewed paragraph break')
        else:
            # Chinese editing may change a paragraph's opening phrase while preserving its layout.
            assert value.count('\n\n') >= len(starts), (lang,suffix,'Missing translated paragraph break')
for entry in catalog['entries']:
    for keys in entry['sections'].values():
        for key in keys:
            for lang,strings in languages.items():
                value=strings[key]
                assert not value.startswith('\n') and not value.endswith('\n') and '\n\n\n' not in value, (lang,key,'Extra blank lines')
                assert not re.search(r'(?<!\n)\n\d+\. ',value), (lang,key,'Numbered steps need spacing')
print(f"Manual revision {catalog['revision']}: {len(ids)} chapters, {len(types)} modules, four tabs and both locales validated.")
