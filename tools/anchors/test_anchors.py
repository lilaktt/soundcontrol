"""Headless checks of actual anchor/config/volume code in every port (JDK 17+, cached Gson).
Does not simulate Minecraft rendering, OpenAL or Mixin application.
"""
from pathlib import Path
import subprocess, os, re, json
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/anchor-tests'
GSON=next((Path.home()/'.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1').glob('*/gson-2.10.1.jar'))

def method(s,signature):
 a=s.index(signature);b=s.index('{',a);d=1
 for i in range(b+1,len(s)):
  d+=(s[i]=='{')-(s[i]=='}')
  if d==0:return s[a:i+1]
 raise AssertionError(signature)

TEST=r'''
package soundcontrol;
import java.util.*; import java.nio.file.*; import java.nio.charset.StandardCharsets;
public class AnchorTests {
 static void check(boolean ok,String label) { if (!ok) throw new AssertionError(label); }
 static void eq(float a,float b,String label) { check(Math.abs(a-b)<0.0001f,label+": "+a+" != "+b); }
 static SoundConfig.SoundSettings setting(float vol,boolean muted) {
  var s=new SoundConfig.SoundSettings();s.volume=vol;s.muted=muted;return s;
 }
 public static void main(String[] args) throws Exception {
  SoundAnchor a=new SoundAnchor("Тихий дім 🎵","minecraft:overworld",-10.5,64,0.25,4);
  check(a.contains("ResourceKey[minecraft:dimension / minecraft:overworld]",-6.5,64,0.25),"sphere boundary and legacy dimension");
  check(!a.contains("minecraft:overworld",-6.499,64,0.25),"outside sphere");
  check(!a.contains("minecraft:the_nether",-10.5,64,0.25),"dimension isolation");
  a.setEnabled(false);check(!a.contains("minecraft:overworld",-10.5,64,0.25),"disabled");a.setEnabled(true);
  a.setShapeMode("box");a.setBoxW(3);a.setBoxH(5);a.setBoxD(1);
  check(a.minX()==-12 && a.maxX()==-9 && a.minZ()==-0.25 && a.maxZ()==0.75,"odd box sizes preserve center");
  check(a.contains("minecraft:overworld",a.minX(),a.maxY(),a.maxZ()),"box boundary");
  check(!a.contains("minecraft:overworld",a.minX()-0.001,64,0.25),"outside box");
  check(!a.contains("minecraft:overworld",Double.NaN,64,0.25),"NaN source rejected");
  check(!a.contains(null,-10.5,64,0.25),"null dimension rejected");
  a.setRadius(Integer.MAX_VALUE);a.setBoxH(-5);check(a.getRadius()==999 && a.getBoxH()==1,"size clamps");
  SoundAnchor invalid=SoundConfig.GSON.fromJson("{\"dimension\":null,\"name\":null,\"radius\":-9,\"boxW\":2147483647,\"soundOverrides\":null}",SoundAnchor.class);
  check(!invalid.contains(null,0,0,0)&&invalid.getName()!=null&&invalid.getSoundOverrides().isEmpty(),"malformed JSON defaults");
  invalid.getSoundOverrides().put(null,setting(1,false));invalid.getSoundOverrides().put("mod:null",null);
  check(invalid.getSoundOverrides().isEmpty(),"invalid override entries discarded");
  check(invalid.getRadius()==1 && invalid.getBoxW()==999,"loaded sizes clamped");
  var overrides=a.getSoundOverrides();
  overrides.put("minecraft:entity.zombie",setting(0,true));
  eq(a.getVolumeModifier("minecraft:entity.zombie.hurt"),0,"basic group applies");
  eq(a.getVolumeModifier("minecraft:entity.zombie_villager.hurt"),-1,"group boundary");
  overrides.put("minecraft:entity.zombie.hurt",setting(0.7f,false));
  eq(a.getVolumeModifier("minecraft:entity.zombie.hurt"),0.7f,"exact override precedes group");
  overrides.put("#global:step",setting(0,true));
  eq(a.getVolumeModifier("minecraft:block.stone.step"),0,"global step applies");
  overrides.put("#global:hostile_hurt",setting(0.3f,false));
  eq(a.getVolumeModifier("minecraft:entity.skeleton.hurt"),0.3f,"hostile group applies");
  overrides.put("#global:passive_ambient",setting(0.2f,false));
  eq(a.getVolumeModifier("minecraft:entity.cow.ambient"),0.2f,"passive group applies");
  overrides.put("mod:machine",setting(Float.NaN,false));eq(a.getVolumeModifier("mod:machine"),1,"nonfinite volume");
  overrides.put("mod:machine",setting(4,false));eq(a.getVolumeModifier("mod:machine"),2,"volume clamp");
  SoundConfig.getAnchors().add(a);
  var b=new SoundAnchor("B","minecraft:overworld",-10.5,64,0.25,8);
  b.getSoundOverrides().put("mod:machine",setting(0.4f,false));SoundConfig.getAnchors().add(b);
  eq(SoundConfig.getAnchorVolumeModifier("mod:machine","minecraft:overworld",-10.5,64,0.25),0.4f,"overlap quietest");
  Collections.reverse(SoundConfig.getAnchors());
  eq(SoundConfig.getAnchorVolumeModifier("mod:machine","minecraft:overworld",-10.5,64,0.25),0.4f,"overlap order independent");
  eq(SoundConfig.getAnchorVolumeModifier("mod:machine","minecraft:overworld",1000,0,0),-1,"outside all zones");
  SoundConfig.getAnchors().add(null);check(SoundConfig.getAnchors().size()==2,"null anchors removed");
  var sound=new TestSound();sound.x=-10.5;sound.y=64;sound.z=0.25;
  SoundConfig.profile=0.6f;
  eq(TestAudio.volume(sound),0.4f,"anchor override at source");
  SoundConfig.profile=0;eq(TestAudio.volume(sound),0,"profile mute always wins");
  SoundConfig.profile=0.6f;sound.relative=true;eq(TestAudio.volume(sound),0.6f,"UI relative sound bypass");sound.relative=false;
  sound.x=1000;eq(TestAudio.volume(sound),0.6f,"moving sound leaves zone");sound.x=-10.5;
  a.setEnabled(false);b.setEnabled(false);eq(TestAudio.volume(sound),0.6f,"disabling all zones restores profile");
  a.setEnabled(true);b.setEnabled(true);
  // Save the actual settings method, not the profile save path, then deserialize with real Gson.
  SoundConfig.saveSettings();
  String saved=Files.readString(SoundConfig.SETTINGS_FILE.toPath(),StandardCharsets.UTF_8);
  check(saved.contains("Тихий дім"),"UTF-8 persistence");
  SoundConfig.SETTINGS=SoundConfig.GSON.fromJson(saved,SoundConfig.AppSettings.class);
  check(SoundConfig.getAnchors().size()==2,"anchors survive reload");
  eq(TestAudio.volume(sound),0.4f,"overrides survive reload");
  check(SoundConfig.getAnchors().stream().anyMatch(z->z.getName().equals("Тихий дім 🎵")&&z.getBoxW()==3),"names and geometry survive reload");
  SoundConfig.getAnchors().clear();SoundConfig.saveSettings();
  SoundConfig.SETTINGS=SoundConfig.GSON.fromJson(Files.readString(SoundConfig.SETTINGS_FILE.toPath()),SoundConfig.AppSettings.class);
  check(SoundConfig.getAnchors().isEmpty(),"deletion survives reload");
  SoundConfig.SETTINGS=SoundConfig.GSON.fromJson("{\"anchors\":null}",SoundConfig.AppSettings.class);
  check(SoundConfig.getAnchors().isEmpty(),"legacy missing/null list");
  try(var files=Files.list(SoundConfig.SC_DIR.toPath())) { check(files.noneMatch(p->p.toString().endsWith(".tmp")),"no temp files left"); }
  // A and B have identical dimensions/coordinates, but independent lists and audio effects.
  AnchorWorldContext.key="singleplayer:world-A";
  a.getSoundOverrides().put("mod:machine",setting(0,true));
  SoundConfig.getAnchors().add(a);SoundConfig.saveSettings();
  eq(TestAudio.volume(sound),0,"world A muted");
  AnchorWorldContext.key="singleplayer:world-B";
  check(SoundConfig.getAnchors().isEmpty(),"world B begins empty");
  eq(TestAudio.volume(sound),0.6f,"world A does not mute world B");
  SoundConfig.getAnchors().add(b);SoundConfig.saveSettings();
  eq(TestAudio.volume(sound),0.4f,"world B own override");
  AnchorWorldContext.key="singleplayer:world-A";
  check(SoundConfig.getAnchors().size()==1&&SoundConfig.getAnchors().get(0)==a,"return to A");
  eq(TestAudio.volume(sound),0,"return to A restores its audio");
  SoundConfig.getAnchors().clear();SoundConfig.saveSettings();
  SoundConfig.SETTINGS=SoundConfig.GSON.fromJson(Files.readString(SoundConfig.SETTINGS_FILE.toPath()),SoundConfig.AppSettings.class);
  check(SoundConfig.getAnchors().isEmpty(),"A deletion survives reload");
  AnchorWorldContext.key="singleplayer:world-B";
  check(SoundConfig.getAnchors().size()==1,"A deletion preserves B after reload");
  eq(TestAudio.volume(sound),0.4f,"B persists independently");
  AnchorWorldContext.key="multiplayer:example.org";
  check(SoundConfig.getAnchors().isEmpty(),"server separate from saves");
  SoundConfig.getAnchors().add(a);
  AnchorWorldContext.key="multiplayer:other.org";
  check(SoundConfig.getAnchors().isEmpty(),"servers isolated");
  AnchorWorldContext.key=null;
  check(SoundConfig.getAnchors().isEmpty(),"title screen no active anchors");
  eq(TestAudio.volume(sound),0.6f,"unknown context never uses previous anchors");
  // Preserve unassigned old anchors until an explicit one-time import.
  SoundConfig.SETTINGS.anchors=new ArrayList<>();SoundConfig.SETTINGS.anchors.add(a);
  SoundConfig.importLegacyAnchors();check(SoundConfig.getLegacyAnchorCount()==1,"no import outside world");
  AnchorWorldContext.key="singleplayer:import-target";
  check(SoundConfig.getAnchors().isEmpty(),"legacy is not silently assigned");
  eq(TestAudio.volume(sound),0.6f,"legacy not applied before consent");
  SoundConfig.saveSettings();
  SoundConfig.SETTINGS=SoundConfig.GSON.fromJson(Files.readString(SoundConfig.SETTINGS_FILE.toPath()),SoundConfig.AppSettings.class);
  check(SoundConfig.getLegacyAnchorCount()==1,"unassigned legacy survives save");
  SoundConfig.importLegacyAnchors();SoundConfig.importLegacyAnchors();
  check(SoundConfig.getAnchors().size()==1&&SoundConfig.getLegacyAnchorCount()==0,"one-time import, no duplicates");
  SoundConfig.SETTINGS=SoundConfig.GSON.fromJson(Files.readString(SoundConfig.SETTINGS_FILE.toPath()),SoundConfig.AppSettings.class);
  eq(TestAudio.volume(sound),0,"import persists");
  AnchorWorldContext.key="singleplayer:unrelated";
  check(SoundConfig.getAnchors().isEmpty()&&SoundConfig.getLegacyAnchorCount()==0,"import does not leak to next world");
  SoundConfig.SETTINGS.anchorsByWorld=null;
  check(SoundConfig.getAnchors().isEmpty(),"null world map recovered");
  SoundConfig.SETTINGS.anchorsByWorld.put(AnchorWorldContext.key,null);
  check(SoundConfig.getAnchors().isEmpty(),"null per-world list recovered");
  System.out.println("PASS geometry, persistence, per-world lists/audio, server isolation, disconnect, explicit migration");
 }
}
'''
ports=sorted(p for p in ROOT.iterdir() if p.is_dir() and p.name.startswith(('fabric-','neoforge-')))
assert len(ports)==17
for port in ports:
 java=port/'src/main/java/soundcontrol'
 out=OUT/port.name;src=out/'src';src.mkdir(parents=True,exist_ok=True)
 anchor=next(java.rglob('SoundAnchor.java')).read_text(encoding='utf-8').replace('package soundcontrol.anchor;','package soundcontrol;')
 config=(java/'SoundConfig.java').read_text(encoding='utf-8')
 methods='\n'.join(method(config,sig) for sig in ['public static List<SoundAnchor> getAnchors()', 'public static int getLegacyAnchorCount()', 'public static void importLegacyAnchors()', 'public static String getSoundGroup(', 'public static float getAnchorSettingVolume(', 'public static float getAnchorVolumeModifier(', 'public static void saveSettings()'])
 hostile=re.search(r'private static final Set<String> HOSTILE_MOBS = Set.of\(.*?\);',config,re.S).group()
 head='''package soundcontrol;
import java.util.*;import java.io.*;import com.google.gson.*;
public class SoundConfig {
 static final File SC_DIR=new File(System.getProperty("test.dir"));
 static final File SETTINGS_FILE=new File(SC_DIR,"settings.json");
 static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 static final Logger LOGGER=new Logger();
 static class Logger { void error(String message,Exception e) { throw new AssertionError(message,e); } }
 static class AppSettings { public String activeProfile="default"; public List<SoundAnchor> anchors=new ArrayList<>(); public Map<String,List<SoundAnchor>> anchorsByWorld=new LinkedHashMap<>(); }
 static AppSettings SETTINGS=new AppSettings();
 public static class SoundSettings { public float volume=1; public boolean muted,favorite,overrideParent; }
 static float profile=1;
 static float getVolumeModifier(String id) { return profile; }
'''
 mixin=(java/'mixin/SoundEngineMixin.java').read_text(encoding='utf-8')
 audio=method(mixin,'private static float soundcontrol$anchorVolume(')
 audio=audio.replace('private static float soundcontrol$anchorVolume(SoundInstance sound)','static float volume(TestSound sound)')
 audio=audio.replace('net.minecraft.client.MinecraftClient','TestClient').replace('net.minecraft.client.Minecraft','TestClient')
 fixture='''package soundcontrol;
class AnchorWorldContext { static String key="singleplayer:test-A"; static String currentKey(){return key;} }
class TestSound {
 double x,y,z; boolean relative;
 String getId(){return "mod:machine";} String getLocation(){return getId();} String getIdentifier(){return getId();}
 double getX(){return x;}double getY(){return y;}double getZ(){return z;}boolean isRelative(){return relative;}
}
class TestClient {
 static final TestClient INSTANCE=new TestClient();static TestClient getInstance(){return INSTANCE;}
 TestWorld world=new TestWorld(),level=world;
 static class TestWorld { TestWorld getRegistryKey(){return this;}String getValue(){return "minecraft:overworld";}String dimension(){return getValue();} }
}
class TestAudio { AUDIO }
'''.replace('AUDIO',audio)
 for name,text in [('SoundAnchor.java',anchor),('SoundConfig.java',head+hostile+'\n'+methods+'\n}'),('TestAudio.java',fixture),('AnchorTests.java',TEST)]:
  (src/name).write_text(text,encoding='utf-8')
 # Static wiring checks catch regressions which headless geometry tests cannot see.
 assert mixin.count('soundcontrol$refreshAnchors(')==1
 assert 'at = @At("TAIL")' in mixin
 assert 'getAdjustedVolume(' in mixin or 'calculateVolume(' in mixin
 if port.name.endswith('1.20.1'):assert 'soundcontrol$initialAnchorVolume' in mixin
 for name in ['SoundAnchorScreen.java','SoundAnchorEditScreen.java','AllSoundsPickerScreen.java']:
  ui=next(java.rglob(name)).read_text(encoding='utf-8')
  assert 'SoundConfig.save();' not in ui,(port,name)
  assert 'SoundConfig.saveSettings();' in ui,(port,name)
 screen=next(java.rglob('SoundControlScreen.java')).read_text(encoding='utf-8')
 assert len(re.findall(r'new (?:soundcontrol\.(?:anchor\.)?)?SoundAnchorScreen\(this\)',screen))==1,(port,'duplicate/missing anchor button')
 for locale in (port/'src/main/resources/assets/soundcontrol/lang').glob('*.json'):
  lang=json.loads(locale.read_text(encoding='utf-8-sig'))
  for key in ['title','create','edit','browse','recent','clear','edit_title','search','all_mods','remove','import_legacy']:
   assert 'text.soundcontrol.anchors.'+key in lang,(port,locale,key)
 classes=out/'classes';classes.mkdir(exist_ok=True)
 subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',str(GSON),'-d',str(classes),*map(str,src.glob('*.java'))],check=True)
 print(port.name,flush=True)
 subprocess.run(['java','-Dtest.dir='+str(out/'config'),'-cp',str(classes)+os.pathsep+str(GSON),'soundcontrol.AnchorTests'],check=True)
