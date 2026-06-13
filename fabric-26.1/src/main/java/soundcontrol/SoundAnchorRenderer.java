package soundcontrol;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

public class SoundAnchorRenderer {

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

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();
        Matrix4f projMatrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
        Font font = client.font;

        int sw = context.guiWidth();
        int sh = context.guiHeight();
        String playerDim = getPlayerDimension();

        for (SoundAnchor anchor : anchors) {
            if (!anchor.isEnabled() || !anchor.getDimension().equals(playerDim)) continue;
            if (!anchor.isShowRadius()) continue;

            double dx = anchor.getX() - camPos.x;
            double dy = anchor.getY() - camPos.y;
            double dz = anchor.getZ() - camPos.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            double maxRenderDist = Math.max(anchor.getMaxExtent() * 4.0, 128.0);
            if (dist > maxRenderDist) continue;

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

        if (pos.w() <= 0.05f) return null;

        float ndcX = pos.x() / pos.w();
        float ndcY = pos.y() / pos.w();

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
        int segments = 24;
        renderRing(context, anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getRadius(), camPos, projMatrix, sw, sh, segments, color, 0);
        renderRing(context, anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getRadius(), camPos, projMatrix, sw, sh, segments, color, 1);
        renderRing(context, anchor.getX(), anchor.getY(), anchor.getZ(), anchor.getRadius(), camPos, projMatrix, sw, sh, segments, color, 2);
    }

    private static void renderRing(GuiGraphicsExtractor context, double cx, double cy, double cz, int radius,
                                    Vec3 camPos, Matrix4f projMatrix, int sw, int sh,
                                    int segments, int color, int plane) {
        double[] prevW = null;

        for (int i = 0; i <= segments; i++) {
            double angle = (2.0 * Math.PI * i) / segments;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double wx, wy, wz;
            if (plane == 0) {
                wx = cx + cos * radius;
                wy = cy;
                wz = cz + sin * radius;
            } else if (plane == 1) {
                wx = cx + cos * radius;
                wy = cy + sin * radius;
                wz = cz;
            } else {
                wx = cx;
                wy = cy + cos * radius;
                wz = cz + sin * radius;
            }

            double[] curW = new double[]{wx, wy, wz};

            if (prevW != null) {
                drawClippedLine(context, camPos, projMatrix, prevW[0], prevW[1], prevW[2], curW[0], curW[1], curW[2], color, sw, sh);
            }
            prevW = curW;
        }
    }

