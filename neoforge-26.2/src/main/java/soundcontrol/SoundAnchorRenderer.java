package soundcontrol;

import soundcontrol.SoundConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

public class SoundAnchorRenderer {

    private static final int SPHERE_SEGMENTS = 16;
    private static final double[] SIN_TABLE = new double[SPHERE_SEGMENTS + 1];
    private static final double[] COS_TABLE = new double[SPHERE_SEGMENTS + 1];

    static {
        for (int i = 0; i <= SPHERE_SEGMENTS; i++) {
            double angle = (2.0 * Math.PI * i) / SPHERE_SEGMENTS;
            SIN_TABLE[i] = Math.sin(angle);
            COS_TABLE[i] = Math.cos(angle);
        }
    }

    private static String getPlayerDimension() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return "";
        return client.player.level().dimension().toString();
    }

    public static void render(GuiGraphicsExtractor context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameRenderer == null) return;

        List<SoundAnchor> anchors = SoundConfig.getAnchors();
        if (anchors.isEmpty()) return;

        Camera camera = client.gameRenderer.mainCamera();
    Vec3 camPos = camera.position();
    Matrix4f projMatrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
        Font font = client.font;

        int sw = context.guiWidth();
        int sh = context.guiHeight();
        String playerDim = getPlayerDimension();

        for (SoundAnchor anchor : anchors) {
            if (!anchor.isEnabled() || !anchor.isInDimension(playerDim)) continue;
            if (!anchor.isShowRadius()) continue;

            double dx = anchor.getX() - camPos.x;
            double dy = anchor.getY() - camPos.y;
            double dz = anchor.getZ() - camPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            double maxRenderDist = Math.max(anchor.getMaxExtent() * 4.0, 128.0);
            if (distSq > maxRenderDist * maxRenderDist) continue;

            renderLabel(context, font, anchor, camPos, projMatrix, sw, sh);

            if ("box".equals(anchor.getShapeMode())) {
                renderBox(context, anchor, camPos, projMatrix, sw, sh);
            } else {
                renderSphere(context, anchor, camPos, projMatrix, sw, sh);
            }
        }
    }

    private static int[] projectPoint(double worldX, double worldY, double worldZ,
                                       Vec3 camPos, Matrix4f projMatrix, int sw, int sh) {
        float dx = (float)(worldX - camPos.x);
        float dy = (float)(worldY - camPos.y);
        float dz = (float)(worldZ - camPos.z);

        Vector4f pos = new Vector4f(dx, dy, dz, 1.0f);
        projMatrix.transform(pos);

        if (!Float.isFinite(pos.w()) || pos.w() <= 0.05f) return null;

        float ndcX = pos.x() / pos.w();
        float ndcY = pos.y() / pos.w();

        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) return null;
        int screenX = (int) ((ndcX + 1.0f) * 0.5f * sw);
        int screenY = (int) ((1.0f - ndcY) * 0.5f * sh);

        return new int[]{screenX, screenY};
    }

    private static void renderLabel(GuiGraphicsExtractor context, Font font, SoundAnchor anchor,
                                     Vec3 camPos, Matrix4f projMatrix, int sw, int sh) {
        int[] screen = projectPoint(anchor.getX(), anchor.getY() + 1.5, anchor.getZ(), camPos, projMatrix, sw, sh);
        if (screen == null) return;
        if (screen[0] < 0 || screen[0] > sw || screen[1] < 0 || screen[1] > sh) return;

        String sizeInfo;
        if ("box".equals(anchor.getShapeMode())) {
            sizeInfo = anchor.getBoxW() + "x" + anchor.getBoxH() + "x" + anchor.getBoxD();
        } else {
            sizeInfo = "R:" + anchor.getRadius();
        }
        String label = anchor.getName() + " [" + sizeInfo + "]";
        int tw = font.width(label);
        context.fill(screen[0] - tw / 2 - 2, screen[1] - 2, screen[0] + tw / 2 + 2, screen[1] + font.lineHeight + 2, 0x80000000);
        context.text(font, label, screen[0] - tw / 2, screen[1], 0xFF55FFFF, true);

        int overrides = anchor.getSoundOverrides().size();
        if (overrides > 0) {
            String info = overrides + " sound(s)";
            int iw = font.width(info);
            context.text(font, info, screen[0] - iw / 2, screen[1] + font.lineHeight + 3, 0xAAFFFF88, false);
        }
    }

    private static void renderSphere(GuiGraphicsExtractor context, SoundAnchor anchor,
                                      Vec3 camPos, Matrix4f projMatrix, int sw, int sh) {
        int color = 0x9955FFFF;
        double cx = anchor.getX(), cy = anchor.getY(), cz = anchor.getZ();
        int r = anchor.getRadius();

        for (int plane = 0; plane < 3; plane++) {
            double prevX = 0, prevY = 0, prevZ = 0;

            for (int i = 0; i <= SPHERE_SEGMENTS; i++) {
                double cos = COS_TABLE[i];
                double sin = SIN_TABLE[i];

                double wx, wy, wz;
                if (plane == 0) {
                    wx = cx + cos * r; wy = cy; wz = cz + sin * r;
                } else if (plane == 1) {
                    wx = cx + cos * r; wy = cy + sin * r; wz = cz;
                } else {
                    wx = cx; wy = cy + cos * r; wz = cz + sin * r;
                }

                if (i > 0) drawProjectedLine(context, prevX, prevY, prevZ, wx, wy, wz, camPos, projMatrix, color, sw, sh);
                prevX = wx; prevY = wy; prevZ = wz;
            }
        }
    }

    private static void renderBox(GuiGraphicsExtractor context, SoundAnchor anchor,
                                   Vec3 camPos, Matrix4f projMatrix, int sw, int sh) {
        int color = 0x9955FFFF;
        double minX = anchor.minX(), maxX = anchor.maxX();
        double minY = anchor.minY(), maxY = anchor.maxY();
        double minZ = anchor.minZ(), maxZ = anchor.maxZ();

        double[][] worldCorners = {
            {minX, minY, minZ}, {maxX, minY, minZ}, {maxX, minY, maxZ}, {minX, minY, maxZ},
            {minX, maxY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ},
        };

        int[][] edges = {
            {0,1},{1,2},{2,3},{3,0},
            {4,5},{5,6},{6,7},{7,4},
            {0,4},{1,5},{2,6},{3,7},
        };

        for (int[] edge : edges) {
            double[] first = worldCorners[edge[0]], second = worldCorners[edge[1]];
            drawProjectedLine(context, first[0], first[1], first[2], second[0], second[1], second[2],
                    camPos, projMatrix, color, sw, sh);
        }
    }

    /** Clip a crossing edge at the camera plane instead of dropping the whole edge. */
    private static void drawProjectedLine(GuiGraphicsExtractor context,
            double x0, double y0, double z0, double x1, double y1, double z1,
            Vec3 camera, Matrix4f projection, int color, int sw, int sh) {
        Vector4f a = projection.transform(new Vector4f((float)(x0 - camera.x), (float)(y0 - camera.y), (float)(z0 - camera.z), 1));
        Vector4f b = projection.transform(new Vector4f((float)(x1 - camera.x), (float)(y1 - camera.y), (float)(z1 - camera.z), 1));
        if (!Float.isFinite(a.x) || !Float.isFinite(a.y) || !Float.isFinite(a.w)
                || !Float.isFinite(b.x) || !Float.isFinite(b.y) || !Float.isFinite(b.w)) return;
        float near = 0.05f;
        if (a.w < near && b.w < near) return;
        if (a.w < near) a.lerp(b, (near - a.w) / (b.w - a.w));
        else if (b.w < near) b.lerp(a, (near - b.w) / (a.w - b.w));
        int sx0 = (int)((a.x / a.w + 1f) * 0.5f * sw);
        int sy0 = (int)((1f - a.y / a.w) * 0.5f * sh);
        int sx1 = (int)((b.x / b.w + 1f) * 0.5f * sw);
        int sy1 = (int)((1f - b.y / b.w) * 0.5f * sh);
        drawLineEfficient(context, sx0, sy0, sx1, sy1, color, sw, sh);
    }

    private static void drawLineEfficient(GuiGraphicsExtractor context,
                                           int x0, int y0, int x1, int y1,
                                           int color, int sw, int sh) {
        // Cohen-Sutherland clip to screen bounds
        double xmin = -2, xmax = sw + 2, ymin = -2, ymax = sh + 2;
        double fx0 = x0, fy0 = y0, fx1 = x1, fy1 = y1;

        int outcode0 = outCode(fx0, fy0, xmin, xmax, ymin, ymax);
        int outcode1 = outCode(fx1, fy1, xmin, xmax, ymin, ymax);

        while (true) {
            if ((outcode0 | outcode1) == 0) break;
            if ((outcode0 & outcode1) != 0) return;

            int out = (outcode0 != 0) ? outcode0 : outcode1;
            double x = 0, y = 0;

            if ((out & 8) != 0) { x = fx0 + (fx1 - fx0) * (ymax - fy0) / (fy1 - fy0); y = ymax; }
            else if ((out & 4) != 0) { x = fx0 + (fx1 - fx0) * (ymin - fy0) / (fy1 - fy0); y = ymin; }
            else if ((out & 2) != 0) { y = fy0 + (fy1 - fy0) * (xmax - fx0) / (fx1 - fx0); x = xmax; }
            else if ((out & 1) != 0) { y = fy0 + (fy1 - fy0) * (xmin - fx0) / (fx1 - fx0); x = xmin; }

            if (out == outcode0) { fx0 = x; fy0 = y; outcode0 = outCode(fx0, fy0, xmin, xmax, ymin, ymax); }
            else { fx1 = x; fy1 = y; outcode1 = outCode(fx1, fy1, xmin, xmax, ymin, ymax); }
        }

        int cx0 = (int) Math.round(fx0), cy0 = (int) Math.round(fy0);
        int cx1 = (int) Math.round(fx1), cy1 = (int) Math.round(fy1);

        // Horizontal line — single fill
        if (cy0 == cy1) {
            int lo = Math.min(cx0, cx1), hi = Math.max(cx0, cx1);
            context.fill(lo, cy0, hi + 1, cy0 + 2, color);
            return;
        }

        // Vertical line — single fill
        if (cx0 == cx1) {
            int lo = Math.min(cy0, cy1), hi = Math.max(cy0, cy1);
            context.fill(cx0, lo, cx0 + 2, hi + 1, color);
            return;
        }

        // Diagonal: use Bresenham but batch into horizontal runs
        int dx = Math.abs(cx1 - cx0);
        int dy = Math.abs(cy1 - cy0);

        if (dx >= dy) {
            // Mostly horizontal — batch horizontal spans per row
            if (cx0 > cx1) { int t = cx0; cx0 = cx1; cx1 = t; t = cy0; cy0 = cy1; cy1 = t; }
            int sy = (cy1 > cy0) ? 1 : -1;
            int err = dx / 2;
            int y = cy0;
            int runStart = cx0;

            for (int x = cx0; x <= cx1; x++) {
                int prevY = y;
                err -= dy;
                if (err < 0) { y += sy; err += dx; }
                if (y != prevY || x == cx1) {
                    int runEnd = (y != prevY) ? x - 1 : x;
                    context.fill(runStart, prevY, runEnd + 2, prevY + 2, color);
                    runStart = (y != prevY) ? x : runStart;
                }
            }
        } else {
            // Mostly vertical — batch vertical spans per column
            if (cy0 > cy1) { int t = cy0; cy0 = cy1; cy1 = t; t = cx0; cx0 = cx1; cx1 = t; }
            int sx = (cx1 > cx0) ? 1 : -1;
            int err = dy / 2;
            int x = cx0;
            int runStart = cy0;

            for (int y = cy0; y <= cy1; y++) {
                int prevX = x;
                err -= dx;
                if (err < 0) { x += sx; err += dy; }
                if (x != prevX || y == cy1) {
                    int runEnd = (x != prevX) ? y - 1 : y;
                    context.fill(prevX, runStart, prevX + 2, runEnd + 2, color);
                    runStart = (x != prevX) ? y : runStart;
                }
            }
        }
    }

    private static int outCode(double x, double y, double xmin, double xmax, double ymin, double ymax) {
        int code = 0;
        if (x < xmin) code |= 1;
        else if (x > xmax) code |= 2;
        if (y < ymin) code |= 4;
        else if (y > ymax) code |= 8;
        return code;
    }
}
