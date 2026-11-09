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
assert ids[:7]==['getting_started','core','module_management','marking_system','preview_system','triggers','wireless_channels'], 'Requested opening guide order differs'
assert not {'retaliation','death_unloading','sound_timeline','combat_compatibility'}.intersection(ids), 'Retired standalone guides are still listed'
categories={}
category_source=(root/'src/main/java/dev/sablelife/client/ModuleCategory.java').read_text(encoding='utf-8')
for names,category in re.findall(r'case (.*?) -> (WEAPON|SIGNAL);',category_source):
    categories.update({name:category for name in re.findall(r'"([a-z_]+)"',names)})
groups=[0 if not e.get('module') else {'SIGNAL':1,'WEAPON':2}.get(categories.get(e['module']),3) for e in catalog['entries']]
assert groups==sorted(groups), 'All must group Guides, Signals, Attacks and Functions'
assert [e['id'] for e in catalog['entries'] if not e.get('module')][-1]=='server_limits', 'Resource limits must be the final Guide'
for entry in catalog['entries']:
    if entry['id']=='getting_started':continue
    key=f'manual.sablelife.{entry["id"]}.brief_usage'
    assert entry['sections']['usage']==[key], (entry['id'],'Expected concise Usage')
    for lang,strings in languages.items():
        assert '\n' not in strings[key], (lang,entry['id'],'Expected one short Usage paragraph')
        assert len(strings[key]) <= (330 if lang=='en_us' else 150), (lang,entry['id'],'Usage too long')
for entry in catalog['entries']:
    module=entry.get('module')
    if not module or categories.get(module)=='SIGNAL':continue
    for tab,keys in entry['sections'].items():
        assert keys==[f'manual.sablelife.{module}.brief_{tab}'], (module,tab,'Expected concise module tab')
        for lang,strings in languages.items():
            assert '\n' not in strings[keys[0]], (lang,module,tab,'Expected one short paragraph')
            assert len(strings[keys[0]]) <= (330 if lang=='en_us' else 150), (lang,module,tab,'Too long')

assert not {'simple_start','basic_operations'}.intersection(ids), 'Retired duplicate introduction chapter'
assert 'core_operations' not in ids, 'Duplicate core introduction chapter'
assert {e['module'] for e in catalog['entries'] if 'module' in e}==types, 'Module coverage differs from Add module catalog'
assert 'dispatch' not in ids, 'Deprecated dispatcher advertised'
assert 'combo_flame' not in ids, 'Withdrawn provisional example still advertised'
assert {'getting_started','module_management','marking_system','preview_system'}.issubset(ids), 'Missing basic operation guide'
intro=next(e for e in catalog['entries'] if e['id']=='getting_started')
assert not set(intro['sections']['usage']).intersection(intro['sections']['example']), 'Tutorial steps repeated in Example'
assert 'manual.sablelife.getting_started.automation' in intro['sections']['usage'], 'Missing automation introduction'
for entry in catalog['entries']:
    assert set(entry['sections'])=={'usage','example','troubleshooting'}
    keys=[entry['title']]+[k for values in entry['sections'].values() for k in values]
    for lang,strings in languages.items():
        assert all(k in strings and strings[k].strip() for k in keys), (lang,entry['id'])
    for bar in entry.get('timeline',[]):
        assert bar['module'] in types and bar['start']>=0 and bar['duration']>0
    for key,figure in entry.get('figures',{}).items():
        assert any(key in keys for keys in entry['sections'].values()), (entry['id'],'unreferenced figure')
        assert figure in {'assemble','dashboard','mark','preview','activate','release'}, (entry['id'],figure)
assert set(next(e for e in catalog['entries'] if e['id']=='getting_started').get('figures',{}).values())=={'assemble','dashboard','mark','preview','activate','release'}
assert {'natural_spawning','survival_heart_core','block_interaction'}.issubset(ids)
for lang,strings in languages.items():
    for key,value in languages['en_us'].items():
        if key.startswith('manual.sablelife.figure.'):
            assert key in strings and strings[key].strip(), (lang,key)
assert catalog['revision']>=1
layout=json.loads((root/'scripts/manual-paragraphs.json').read_text(encoding='utf-8'))
polished=json.loads((root/'scripts/chinese-polish.json').read_text(encoding='utf-8'))
referenced_text={key for entry in catalog['entries'] for keys in entry['sections'].values() for key in keys}
for suffix,boundaries in layout.items():
    # Historical layout notes also contain retired chapters and replaced tabs.
    if 'manual.sablelife.'+suffix not in referenced_text:continue
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
print(f"Manual revision {catalog['revision']}: {len(ids)} chapters, {len(types)} modules, three tabs and both locales validated.")
