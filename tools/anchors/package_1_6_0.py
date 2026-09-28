"""Verify and collect only Sound Control 1.6.0 release jars (never install them)."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/soundcontrol-1.6.0'
OUT.mkdir(exist_ok=True)
checksums = []
ports = sorted(p for p in ROOT.iterdir() if p.is_dir() and p.name.startswith(('fabric-', 'neoforge-')))
assert len(ports) == 17
for port in ports:
    jars = [p for p in (port / 'build/libs').glob('*-1.6.0-*.jar')
            if not any(part in p.name for part in ['-sources', '-dev', '-javadoc'])]
    assert len(jars) == 1, (port.name, jars)
    jar = jars[0]
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        assert 'soundcontrol/AnchorWorldContext.class' in names
        config = z.read('soundcontrol/SoundConfig.class')
        settings = z.read('soundcontrol/SoundConfig$AppSettings.class')
        assert b'soundcontrol/AnchorWorldContext' in config
        assert b'anchorsByWorld' in settings and b'anchors' in settings
        assert b'importLegacyAnchors' in config and b'getLegacyAnchorCount' in config
        assert b'getAnchorVolumeModifier' in config
        fab = port.name.startswith('fabric-')
        prefix = 'soundcontrol/anchor/' if fab else 'soundcontrol/'
        screen = z.read(prefix + 'SoundAnchorScreen.class')
        assert b'anchors.import_legacy' in screen and b'worldKey' in screen
        for cls in ['SoundAnchor', 'SoundAnchorEditScreen', 'SoundAnchorRenderer']:
            assert prefix + cls + '.class' in names
        assert b'soundcontrol$anchorVolume' in z.read('soundcontrol/mixin/SoundEngineMixin.class')
        if fab:
            meta = json.loads(z.read('fabric.mod.json'))
            version = meta['version']
        else:
            meta = z.read('META-INF/mods.toml' if port.name.endswith('1.20.1') else 'META-INF/neoforge.mods.toml').decode('utf-8')
            version = re.search(r'(?m)^version\s*=\s*"([^"]+)"', meta)[1]
        assert version.startswith('1.6.0-'), (port.name, version)
        assert '${version}' not in version
        for file in names:
            if file.startswith('assets/soundcontrol/lang/') and file.endswith('.json'):
                lang = json.loads(z.read(file).decode('utf-8-sig'))
                assert 'text.soundcontrol.anchors.import_legacy' in lang
    destination = OUT / jar.name
    shutil.copy2(jar, destination)
    checksums.append(hashlib.sha256(destination.read_bytes()).hexdigest() + '  ' + destination.name)
    print('PASS', port.name, version)
(OUT / 'SHA256SUMS.txt').write_text('\n'.join(checksums) + '\n', encoding='utf-8')
(OUT / 'README.txt').write_text(
    'Sound Control 1.6.0: per-world Sound Anchors, all 17 platform modules.\n'
    'Install ONE jar matching your Minecraft version and loader, replacing the previous mod.\n'
    'Back up config/soundcontrol/settings.json before upgrading.\n'
    'Old global anchors remain unassigned/inactive. Enter their intended world and use the explicit import button.\n'
    'Local identity: save folder path. Multiplayer identity: server address and port. Dimensions remain separate filters.\n'
    'Build, headless tests and package checks passed. In-game UI/audio tests still required.\n', encoding='utf-8')
print('Release folder:', OUT)
