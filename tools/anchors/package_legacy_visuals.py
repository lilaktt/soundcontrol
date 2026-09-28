from pathlib import Path
import hashlib
import json
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/anchors-1.20.1-visual-fix'
OUT.mkdir(exist_ok=True)
checksums = []
for port in ['fabric-1.20.1', 'neoforge-1.20.1']:
    files = [p for p in (ROOT / port / 'build/libs').glob('*-1.6.0-*.jar') if not any(s in p.name for s in ['-sources', '-dev', '-javadoc'])]
    assert len(files) == 1, files
    jar = files[0]
    prefix = 'soundcontrol/anchor/' if port.startswith('fabric') else 'soundcontrol/'
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        assert 'soundcontrol/AnchorRenderFrame.class' in names
        assert 'soundcontrol/mixin/AnchorWorldRenderMixin.class' in names
        assert b'soundcontrol/AnchorRenderFrame' in z.read(prefix + 'SoundAnchorRenderer.class')
        config = json.loads(z.read('soundcontrol.mixins.json'))
        assert 'AnchorWorldRenderMixin' in config['client']
        if port.startswith('neoforge'):
            refmap = json.loads(z.read('soundcontrol.refmap.json'))
            assert refmap['mappings']['soundcontrol/mixin/AnchorWorldRenderMixin']['renderLevel'].startswith('m_109599_(')
        else:
            assert json.loads(z.read('fabric.mod.json'))['version'] == '1.6.0-1.20.1'
    shutil.copy2(jar, OUT / jar.name)
    checksums.append(hashlib.sha256(jar.read_bytes()).hexdigest() + '  ' + jar.name)
    print('PASS', port, '->', (OUT / jar.name).relative_to(ROOT))
(OUT / 'SHA256SUMS.txt').write_text('\n'.join(checksums) + '\n', encoding='utf-8')
(OUT / 'README.txt').write_text('1.20.1 visual hotfix, mod version 1.6.0. Install only the jar matching your loader, replacing the old jar.\nMenu dimming and captured world-render matrices are included. Build/tests passed; in-game visual verification is still required.\n', encoding='utf-8')
