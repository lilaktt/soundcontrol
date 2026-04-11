package soundcontrol;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
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
            this.radarY = 100; // Default for dragging
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
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        context.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        context.centeredText(this.font, Component.translatable("text.soundcontrol.radar_position.help"), this.width / 2, 35, 0xFFAAAAAA);

        // Preview radar
        int previewX = this.radarX;
        int previewY = this.radarY;

        context.text(this.font, "» [Preview Sound 1]", previewX, previewY, 0xFFFFFFFF, true);
        context.text(this.font, "» [Preview Sound 2]", previewX, previewY + 10, 0xFFFFFFFF, true);
        context.text(this.font, "» [Preview Sound 3]", previewX, previewY + 20, 0xFFFFFFFF, true);
        
        // Draw frame around preview
        context.outline(previewX - 2, previewY - 2, 120, 34, 0x88FFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasHandled) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (button == 0) {
            if (mouseX >= this.radarX - 5 && mouseX <= this.radarX + 115 && mouseY >= this.radarY - 5 && mouseY <= this.radarY + 35) {
                this.dragging = true;
                this.dragStartX = mouseX - this.radarX;
                this.dragStartY = mouseY - this.radarY;
                return true;
            }
        }
        return super.mouseClicked(event, wasHandled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.dragging) {
            this.radarX = (int) (event.x() - this.dragStartX);
            this.radarY = (int) (event.y() - this.dragStartY);
            
            // Constrain to screen
            this.radarX = Math.clamp(this.radarX, 0, this.width - 100);
            this.radarY = Math.clamp(this.radarY, 0, this.height - 50);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