    private static void renderBox(GuiGraphicsExtractor context, SoundAnchor anchor,
                                   Vec3 camPos, Matrix4f projMatrix, int sw, int sh) {
        int color = 0x9955FFFF;
        double ax = Math.round(anchor.getX());
        double ay = Math.round(anchor.getY());
        double az = Math.round(anchor.getZ());
        double halfW = anchor.getBoxW() / 2.0;
        double halfH = anchor.getBoxH() / 2.0;
        double halfD = anchor.getBoxD() / 2.0;

        double minX = Math.round(ax - halfW);
        double maxX = Math.round(ax + halfW);
        double minY = Math.round(ay - halfH);
        double maxY = Math.round(ay + halfH);
        double minZ = Math.round(az - halfD);
        double maxZ = Math.round(az + halfD);

        double[][] worldCorners = {
            {minX, minY, minZ},
            {maxX, minY, minZ},
            {maxX, minY, maxZ},
            {minX, minY, maxZ},
            {minX, maxY, minZ},
            {maxX, maxY, minZ},
            {maxX, maxY, maxZ},
            {minX, maxY, maxZ},
        };

        int[][] edges = {
            {0,1},{1,2},{2,3},{3,0},
            {4,5},{5,6},{6,7},{7,4},
            {0,4},{1,5},{2,6},{3,7},
        };

        for (int[] edge : edges) {
            double[] p1 = worldCorners[edge[0]];
            double[] p2 = worldCorners[edge[1]];
            drawClippedLine(context, camPos, projMatrix, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], color, sw, sh);
        }
    }

    private static void drawClippedLine(GuiGraphicsExtractor context, Vec3 camPos, Matrix4f projMatrix,
                                        double x1, double y1, double z1,
                                        double x2, double y2, double z2,
                                        int color, int sw, int sh) {
        float dx1 = (float)(x1 - camPos.x);
        float dy1 = (float)(y1 - camPos.y);
        float dz1 = (float)(z1 - camPos.z);
        Vector4f p1 = new Vector4f(dx1, dy1, dz1, 1.0f);
        projMatrix.transform(p1);

        float dx2 = (float)(x2 - camPos.x);
        float dy2 = (float)(y2 - camPos.y);
        float dz2 = (float)(z2 - camPos.z);
        Vector4f p2 = new Vector4f(dx2, dy2, dz2, 1.0f);
        projMatrix.transform(p2);

        float w1 = p1.w();
        float w2 = p2.w();

        if (w1 <= 0.05f && w2 <= 0.05f) return;

        if (w1 <= 0.05f) {
            float t = (p2.w() - 0.05f) / (p2.w() - w1);
            p1.lerp(p2, 1.0f - t);
        } else if (w2 <= 0.05f) {
            float t = (p1.w() - 0.05f) / (p1.w() - w2);
            p2.lerp(p1, 1.0f - t);
        }

        float ndcX1 = p1.x() / p1.w();
        float ndcY1 = p1.y() / p1.w();
        float ndcX2 = p2.x() / p2.w();
        float ndcY2 = p2.y() / p2.w();

        int sX1 = (int) ((ndcX1 + 1.0f) * 0.5f * sw);
        int sY1 = (int) ((1.0f - ndcY1) * 0.5f * sh);
        int sX2 = (int) ((ndcX2 + 1.0f) * 0.5f * sw);
        int sY2 = (int) ((1.0f - ndcY2) * 0.5f * sh);

        drawLine(context, sX1, sY1, sX2, sY2, color, sw, sh);
    }

    private static final int INSIDE = 0;
    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static final int BOTTOM = 4;
    private static final int TOP = 8;

    private static int computeOutCode(double x, double y, double xmin, double xmax, double ymin, double ymax) {
        int code = INSIDE;
        if (x < xmin) code |= LEFT;
        else if (x > xmax) code |= RIGHT;
        if (y < ymin) code |= BOTTOM;
        else if (y > ymax) code |= TOP;
        return code;
    }

    private static void drawLine(GuiGraphicsExtractor context, int sX1, int sY1, int sX2, int sY2, int color, int sw, int sh) {
        double xmin = -5, xmax = sw + 5, ymin = -5, ymax = sh + 5;
        double x0 = sX1, y0 = sY1, x1 = sX2, y1 = sY2;
        int outcode0 = computeOutCode(x0, y0, xmin, xmax, ymin, ymax);
        int outcode1 = computeOutCode(x1, y1, xmin, xmax, ymin, ymax);
        boolean accept = false;

        while (true) {
            if ((outcode0 | outcode1) == 0) {
                accept = true;
                break;
            } else if ((outcode0 & outcode1) != 0) {
                break;
            } else {
                double x = 0, y = 0;
                int outcodeOut = (outcode0 != 0) ? outcode0 : outcode1;
                
                if ((outcodeOut & TOP) != 0) {
                    x = x0 + (x1 - x0) * (ymax - y0) / (y1 - y0);
                    y = ymax;
                } else if ((outcodeOut & BOTTOM) != 0) {
                    x = x0 + (x1 - x0) * (ymin - y0) / (y1 - y0);
                    y = ymin;
                } else if ((outcodeOut & RIGHT) != 0) {
                    y = y0 + (y1 - y0) * (xmax - x0) / (x1 - x0);
                    x = xmax;
                } else if ((outcodeOut & LEFT) != 0) {
                    y = y0 + (y1 - y0) * (xmin - x0) / (x1 - x0);
                    x = xmin;
                }

                if (outcodeOut == outcode0) {
                    x0 = x; y0 = y;
                    outcode0 = computeOutCode(x0, y0, xmin, xmax, ymin, ymax);
                } else {
                    x1 = x; y1 = y;
                    outcode1 = computeOutCode(x1, y1, xmin, xmax, ymin, ymax);
                }
            }
        }

        if (accept) {
            int finalX1 = (int) Math.round(x0);
            int finalY1 = (int) Math.round(y0);
            int finalX2 = (int) Math.round(x1);
            int finalY2 = (int) Math.round(y1);

            int dx = Math.abs(finalX2 - finalX1);
            int dy = Math.abs(finalY2 - finalY1);
            int steps = Math.max(dx, dy);

            if (steps == 0) {
                context.fill(finalX1, finalY1, finalX1 + 2, finalY1 + 2, color);
                return;
            }

            int stepSize = Math.max(1, steps / 80);
            for (int i = 0; i <= steps; i += stepSize) {
                int px = finalX1 + (finalX2 - finalX1) * i / steps;
                int py = finalY1 + (finalY2 - finalY1) * i / steps;
                context.fill(px, py, px + 2, py + 2, color);
            }
        }
    }
}
