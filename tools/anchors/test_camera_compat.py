"""Check the camera ABI used by the 1.21.9–1.21.11 Fabric jar.
Requires cached Yarn Minecraft jars/mappings for 1.21.9 and 1.21.11 and JDK javap.
Optionally pass the built remapped mod jar to check its actual method references.
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/fabric-loom'
JAVAP = shutil.which('javap') or str(Path(os.environ['JAVA_HOME']) / 'bin/javap.exe')


def inspect(jar, cls):
    return subprocess.run([JAVAP, '-p', '-c', '-classpath', str(jar), cls],
                          capture_output=True, text=True, check=True).stdout


for version in ['1.21.9', '1.21.11']:
    jar = next(p for p in (CACHE / 'minecraftMaven/net/minecraft/minecraft-merged')
               .glob(version + '-*yarn*/*.jar') if not p.name.endswith('-sources.jar'))
    camera = inspect(jar, 'net.minecraft.client.render.Camera')
    assert 'public net.minecraft.util.math.Vec3d getCameraPos();' in camera, version
    method = camera.split('public net.minecraft.util.math.Vec3d getCameraPos();', 1)[1].split('\n  public ', 1)[0]
    # This is the actual camera position (including third-person offset), not player eye position.
    if 'getPos:()Lnet/minecraft/util/math/Vec3d;' in method:
        getter = camera.split('public net.minecraft.util.math.Vec3d getPos();', 1)[1].split('\n  public ', 1)[0]
        assert 'Field pos:Lnet/minecraft/util/math/Vec3d;' in getter, version
    else:
        assert 'Field pos:Lnet/minecraft/util/math/Vec3d;' in method, version
    mapping = next((CACHE / version).glob('*yarn*/mappings.tiny')).read_text(encoding='utf-8')
    active = False
    found = False
    for line in mapping.splitlines():
        if line.startswith('c\t'):
            active = line.endswith('net/minecraft/world/waypoint/TrackedWaypoint$YawProvider')
        if active and line.endswith('\tgetCameraPos'):
            assert '\tmethod_71156\t' in line, (version, line)
            found = True
    assert found, version
    if version == '1.21.11':
        assert 'public net.minecraft.util.math.Vec3d getPos();' not in camera
    print('PASS real Minecraft camera ABI and mapping:', version)

for relative in ['anchor/SoundAnchorRenderer.java', 'render/SoundWorldRenderer.java']:
    source = (ROOT / 'fabric-1.21.9/src/main/java/soundcontrol' / relative).read_text(encoding='utf-8')
    assert 'camera.getCameraPos()' in source, relative
    assert 'camera.getPos()' not in source, relative
print('PASS both renderers use the compatible camera method')

if len(sys.argv) > 1:
    jar = Path(sys.argv[1])
    for cls in ['soundcontrol.anchor.SoundAnchorRenderer', 'soundcontrol.render.SoundWorldRenderer']:
        code = inspect(jar, cls)
        assert 'net/minecraft/class_4184.method_71156:()Lnet/minecraft/class_243;' in code, cls
        assert 'net/minecraft/class_4184.method_19326:' not in code, cls
    print('PASS remapped JAR uses method_71156; removed method_19326 is absent')
