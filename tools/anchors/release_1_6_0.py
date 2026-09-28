"""Set mod (not loader/Minecraft) versions and add explicit legacy-anchor import labels."""
from pathlib import Path
import json
import re
ROOT = Path(__file__).resolve().parents[2]
LABELS = {
    'en_us': 'Move old anchors into this world (%s)',
    'uk_ua': 'Перенести старі якорі в цей світ (%s)',
    'ru_ru': 'Перенести старые якоря в этот мир (%s)',
    'de_de': 'Alte Anker in diese Welt verschieben (%s)',
    'es_es': 'Mover anclas anteriores a este mundo (%s)',
    'fr_fr': 'Déplacer les anciennes ancres vers ce monde (%s)',
    'it_it': 'Sposta le vecchie ancore in questo mondo (%s)',
    'pl_pl': 'Przenieś stare kotwice do tego świata (%s)',
    'pt_br': 'Mover âncoras antigas para este mundo (%s)',
    'ja_jp': '以前のアンカーをこのワールドに移動 (%s)',
    'ko_kr': '이전 앵커를 이 월드로 이동 (%s)',
    'zh_cn': '将旧锚点移至此世界（%s）',
}
for port in sorted(ROOT.iterdir()):
    if not port.is_dir() or not port.name.startswith(('fabric-', 'neoforge-')):
        continue
    p = port / 'build.gradle'
    text = p.read_text(encoding='utf-8')
    text, count = re.subn(r'(?m)^version\s*=\s*[\"\']1\.\d+\.\d+([^\"\']*)[\"\']',
                          lambda m: 'version = "1.6.0' + m[1] + '"', text)
    assert count == 1, p
    p.write_text(text, encoding='utf-8')
    for p in (port / 'src/main/resources/assets/soundcontrol/lang').glob('*.json'):
        text = json.loads(p.read_text(encoding='utf-8-sig'))
        text['text.soundcontrol.anchors.import_legacy'] = LABELS.get(p.stem, LABELS['en_us'])
        p.write_text(json.dumps(text, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print('1.6.0:', port.name)
p = ROOT / 'build.gradle'
text = re.sub(r"(?m)^(\s*version\s*=\s*)'1\.\d+\.\d+'", r"\1'1.6.0'", p.read_text(encoding='utf-8'))
p.write_text(text, encoding='utf-8')
