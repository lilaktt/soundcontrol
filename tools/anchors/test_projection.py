"""Execute actual renderer math from all 17 ports with real JOML and a recording GUI fixture."""
from pathlib import Path
import subprocess,os,re
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/anchor-projection-tests'
JOML=next((Path.home()/'.gradle/caches/modules-2/files-2.1/org.joml/joml/1.10.5').glob('*/joml-1.10.5.jar'))

# Find definitions, not earlier calls.
def extract(s,name):
 match=re.search(r'    private static [^\n]+\b'+name+r'\(',s)
 a=match.start();b=s.index('{',a);depth=1
 for i in range(b+1,len(s)):
  depth+=(s[i]=='{')-(s[i]=='}')
  if depth==0:return s[a:i+1]
 raise AssertionError(name)

HARNESS='''import org.joml.Matrix4f;import org.joml.Vector4f;
import java.util.ArrayList;import java.util.List;import java.util.Random;
public class ProjectionTest {
 static class Vec { double x,y,z; }
 static class Gui {
  List<int[]> fills=new ArrayList<>();
  void fill(int x0,int y0,int x1,int y1,int color) {
   if (x0 < -4 || y0 < -4 || x1 > 326 || y1 > 246 || x1 < x0 || y1 < y0)
    throw new AssertionError("Unbounded/invalid line fill: "+java.util.Arrays.toString(new int[]{x0,y0,x1,y1}));
   fills.add(new int[]{x0,y0,x1,y1});
  }
 }
 METHODS
 static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
 public static void main(String[] args) {
  Matrix4f projection=new Matrix4f().perspective((float)Math.toRadians(70),320f/240f,0.05f,4096f);
  Vec camera=new Vec();Gui gui=new Gui();
  int[] center=projectPoint(0,0,-3,camera,projection,320,240);
  check(center[0]==160 && center[1]==120,"center projection");
  check(projectPoint(0,0,3,camera,projection,320,240)==null,"behind camera");
  check(projectPoint(Double.NaN,0,-3,camera,projection,320,240)==null,"nonfinite point");
  drawProjectedLine(gui,-1,0,-3,1,0,1,camera,projection,0,320,240);
  check(!gui.fills.isEmpty(),"edge crossing camera plane remains visible");
  gui.fills.clear();drawProjectedLine(gui,0,0,3,1,0,4,camera,projection,0,320,240);
  check(gui.fills.isEmpty(),"behind camera edge rejected");
  drawProjectedLine(gui,Double.NaN,0,-3,1,0,-4,camera,projection,0,320,240);
  check(gui.fills.isEmpty(),"nonfinite edge rejected");
  Random random=new Random(718);
  for(int i=0;i<10000;i++) {
   gui.fills.clear();
   drawLineEfficient(gui,random.nextInt(200000)-100000,random.nextInt(200000)-100000,
    random.nextInt(200000)-100000,random.nextInt(200000)-100000,0,320,240);
   check(gui.fills.size()<=565,"clipped rasterization has bounded work");
  }
  gui.fills.clear();drawLineEfficient(gui,Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,0,320,240);
  System.out.println("PASS projection, behind-camera clipping, nonfinite input, 10000 bounded screen clips");
 }
}
'''
paths=sorted(ROOT.glob('*/src/main/java/soundcontrol/**/SoundAnchorRenderer.java'));assert len(paths)==17
canonical=None
for p in paths:
 s=p.read_text(encoding='utf-8')
 methods='\n'.join(extract(s,n) for n in ['projectPoint','drawProjectedLine','drawLineEfficient','outCode'])
 for old in ['GuiGraphicsExtractor','GuiGraphics','DrawContext']:methods=methods.replace(old,'Gui')
 methods=re.sub(r'\bVec3d?\b','Vec',methods)
 if canonical is None:canonical=methods
 assert canonical==methods,p
 out=OUT/p.relative_to(ROOT).parts[0];out.mkdir(parents=True,exist_ok=True)
 java=out/'ProjectionTest.java';java.write_text(HARNESS.replace('METHODS',methods),encoding='utf-8')
 subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',str(JOML),'-d',str(out),str(java)],check=True)
 print(p.relative_to(ROOT).parts[0],flush=True)
 subprocess.run(['java','-cp',str(out)+os.pathsep+str(JOML),'ProjectionTest'],check=True,timeout=15)
