package soundcontrol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class SoundAnchorScreen extends Screen {
    private final Screen parent;
    private AnchorListWidget anchorList;

    public SoundAnchorScreen(Screen parent) {
        super(Component.translatable("text.soundcontrol.anchors.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.anchorList = new AnchorListWidget(this.minecraft, this.width, this.height - 80, 24, 48);
        this.addWidget(this.anchorList);
        this.addRenderableWidget(this.anchorList);

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.anchors.create"), button -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                var player = this.minecraft.player;
                String dim = player.level().dimension().toString();
                int nextNum = SoundConfig.getAnchors().size() + 1;
                SoundAnchor anchor = new SoundAnchor(
                    "Anchor #" + nextNum, dim,
                    Math.round(player.getX()), Math.round(player.getY()), Math.round(player.getZ()), 16
                );
                SoundConfig.getAnchors().add(anchor);
                SoundConfig.save();
                refreshList();
            }
        }).bounds(this.width / 2 - 100, this.height - 50, 200, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 26, 200, 20).build());

        refreshList();
    }

    void refreshList() {
        this.anchorList.clear();
        List<SoundAnchor> anchors = SoundConfig.getAnchors();
        for (int i = 0; i < anchors.size(); i++) {
            this.anchorList.add(new AnchorEntry(anchors.get(i), i, this));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { this.onClose(); return true; }
        return super.keyPressed(input);
    }

    @Override public void onClose() { this.minecraft.setScreen(this.parent); }
    @Override public boolean isPauseScreen() { return false; }

    private static class AnchorListWidget extends ContainerObjectSelectionList<AnchorEntry> {
        public AnchorListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }
        @Override public int getRowWidth() { return 360; }
        public void clear() { this.clearEntries(); }
        public void add(AnchorEntry entry) { this.addEntry(entry); }
    }

    private static class AnchorEntry extends ContainerObjectSelectionList.Entry<AnchorEntry> {
        private final SoundAnchor anchor;
        private final int index;
        private final SoundAnchorScreen parentScreen;

        private final Button toggleButton;
        private final EditBox nameBox;
        private final Button modeButton;
        private final Button showRadiusButton;
        private final Button editButton;
        private final Button deleteButton;

        private final EditBox radiusBox;
        private final EditBox wBox;
        private final EditBox hBox;
        private final EditBox dBox;

        public AnchorEntry(SoundAnchor anchor, int index, SoundAnchorScreen parentScreen) {
            this.anchor = anchor;
            this.index = index;
            this.parentScreen = parentScreen;
            var font = Minecraft.getInstance().font;

            this.toggleButton = Button.builder(
                Component.literal(anchor.isEnabled() ? "\u2713" : "\u2717"),
                b -> { this.anchor.setEnabled(!this.anchor.isEnabled()); b.setMessage(Component.literal(this.anchor.isEnabled() ? "\u2713" : "\u2717")); SoundConfig.save(); }
            ).bounds(0, 0, 20, 20).build();

            this.nameBox = new EditBox(font, 0, 0, 80, 16, Component.literal(""));
            this.nameBox.setValue(anchor.getName());
            this.nameBox.setResponder(name -> { this.anchor.setName(name); SoundConfig.save(); });

            this.modeButton = Button.builder(
                Component.literal("radius".equals(anchor.getShapeMode()) ? "\u25CF R" : "\u25A0 Box"),
                b -> {
                    if ("radius".equals(this.anchor.getShapeMode())) {
                        this.anchor.setShapeMode("box");
                        b.setMessage(Component.literal("\u25A0 Box"));
                    } else {
                        this.anchor.setShapeMode("radius");
                        b.setMessage(Component.literal("\u25CF R"));
                    }
                    SoundConfig.save();
                }
            ).bounds(0, 0, 42, 20).build();

            this.showRadiusButton = Button.builder(
                Component.literal(anchor.isShowRadius() ? "\u25CB" : "\u2022"),
                b -> { this.anchor.setShowRadius(!this.anchor.isShowRadius()); b.setMessage(Component.literal(this.anchor.isShowRadius() ? "\u25CB" : "\u2022")); SoundConfig.save(); }
            ).bounds(0, 0, 20, 20).build();

            this.editButton = Button.builder(Component.translatable("text.soundcontrol.anchors.edit"), b -> {
                parentScreen.minecraft.setScreen(new SoundAnchorEditScreen(parentScreen, this.anchor));
            }).bounds(0, 0, 40, 20).build();

            this.deleteButton = Button.builder(Component.literal("\u2715"), b -> {
                SoundConfig.getAnchors().remove(this.index);
                SoundConfig.save();
                parentScreen.refreshList();
            }).bounds(0, 0, 20, 20).build();

            this.radiusBox = new EditBox(font, 0, 0, 40, 16, Component.literal(""));
            this.radiusBox.setValue(String.valueOf(anchor.getRadius()));
            this.radiusBox.setResponder(val -> {
                try { int r = Integer.parseInt(val); if (r >= 1 && r <= 999) { this.anchor.setRadius(r); SoundConfig.save(); } } catch (NumberFormatException ignored) {}
            });

            this.wBox = new EditBox(font, 0, 0, 40, 16, Component.literal(""));
            this.wBox.setValue(String.valueOf(anchor.getBoxW()));
            this.wBox.setResponder(val -> {
                try { int v = Integer.parseInt(val); if (v >= 1 && v <= 999) { this.anchor.setBoxW(v); SoundConfig.save(); } } catch (NumberFormatException ignored) {}
            });

            this.hBox = new EditBox(font, 0, 0, 40, 16, Component.literal(""));
            this.hBox.setValue(String.valueOf(anchor.getBoxH()));
            this.hBox.setResponder(val -> {
                try { int v = Integer.parseInt(val); if (v >= 1 && v <= 999) { this.anchor.setBoxH(v); SoundConfig.save(); } } catch (NumberFormatException ignored) {}
            });

            this.dBox = new EditBox(font, 0, 0, 40, 16, Component.literal(""));
            this.dBox.setValue(String.valueOf(anchor.getBoxD()));
            this.dBox.setResponder(val -> {
                try { int v = Integer.parseInt(val); if (v >= 1 && v <= 999) { this.anchor.setBoxD(v); SoundConfig.save(); } } catch (NumberFormatException ignored) {}
            });
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = this.getX();
            int y = this.getY();
            var font = Minecraft.getInstance().font;

            // Row 1
            int cx = x + 58;
            this.toggleButton.setX(cx); this.toggleButton.setY(y + 2);
            this.toggleButton.extractRenderState(context, mouseX, mouseY, tickDelta);
            cx += 22;

            this.nameBox.setX(cx); this.nameBox.setY(y + 4);
            this.nameBox.extractRenderState(context, mouseX, mouseY, tickDelta);
            cx += 84;

            this.modeButton.setX(cx); this.modeButton.setY(y + 2);
            this.modeButton.extractRenderState(context, mouseX, mouseY, tickDelta);
            cx += 46;

            this.showRadiusButton.setX(cx); this.showRadiusButton.setY(y + 2);
            this.showRadiusButton.extractRenderState(context, mouseX, mouseY, tickDelta);
            cx += 24;

            this.editButton.setX(cx); this.editButton.setY(y + 2);
            this.editButton.extractRenderState(context, mouseX, mouseY, tickDelta);
            cx += 44;

            this.deleteButton.setX(cx); this.deleteButton.setY(y + 2);
            this.deleteButton.extractRenderState(context, mouseX, mouseY, tickDelta);
            cx += 24;

            int overrides = anchor.getSoundOverrides().size();
            if (overrides > 0) {
                context.text(font, overrides + " snd", cx, y + 7, 0xFFFFAA00);
            }

            // Row 2
            if ("box".equals(anchor.getShapeMode())) {
                int bx = x + 58;
                context.text(font, "W:", bx + 4, y + 28, 0xFF999999);
                this.wBox.setX(bx + 16); this.wBox.setY(y + 24);
                this.wBox.extractRenderState(context, mouseX, mouseY, tickDelta);

                context.text(font, "H:", bx + 62, y + 28, 0xFF999999);
                this.hBox.setX(bx + 74); this.hBox.setY(y + 24);
                this.hBox.extractRenderState(context, mouseX, mouseY, tickDelta);

                context.text(font, "D:", bx + 120, y + 28, 0xFF999999);
                this.dBox.setX(bx + 132); this.dBox.setY(y + 24);
                this.dBox.extractRenderState(context, mouseX, mouseY, tickDelta);
            } else {
                context.text(font, "R:", x + 62, y + 28, 0xFF999999);
                this.radiusBox.setX(x + 74); this.radiusBox.setY(y + 24);
                this.radiusBox.extractRenderState(context, mouseX, mouseY, tickDelta);
            }
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return java.util.List.of(toggleButton, nameBox, modeButton, showRadiusButton, editButton, deleteButton, radiusBox, wBox, hBox, dBox);
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return java.util.List.of(toggleButton, nameBox, modeButton, showRadiusButton, editButton, deleteButton, radiusBox, wBox, hBox, dBox);
        }
    }
}
