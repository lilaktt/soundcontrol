package soundcontrol.anchor;

import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

public class SoundAnchorRenderer {
    private static final int SPHERE_SEGMENTS = 16;
    private static final double[] SIN_TABLE = new double[SPHERE_SEGMENTS + 1];
    private static final double[] COS_TABLE = new double[SPHERE_SEGMENTS + 1];
    private static final int COLOR = 0x9955FFFF;

    static {
        for (int i = 0; i <= SPHERE_SEGMENTS; i++) {
            double angle = (2.0 * Math.PI * i) / SPHERE_SEGMENTS;
            SIN_TABLE[i] = Math.sin(angle);
            COS_TABLE[i] = Math.cos(angle);
        }
    }

    public static void render(DrawContext context) {
        if (!Compat.inWorld()) return;
        List<SoundAnchor> anchors = SoundConfig.getAnchors();
        if (anchors.isEmpty()) return;
        Compat.Frame frame = Compat.frame();
        if (frame == null) return;

        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();
        String playerDim = Compat.dimensionKey();

        for (SoundAnchor anchor : anchors) {
            if (!anchor.isEnabled() || !anchor.isShowRadius() || !anchor.isInDimension(playerDim)) continue;
            double dx = anchor.getX() - frame.camX();
            double dy = anchor.getY() - frame.camY();
            double dz = anchor.getZ() - frame.camZ();
            double maxRenderDist = Math.max(anchor.getMaxExtent() * 4.0, 128.0);
            if (dx * dx + dy * dy + dz * dz > maxRenderDist * maxRenderDist) continue;

            renderLabel(context, font, anchor, frame, sw, sh);
            if ("box".equals(anchor.getShapeMode())) renderBox(context, anchor, frame, sw, sh);
            else renderSphere(context, anchor, frame, sw, sh);
        }
    }

