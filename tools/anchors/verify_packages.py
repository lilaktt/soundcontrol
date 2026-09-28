"""Verify and collect the latest built platform jars without touching installed mods."""
from pathlib import Path
import hashlib,json,shutil
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/anchor-port-jars'
OUT.mkdir(exist_ok=True)
import zipfile
ports=sorted(p for p in ROOT.iterdir() if p.is_dir() and p.name.startswith(('fabric-','neoforge-')))
assert len(ports)==17
manifest=[]
for port in ports:
    candidates=[p for p in (port/'build/libs').glob('*.jar') if not any(t in p.name for t in ['-sources','-dev','-javadoc'])]
    jar=max(candidates,key=lambda p:p.stat().st_mtime)
    fab=port.name.startswith('fabric-')
    prefix='soundcontrol/anchor/' if fab else 'soundcontrol/'
    with zipfile.ZipFile(jar) as z:
        names=set(z.namelist())
        for cls in ['SoundAnchor','SoundAnchorScreen','SoundAnchorEditScreen','SoundAnchorRenderer']:
            assert prefix+cls+'.class' in names,(port.name,cls)
        assert ('soundcontrol/gui/' if fab else 'soundcontrol/')+'AllSoundsPickerScreen.class' in names
        engine=z.read('soundcontrol/mixin/SoundEngineMixin.class')
        for method in ['soundcontrol$anchorVolume','soundcontrol$refreshAnchors']:
            assert method.encode() in engine,(port,method)
        if port.name.endswith('-1.20.1'):assert b'soundcontrol$initialAnchorVolume' in engine
        config=z.read('soundcontrol/SoundConfig.class')
        assert b'getAnchorSettingVolume' in config and b'getAnchorVolumeModifier' in config
        menu=z.read(('soundcontrol/gui/' if fab else 'soundcontrol/')+'SoundControlScreen.class')
        assert (prefix+'SoundAnchorScreen').encode() in menu
        assert any((prefix+'SoundAnchorRenderer').encode() in z.read(name)
                   for name in names if name in ['soundcontrol/SoundControl.class','soundcontrol/mixin/GuiMixin.class'])
        mixins=json.loads(z.read('soundcontrol.mixins.json'))
        assert 'SoundEngineMixin' in mixins.get('client',[])+mixins.get('mixins',[])
        for name in names:
            if name.startswith('assets/soundcontrol/lang/') and name.endswith('.json'):
                lang=json.loads(z.read(name).decode('utf-8-sig'))
                assert 'text.soundcontrol.anchors.title' in lang
                assert 'text.soundcontrol.anchors.edit_title' in lang
        if port.name=='neoforge-1.20.1':
            refmap=json.loads(z.read('soundcontrol.refmap.json'))
            mapping=refmap['mappings']['soundcontrol/mixin/SoundEngineMixin']
            assert mapping['tick(Z)V']=='m_120302_(Z)V'
            assert mapping['Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F'].endswith('m_235257_(FLnet/minecraft/sounds/SoundSource;)F')
    destination=OUT/jar.name
    shutil.copy2(jar,destination)
    manifest.append(hashlib.sha256(destination.read_bytes()).hexdigest()+'  '+destination.name)
    print('PASS packaged '+port.name+' -> '+str(destination.relative_to(ROOT)))
(OUT/'SHA256SUMS.txt').write_text('\n'.join(manifest)+'\n',encoding='utf-8')
(OUT/'README.txt').write_text('Sound Anchor port: 17 platform jars. Install only ONE jar matching your Minecraft version and loader.\n'
    'Replace the previous Sound Control jar, do not keep both installed.\n'
    'Build and headless regression checks passed. In-game UI, OpenAL and runtime Mixin smoke tests remain required.\n'
    'Existing config/soundcontrol/settings.json is retained. Back it up before testing.\n'
    'See tools/anchors/README.md in the source project for behavior, limitations and the smoke-test checklist.\n',encoding='utf-8')
