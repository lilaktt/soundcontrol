package soundcontrol;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RadarPositionScreen extends Screen {
    private final Screen parent;
    private int radarX;
    private int radarY;
    private boolean dragging = false;
    private double dragStartX;
    private double dragStartY;

    public RadarPositionScreen(Screen parent) {
        super(Component.translatable("text.soundcontrol.radar_position.title"));
        this.parent = parent;
        this.radarX = SoundConfig.getRadarX();
        this.radarY = SoundConfig.getRadarY();
        if (this.radarY == -1) {
            this.radarY = 100;
        }
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.translatable("gui.save"), button -> {
            SoundConfig.setRadarPos(this.radarX, this.radarY);
            SoundConfig.save();
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 105, this.height - 40, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 + 5, this.height - 40, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        boolean isLeftMouseDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(this.minecraft.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        
        if (isLeftMouseDown) {
            if (!this.dragging) {
                if (mouseX >= this.radarX - 5 && mouseX <= this.radarX + 115 && mouseY >= this.radarY - 5 && mouseY <= this.radarY + 35) {
                    this.dragging = true;
                    this.dragStartX = mouseX - this.radarX;
                    this.dragStartY = mouseY - this.radarY;
                }
            } else {
                this.radarX = (int) (mouseX - this.dragStartX);
                this.radarY = (int) (mouseY - this.dragStartY);
                this.radarX = max(0, min(this.radarX, this.width - 100));
                this.radarY = max(0, min(this.radarY, this.height - 50));
            }
        } else {
            this.dragging = false;
        }

        context.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        context.drawCenteredString(this.font, Component.translatable("text.soundcontrol.radar_position.help"), this.width / 2, 35, 0xFFAAAAAA);

        int previewX = this.radarX;
        int previewY = this.radarY;

        context.drawString(this.font, "» [Preview Sound 1]", previewX, previewY, 0xFFFFFFFF, true);
        context.drawString(this.font, "» [Preview Sound 2]", previewX, previewY + 10, 0xFFFFFFFF, true);
        context.drawString(this.font, "» [Preview Sound 3]", previewX, previewY + 20, 0xFFFFFFFF, true);
        
        context.fill(previewX - 2, previewY - 2, previewX + 118, previewY - 1, 0x88FFFFFF); // Top
        context.fill(previewX - 2, previewY + 33, previewX + 118, previewY + 34, 0x88FFFFFF); // Bottom
        context.fill(previewX - 2, previewY - 1, previewX - 1, previewY + 33, 0x88FFFFFF); // Left
        context.fill(previewX + 117, previewY - 1, previewX + 118, previewY + 33, 0x88FFFFFF); // Right
    }

    private int min(int a, int b) { return a < b ? a : b; }
    private int max(int a, int b) { return a > b ? a : b; }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