    static int[] projectPoint(double worldX, double worldY, double worldZ, Compat.Frame frame, int sw, int sh) {
        Vector4f pos = frame.viewProj().transform(new Vector4f(
                (float) (worldX - frame.camX()), (float) (worldY - frame.camY()), (float) (worldZ - frame.camZ()), 1.0f));
        if (!Float.isFinite(pos.w()) || pos.w() <= 0.05f) return null;
        float ndcX = pos.x() / pos.w();
        float ndcY = pos.y() / pos.w();
        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) return null;
        return new int[]{(int) ((ndcX + 1.0f) * 0.5f * sw), (int) ((1.0f - ndcY) * 0.5f * sh)};
    }

    private static void renderLabel(DrawContext context, TextRenderer font, SoundAnchor anchor, Compat.Frame frame, int sw, int sh) {
        int[] screen = projectPoint(anchor.getX(), anchor.getY() + 1.5, anchor.getZ(), frame, sw, sh);
        if (screen == null || screen[0] < 0 || screen[0] > sw || screen[1] < 0 || screen[1] > sh) return;
        String sizeInfo = "box".equals(anchor.getShapeMode())
                ? anchor.getBoxW() + "x" + anchor.getBoxH() + "x" + anchor.getBoxD()
                : "R:" + anchor.getRadius();
        String label = anchor.getName() + " [" + sizeInfo + "]";
        int tw = font.getWidth(label);
        context.fill(screen[0] - tw / 2 - 2, screen[1] - 2, screen[0] + tw / 2 + 2, screen[1] + font.fontHeight + 2, 0x80000000);
        context.drawText(font, label, screen[0] - tw / 2, screen[1], 0xFF55FFFF, true);
        int overrides = anchor.getSoundOverrides().size();
        if (overrides > 0) {
            String info = overrides + " sound(s)";
            context.drawText(font, info, screen[0] - font.getWidth(info) / 2, screen[1] + font.fontHeight + 3, 0xAAFFFF88, true);
        }
    }

    private static void renderSphere(DrawContext context, SoundAnchor anchor, Compat.Frame frame, int sw, int sh) {
        double cx = anchor.getX(), cy = anchor.getY(), cz = anchor.getZ();
        int r = anchor.getRadius();
        for (int plane = 0; plane < 3; plane++) {
            double prevX = 0, prevY = 0, prevZ = 0;
            for (int i = 0; i <= SPHERE_SEGMENTS; i++) {
                double cos = COS_TABLE[i], sin = SIN_TABLE[i];
                double wx, wy, wz;
                if (plane == 0) { wx = cx + cos * r; wy = cy; wz = cz + sin * r; }
                else if (plane == 1) { wx = cx + cos * r; wy = cy + sin * r; wz = cz; }
                else { wx = cx; wy = cy + cos * r; wz = cz + sin * r; }
                if (i > 0) drawProjectedLine(context, prevX, prevY, prevZ, wx, wy, wz, frame, sw, sh);
                prevX = wx; prevY = wy; prevZ = wz;
            }
        }
    }

    private static void renderBox(DrawContext context, SoundAnchor anchor, Compat.Frame frame, int sw, int sh) {
        double minX = anchor.minX(), maxX = anchor.maxX();
        double minY = anchor.minY(), maxY = anchor.maxY();
        double minZ = anchor.minZ(), maxZ = anchor.maxZ();
        double[][] corners = {
            {minX, minY, minZ}, {maxX, minY, minZ}, {maxX, minY, maxZ}, {minX, minY, maxZ},
            {minX, maxY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ},
        };
        int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        for (int[] edge : edges) {
            double[] a = corners[edge[0]], b = corners[edge[1]];
            drawProjectedLine(context, a[0], a[1], a[2], b[0], b[1], b[2], frame, sw, sh);
        }
    }

    private static void drawProjectedLine(DrawContext context, double x0, double y0, double z0,
                                          double x1, double y1, double z1, Compat.Frame frame, int sw, int sh) {
        Matrix4f projection = frame.viewProj();
        Vector4f a = projection.transform(new Vector4f((float) (x0 - frame.camX()), (float) (y0 - frame.camY()), (float) (z0 - frame.camZ()), 1));
        Vector4f b = projection.transform(new Vector4f((float) (x1 - frame.camX()), (float) (y1 - frame.camY()), (float) (z1 - frame.camZ()), 1));
        if (!Float.isFinite(a.x) || !Float.isFinite(a.y) || !Float.isFinite(a.w)
                || !Float.isFinite(b.x) || !Float.isFinite(b.y) || !Float.isFinite(b.w)) return;
        float near = 0.05f;
        if (a.w < near && b.w < near) return;
        if (a.w < near) a.lerp(b, (near - a.w) / (b.w - a.w));
        else if (b.w < near) b.lerp(a, (near - b.w) / (a.w - b.w));
        drawLine(context, (int) ((a.x / a.w + 1f) * 0.5f * sw), (int) ((1f - a.y / a.w) * 0.5f * sh),
                (int) ((b.x / b.w + 1f) * 0.5f * sw), (int) ((1f - b.y / b.w) * 0.5f * sh), sw, sh);
    }

    private static void drawLine(DrawContext context, int x0, int y0, int x1, int y1, int sw, int sh) {
        double xmin = -2, xmax = sw + 2, ymin = -2, ymax = sh + 2;
        double fx0 = x0, fy0 = y0, fx1 = x1, fy1 = y1;
        int outcode0 = outCode(fx0, fy0, xmin, xmax, ymin, ymax);
        int outcode1 = outCode(fx1, fy1, xmin, xmax, ymin, ymax);
        while ((outcode0 | outcode1) != 0) {
            if ((outcode0 & outcode1) != 0) return;
            int out = outcode0 != 0 ? outcode0 : outcode1;
            double x, y;
            if ((out & 8) != 0) { x = fx0 + (fx1 - fx0) * (ymax - fy0) / (fy1 - fy0); y = ymax; }
            else if ((out & 4) != 0) { x = fx0 + (fx1 - fx0) * (ymin - fy0) / (fy1 - fy0); y = ymin; }
            else if ((out & 2) != 0) { y = fy0 + (fy1 - fy0) * (xmax - fx0) / (fx1 - fx0); x = xmax; }
            else { y = fy0 + (fy1 - fy0) * (xmin - fx0) / (fx1 - fx0); x = xmin; }
            if (out == outcode0) { fx0 = x; fy0 = y; outcode0 = outCode(fx0, fy0, xmin, xmax, ymin, ymax); }
            else { fx1 = x; fy1 = y; outcode1 = outCode(fx1, fy1, xmin, xmax, ymin, ymax); }
        }
        int cx0 = (int) Math.round(fx0), cy0 = (int) Math.round(fy0);
        int cx1 = (int) Math.round(fx1), cy1 = (int) Math.round(fy1);
        if (cy0 == cy1) { context.fill(Math.min(cx0, cx1), cy0, Math.max(cx0, cx1) + 1, cy0 + 2, COLOR); return; }
        if (cx0 == cx1) { context.fill(cx0, Math.min(cy0, cy1), cx0 + 2, Math.max(cy0, cy1) + 1, COLOR); return; }
        int dx = Math.abs(cx1 - cx0), dy = Math.abs(cy1 - cy0);
        if (dx >= dy) {
            if (cx0 > cx1) { int t = cx0; cx0 = cx1; cx1 = t; t = cy0; cy0 = cy1; cy1 = t; }
            int sy = cy1 > cy0 ? 1 : -1, err = dx / 2, y = cy0, runStart = cx0;
            for (int x = cx0; x <= cx1; x++) {
                int prevY = y;
                err -= dy;
                if (err < 0) { y += sy; err += dx; }
                if (y != prevY || x == cx1) {
                    int runEnd = y != prevY ? x - 1 : x;
                    context.fill(runStart, prevY, runEnd + 2, prevY + 2, COLOR);
                    if (y != prevY) runStart = x;
                }
            }
        } else {
            if (cy0 > cy1) { int t = cy0; cy0 = cy1; cy1 = t; t = cx0; cx0 = cx1; cx1 = t; }
            int sx = cx1 > cx0 ? 1 : -1, err = dy / 2, x = cx0, runStart = cy0;
            for (int y = cy0; y <= cy1; y++) {
                int prevX = x;
                err -= dx;
                if (err < 0) { x += sx; err += dy; }
                if (x != prevX || y == cy1) {
                    int runEnd = x != prevX ? y - 1 : y;
                    context.fill(prevX, runStart, prevX + 2, runEnd + 2, COLOR);
                    if (x != prevX) runStart = y;
                }
            }
        }
    }

    private static int outCode(double x, double y, double xmin, double xmax, double ymin, double ymax) {
        int code = 0;
        if (x < xmin) code |= 1; else if (x > xmax) code |= 2;
        if (y < ymin) code |= 4; else if (y > ymax) code |= 8;
        return code;
    }
}
