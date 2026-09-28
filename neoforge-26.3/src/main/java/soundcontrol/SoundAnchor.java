package soundcontrol;

import soundcontrol.SoundConfig;
import java.util.LinkedHashMap;
import java.util.Map;

/** Same JSON schema and geometry on every loader/version. */
public class SoundAnchor {
    private String name = "Anchor";
    private String dimension;
    private double x, y, z;
    private int radius = 16;
    private boolean enabled = true;
    private boolean showRadius = true;
    private String shapeMode = "radius";
    private int boxW = 32, boxH = 32, boxD = 32;
    private Map<String, SoundConfig.SoundSettings> soundOverrides = new LinkedHashMap<>();

    public SoundAnchor() {}
    public SoundAnchor(String name, String dimension, double x, double y, double z, int radius) {
        setName(name); setDimension(dimension);
        setX(x); setY(y); setZ(z); setRadius(radius);
    }

    private static int size(int value) { return Math.max(1, Math.min(999, value)); }
    private static double coordinate(double value) { return Double.isFinite(value) ? value : 0; }
    public String getName() { return name == null ? "Anchor" : name; }
    public String getDimension() { return normalizeDimension(dimension); }
    public double getX() { return coordinate(x); }
    public double getY() { return coordinate(y); }
    public double getZ() { return coordinate(z); }
    public int getRadius() { return size(radius); }
    public boolean isEnabled() { return enabled; }
    public boolean isShowRadius() { return showRadius; }
    public String getShapeMode() { return "box".equals(shapeMode) ? "box" : "radius"; }
    public int getBoxW() { return size(boxW); }
    public int getBoxH() { return size(boxH); }
    public int getBoxD() { return size(boxD); }
    public Map<String, SoundConfig.SoundSettings> getSoundOverrides() {
        if (soundOverrides == null) soundOverrides = new LinkedHashMap<>();
        soundOverrides.entrySet().removeIf(e -> e.getKey() == null || e.getKey().isBlank() || e.getValue() == null);
        return soundOverrides;
    }

    public void setName(String value) { name = value == null ? "Anchor" : value; }
    public void setDimension(String value) { dimension = normalizeDimension(value); }
    public void setX(double value) { x = coordinate(value); }
    public void setY(double value) { y = coordinate(value); }
    public void setZ(double value) { z = coordinate(value); }
    public void setRadius(int value) { radius = size(value); }
    public void setEnabled(boolean value) { enabled = value; }
    public void setShowRadius(boolean value) { showRadius = value; }
    public void setShapeMode(String value) { shapeMode = "box".equals(value) ? "box" : "radius"; }
    public void setBoxW(int value) { boxW = size(value); }
    public void setBoxH(int value) { boxH = size(value); }
    public void setBoxD(int value) { boxD = size(value); }

    /** Accept old 26.x ResourceKey strings as well as portable dimension IDs. */
    public static String normalizeDimension(String value) {
        if (value == null) return "";
        if (value.startsWith("ResourceKey[") && value.endsWith("]")) {
            int separator = value.indexOf(" / ");
            if (separator >= 0) return value.substring(separator + 3, value.length() - 1);
        }
        return value;
    }

    public boolean isInDimension(String value) {
        return !getDimension().isEmpty() && getDimension().equals(normalizeDimension(value));
    }

    public double minX() { return getX() - getBoxW() / 2.0; }
    public double maxX() { return getX() + getBoxW() / 2.0; }
    public double minY() { return getY() - getBoxH() / 2.0; }
    public double maxY() { return getY() + getBoxH() / 2.0; }
    public double minZ() { return getZ() - getBoxD() / 2.0; }
    public double maxZ() { return getZ() + getBoxD() / 2.0; }

    public boolean contains(String dimension, double px, double py, double pz) {
        if (!enabled || !isInDimension(dimension)) return false;
        if (!Double.isFinite(px) || !Double.isFinite(py) || !Double.isFinite(pz)
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        if ("box".equals(getShapeMode())) {
            return px >= minX() && px <= maxX() && py >= minY() && py <= maxY()
                    && pz >= minZ() && pz <= maxZ();
        }
        double dx = px - getX(), dy = py - getY(), dz = pz - getZ();
        return dx * dx + dy * dy + dz * dz <= (double) getRadius() * getRadius();
    }

    public float getVolumeModifier(String soundId) {
        return SoundConfig.getAnchorSettingVolume(getSoundOverrides(), soundId);
    }

    public double getMaxExtent() {
        return "box".equals(getShapeMode())
                ? Math.max(getBoxW(), Math.max(getBoxH(), getBoxD())) / 2.0 : getRadius();
    }
}
