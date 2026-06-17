package soundcontrol;

import java.util.HashMap;
import java.util.Map;


public class SoundAnchor {
    private String name;
    private String dimension;
    private double x, y, z;
    private int radius = 16;
    private boolean enabled = true;
    private boolean showRadius = true;

    
    private String shapeMode = "radius";
    private int boxW = 32; 
    private int boxH = 32; 
    private int boxD = 32; 

    private Map<String, SoundConfig.SoundSettings> soundOverrides = new HashMap<>();

    public SoundAnchor() {}

    public SoundAnchor(String name, String dimension, double x, double y, double z, int radius) {
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
    }

    
    public String getName() { return name; }
    public String getDimension() { return dimension; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public int getRadius() { return radius; }
    public boolean isEnabled() { return enabled; }
    public boolean isShowRadius() { return showRadius; }
    public String getShapeMode() { return shapeMode; }
    public int getBoxW() { return boxW; }
    public int getBoxH() { return boxH; }
    public int getBoxD() { return boxD; }
    public Map<String, SoundConfig.SoundSettings> getSoundOverrides() { return soundOverrides; }

    
    public void setName(String name) { this.name = name; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setZ(double z) { this.z = z; }
    public void setRadius(int radius) { this.radius = radius; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setShowRadius(boolean showRadius) { this.showRadius = showRadius; }
    public void setShapeMode(String shapeMode) { this.shapeMode = shapeMode; }
    public void setBoxW(int boxW) { this.boxW = boxW; }
    public void setBoxH(int boxH) { this.boxH = boxH; }
    public void setBoxD(int boxD) { this.boxD = boxD; }

    
    public boolean contains(String dimension, double px, double py, double pz) {
        if (!this.enabled) return false;
        if (!this.dimension.equals(dimension)) return false;

        if ("box".equals(shapeMode)) {
            double ax = Math.round(this.x);
            double ay = Math.round(this.y);
            double az = Math.round(this.z);
            double halfW = boxW / 2.0;
            double halfH = boxH / 2.0;
            double halfD = boxD / 2.0;
            
            double minX = Math.round(ax - halfW);
            double maxX = Math.round(ax + halfW);
            double minY = Math.round(ay - halfH);
            double maxY = Math.round(ay + halfH);
            double minZ = Math.round(az - halfD);
            double maxZ = Math.round(az + halfD);

            return px >= minX && px <= maxX
                && py >= minY && py <= maxY
                && pz >= minZ && pz <= maxZ;
        } else {
            double dx = px - this.x;
            double dy = py - this.y;
            double dz = pz - this.z;
            return (dx * dx + dy * dy + dz * dz) <= (radius * radius);
        }
    }

    
    public float getVolumeModifier(String soundId) {
        if (soundOverrides.containsKey(soundId)) {
            SoundConfig.SoundSettings s = soundOverrides.get(soundId);
            return s.muted ? 0.0f : s.volume;
        }
        return -1.0f;
    }

    
    public double getMaxExtent() {
        if ("box".equals(shapeMode)) {
            return Math.max(boxW, Math.max(boxH, boxD)) / 2.0;
        }
        return radius;
    }
}
