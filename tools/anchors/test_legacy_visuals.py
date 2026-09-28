from pathlib import Path
import json
import os
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/legacy-anchor-visual-tests'
JOML = next((Path.home() / '.gradle/caches/modules-2/files-2.1/org.joml/joml/1.10.5').glob('*/joml-1.10.5.jar'))
TEST = '''package soundcontrol;
import org.joml.*;
public class LegacyVisualTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        Object world = new Object();
        Vec position = new Vec(-35.5, 67, 102.25);
        for (float yaw : new float[]{0, 45, 90, 180, 270}) {
            for (float pitch : new float[]{-85, -30, 0, 30, 85}) {
                for (float fov : new float[]{30, 70, 90, 110}) {
                    Matrix4f view = new Matrix4f().rotateX((float)java.lang.Math.toRadians(pitch))
                            .rotateY((float)java.lang.Math.toRadians(yaw + 180));
                    Matrix4f projection = new Matrix4f().perspective((float)java.lang.Math.toRadians(fov), 16f / 9f, 0.05f, 512)
                            .translate(0.025f, -0.05f, 0).rotateZ(0.03f);
                    Matrix4f originalView = new Matrix4f(view), originalProjection = new Matrix4f(projection);
                    AnchorRenderFrame.capture(world, view, projection, position);
                    check(view.equals(originalView) && projection.equals(originalProjection), "capture must not mutate game matrices");
                    var frame = AnchorRenderFrame.take(world);
                    check(frame != null && frame.position() == position, "same camera origin");
                    for (int i = 0; i < 8; i++) {
                        Vector4f point = new Vector4f((i & 1) == 0 ? -8 : 8, (i & 2) == 0 ? -5 : 5, (i & 4) == 0 ? -12 : 12, 1);
                        Vector4f expected = originalProjection.transform(originalView.transform(new Vector4f(point)));
                        Vector4f actual = frame.viewProjection().transform(new Vector4f(point));
                        check(actual.equals(expected, 0.0001f), "anchor must use the same world transform for all angles/FOV/bobbing");
                    }
                    Matrix4f snapshot = new Matrix4f(frame.viewProjection());
                    view.identity(); projection.identity();
                    check(frame.viewProjection().equals(snapshot), "later GUI/hand transforms cannot alter captured frame");
                    check(AnchorRenderFrame.take(world) == null, "frame consumed once");
                }
            }
        }
        AnchorRenderFrame.capture(world, new Matrix4f(), new Matrix4f(), position);
        check(AnchorRenderFrame.take(new Object()) == null, "no previous-world geometry");
        AnchorRenderFrame.capture(world, new Matrix4f(), new Matrix4f(), position);
        check(AnchorRenderFrame.take(null) == null, "no disconnected-world geometry");
        check(AnchorRenderFrame.take(world) == null, "old frame cleared");
        AnchorRenderFrame.capture(null, new Matrix4f(), new Matrix4f(), position);
        check(AnchorRenderFrame.take(world) == null, "null capture clears snapshot");
        System.out.println("PASS 100 view/FOV transforms, 800 corners, immutable capture, one-frame and world isolation");
    }
}
class Vec {
    final double x, y, z;
    Vec(double x, double y, double z) { this.x=x; this.y=y; this.z=z; }
}
'''
for port in ['fabric-1.20.1', 'neoforge-1.20.1']:
    java = ROOT / port / 'src/main/java/soundcontrol'
    for name in ['SoundAnchorScreen.java', 'SoundAnchorEditScreen.java', 'AllSoundsPickerScreen.java']:
        source = next(java.rglob(name)).read_text(encoding='utf-8')
        method = re.search(r'public void render\([^\n]+float delta\) \{(.*?)\n    }', source, re.S)[1]
        assert method.count('context.fill(0, 0, this.width, this.height, 0xC0101010);') == 1, name
        assert method.index('context.fill(') < method.index('drawPanelBackground(') < method.index('super.render('), name
        assert source.count('context.fill(0, panelTop, this.width, panelBottom, 0x80000000);') == 1, name
        assert 'this.panelTop = y; this.panelBottom = y + height;' in source, name
        for height in [240, 360, 540, 1080]:
            bounds = {'SoundAnchorScreen.java': [(24, height - 80), (24, height - 104)],
                      'SoundAnchorEditScreen.java': [(40, height - 88)],
                      'AllSoundsPickerScreen.java': [(94, max(20, height - 128))]}[name]
            for top, size in bounds:
                bottom = top + size
                assert 0 < top < bottom < height, (name, height)
        print('PASS layered background before controls:', port, name, flush=True)
    renderer = next(java.rglob('SoundAnchorRenderer.java')).read_text(encoding='utf-8')
    assert 'AnchorRenderFrame.take(' in renderer and 'frame.position()' in renderer
    assert 'frame.viewProjection()' in renderer
    assert '.perspective(' not in renderer and '.conjugate(' not in renderer
    hook = (java / 'mixin/AnchorWorldRenderMixin.java').read_text(encoding='utf-8')
    assert '@At("HEAD")' in hook and 'AnchorRenderFrame.capture(' in hook
    config = json.loads((ROOT / port / 'src/main/resources/soundcontrol.mixins.json').read_text())
    assert 'AnchorWorldRenderMixin' in config['client']
    if port.startswith('neoforge'):
        refmap = json.loads((ROOT / port / 'src/main/resources/soundcontrol.refmap.json').read_text())
        assert refmap['mappings']['soundcontrol/mixin/AnchorWorldRenderMixin']['renderLevel'].startswith('m_109599_(')
    frame = (java / 'AnchorRenderFrame.java').read_text(encoding='utf-8')
    frame = re.sub(r'import net.minecraft.[^;]+;\n', '', frame)
    frame = re.sub(r'\bVec3d?\b', 'Vec', frame)
    out = OUT / port; out.mkdir(parents=True, exist_ok=True)
    (out / 'AnchorRenderFrame.java').write_text(frame, encoding='utf-8')
    (out / 'LegacyVisualTest.java').write_text(TEST, encoding='utf-8')
    subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-cp', str(JOML), '-d', str(out),
                    str(out / 'AnchorRenderFrame.java'), str(out / 'LegacyVisualTest.java')], check=True)
    print(port, flush=True)
    subprocess.run(['java', '-cp', str(out) + os.pathsep + str(JOML), 'soundcontrol.LegacyVisualTest'], check=True)
