"""Check generated anchor-list geometry for every port (JDK 17+). No Minecraft UI launch."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/anchor-layout-tests'
OUT.mkdir(parents=True, exist_ok=True)
paths = sorted(ROOT.glob('*/src/main/java/soundcontrol/**/SoundAnchorScreen.java'))
assert len(paths) == 17
classes, calls = [], []
for index, path in enumerate(paths):
    source = path.read_text(encoding='utf-8')
    methods = []
    for name in ['getRowWidth', 'getRowLeft']:
        match = re.search(r'public int ' + name + r'\(\) \{[^}]+}', source)
        assert match, (path, name)
        methods.append(match.group())
    scrollbar = re.search(r'protected int (?:getScrollbarPositionX|getScrollbarPosition|getScrollbarX|scrollBarX)\(\) \{[^}]+}', source)
    assert scrollbar, path
    methods.append(re.sub(r'protected int \w+\(', 'public int scrollbar(', scrollbar.group()))
    assert 'this.hBox.setX(bx + 74)' in source, path
    assert 'this.wBox.setX(bx + 16)' in source, path
    assert 'this.dBox.setX(bx + 132)' in source, path
    assert 'x + 184, y + 28' in source, path  # Count belongs on second row, not beyond buttons.
    if 'renderContent(' in source or 'extractContent(' in source:
        assert 'int x = this.getX();' in source, path
    elif path.relative_to(ROOT).parts[0] == 'fabric-1.21.9':
        assert 'int x = parentScreen.anchorList.getRowLeft();' in source, path
    classes.append('static class Port' + str(index) + ' { int width; ' + '\n'.join(methods) + ' }')
    calls.append('''{
        PortINDEX port = new PortINDEX();
        for (int width : new int[]{320, 427, 640, 854, 960, 1280, 1920}) {
            port.width = width;
            int left = port.getRowLeft(), row = port.getRowWidth(), scroll = port.scrollbar();
            check(row == 248, "compact row");
            check(scroll - (left + row) == 4, "scrollbar follows row edge");
            check(Math.abs((left + scroll + 6) - width) <= 1, "content and scrollbar centered together");
            int deleteRight = left + 2 + 22 + 84 + 46 + 24 + 44 + 20;
            check(scroll - deleteRight >= 4 && scroll - deleteRight <= 14, "no large empty gap");
            check(left + 184 + 58 <= left + row, "count stays within row");
            check(left + 2 + 16 + 40 <= left + 2 + 74, "W and H do not overlap");
            check(left + 2 + 74 + 40 <= left + 2 + 132, "H and D do not overlap");
            check(left >= 0 && scroll + 6 < width, "bounds");
        }
        System.out.println("PASS PORTNAME");
    }'''.replace('INDEX', str(index)).replace('PORTNAME', path.relative_to(ROOT).parts[0]))
java = OUT / 'AnchorLayoutTest.java'
java.write_text('public class AnchorLayoutTest {\n' + '\n'.join(classes) + '''
static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
public static void main(String[] args) {
''' + '\n'.join(calls) + '\n}\n}\n', encoding='utf-8')
subprocess.run(['javac', '--release', '17', '-d', str(OUT), str(java)], check=True)
subprocess.run(['java', '-cp', str(OUT), 'AnchorLayoutTest'], check=True)
