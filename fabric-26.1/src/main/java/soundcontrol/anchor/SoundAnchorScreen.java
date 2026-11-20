package soundcontrol.anchor;

import soundcontrol.AnchorWorldContext;
import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import soundcontrol.gui.SoundControlScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

public class SoundAnchorScreen extends Screen {
    private final Screen parent;
    private final String worldKey = AnchorWorldContext.currentKey();
    AnchorListWidget anchorList;

    public SoundAnchorScreen(Screen parent) {
        super(Component.translatable("text.soundcontrol.anchors.title"));
        this.parent = parent;
    }

    private int listBottom() {
        return this.height - (SoundConfig.getLegacyAnchorCount() > 0 ? 80 : 56);
    }

    @Override
    protected void init() {
        this.anchorList = new AnchorListWidget(this.minecraft, this.width, listBottom() - 24, 24, 48);
        this.addRenderableWidget(this.anchorList);

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.anchors.create"), button -> {
            double[] pos = Compat.playerPos();
            if (pos != null && AnchorWorldContext.currentKey() != null) {
                int nextNum = SoundConfig.getAnchors().size() + 1;
                SoundAnchor anchor = new SoundAnchor("Anchor #" + nextNum, Compat.dimensionKey(),
                    Math.floor(pos[0]) + 0.5, Math.floor(pos[1]), Math.floor(pos[2]) + 0.5, 16);
                SoundConfig.getAnchors().add(anchor);
                SoundConfig.saveSettings();
                refreshList();
            }
        }).bounds(this.width / 2 - 100, this.height - 50, 200, 20).build());

        if (SoundConfig.getLegacyAnchorCount() > 0) {
            this.addRenderableWidget(Button.builder(
                    Component.translatable("text.soundcontrol.anchors.import_legacy", SoundConfig.getLegacyAnchorCount()), b -> {
                        SoundConfig.importLegacyAnchors();
                        Compat.setScreen(new SoundAnchorScreen(this.parent));
                    }).bounds(this.width / 2 - 140, this.height - 74, 280, 20).build());
        }

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 26, 200, 20).build());

        refreshList();
    }

    void refreshList() {
        this.anchorList.clear();
        for (SoundAnchor anchor : SoundConfig.getAnchors()) this.anchorList.add(new AnchorEntry(anchor, this));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, SoundControlScreen.BACKDROP);
        context.fill(0, 24, this.width, listBottom(), SoundControlScreen.LIST_PANEL);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        if (this.anchorList.children().isEmpty()) {
            context.centeredText(this.font, Component.translatable("text.soundcontrol.anchors.empty"), this.width / 2, (24 + listBottom()) / 2, 0xFF888888);
        }
    }

    @Override public void tick() {
        super.tick();
        if (!java.util.Objects.equals(worldKey, AnchorWorldContext.currentKey())) Compat.setScreen(null);
    }

    @Override public void onClose() { SoundConfig.flush(); Compat.setScreen(this.parent); }
    @Override public boolean isPauseScreen() { return false; }

    static class AnchorListWidget extends ContainerObjectSelectionList<AnchorEntry> {
        AnchorListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }
        @Override public int getRowWidth() { return Math.min(248, this.width - 24); }
        @Override public int getRowLeft() { return this.getX() + (this.width - getRowWidth() - 10) / 2; }
        @Override protected int scrollBarX() { return getRowLeft() + getRowWidth() + 4; }
        @Override protected void extractListBackground(GuiGraphicsExtractor context) {}
        @Override protected void extractListSeparators(GuiGraphicsExtractor context) {}
        void clear() { this.clearEntries(); }
        void add(AnchorEntry entry) { this.addEntry(entry); }
    }

    static class AnchorEntry extends ContainerObjectSelectionList.Entry<AnchorEntry> {
        private final SoundAnchor anchor;
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

        AnchorEntry(SoundAnchor anchor, SoundAnchorScreen parentScreen) {
            this.anchor = anchor;
            this.parentScreen = parentScreen;
            var font = Minecraft.getInstance().font;

            this.toggleButton = Button.builder(Component.literal(anchor.isEnabled() ? "✓" : "✗"), b -> {
                this.anchor.setEnabled(!this.anchor.isEnabled());
                b.setMessage(Component.literal(this.anchor.isEnabled() ? "✓" : "✗"));
                SoundConfig.saveSettings();
            }).bounds(0, 0, 20, 20).build();

            this.nameBox = new EditBox(font, 0, 0, 80, 16, Component.translatable("text.soundcontrol.anchors.name"));
            this.nameBox.setMaxLength(48);
            this.nameBox.setValue(anchor.getName());
            this.nameBox.setResponder(name -> { this.anchor.setName(name); SoundConfig.saveSettings(); });

            this.modeButton = Button.builder(Component.literal(modeLabel()), b -> {
                this.anchor.setShapeMode("radius".equals(this.anchor.getShapeMode()) ? "box" : "radius");
                b.setMessage(Component.literal(modeLabel()));
                updateShapeFields();
                SoundConfig.saveSettings();
            }).bounds(0, 0, 42, 20).build();

            this.showRadiusButton = Button.builder(Component.literal(anchor.isShowRadius() ? "○" : "•"), b -> {
                this.anchor.setShowRadius(!this.anchor.isShowRadius());
                b.setMessage(Component.literal(this.anchor.isShowRadius() ? "○" : "•"));
                SoundConfig.saveSettings();
            }).bounds(0, 0, 20, 20).build();

            this.editButton = Button.builder(Component.translatable("text.soundcontrol.anchors.edit"),
                b -> Compat.setScreen(new SoundAnchorEditScreen(parentScreen, this.anchor))).bounds(0, 0, 40, 20).build();

            this.deleteButton = Button.builder(Component.literal("✕"), b -> {
                SoundConfig.getAnchors().remove(this.anchor);
                SoundConfig.saveSettings();
                parentScreen.refreshList();
            }).bounds(0, 0, 20, 20).build();

            this.radiusBox = sizeBox(font, anchor.getRadius(), this.anchor::setRadius);
            this.wBox = sizeBox(font, anchor.getBoxW(), this.anchor::setBoxW);
            this.hBox = sizeBox(font, anchor.getBoxH(), this.anchor::setBoxH);
            this.dBox = sizeBox(font, anchor.getBoxD(), this.anchor::setBoxD);
            updateShapeFields();
        }

        private String modeLabel() {
            return "radius".equals(this.anchor.getShapeMode()) ? "● R" : "■ Box";
        }

        private static EditBox sizeBox(net.minecraft.client.gui.Font font, int value, java.util.function.IntConsumer setter) {
            EditBox box = new EditBox(font, 0, 0, 40, 16, Component.literal(""));
            box.setMaxLength(3);
            box.setValue(String.valueOf(value));
            box.setResponder(text -> {
                try {
                    int v = Integer.parseInt(text.trim());
                    if (v >= 1 && v <= 999) { setter.accept(v); SoundConfig.saveSettings(); }
                } catch (NumberFormatException ignored) {}
            });
            return box;
        }

        private void updateShapeFields() {
            boolean box = "box".equals(this.anchor.getShapeMode());
            this.radiusBox.visible = this.radiusBox.active = !box;
            this.wBox.visible = this.wBox.active = box;
            this.hBox.visible = this.hBox.active = box;
            this.dBox.visible = this.dBox.active = box;
            if (box) this.radiusBox.setFocused(false);
            else { this.wBox.setFocused(false); this.hBox.setFocused(false); this.dBox.setFocused(false); }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = this.getContentX();
            int y = this.getContentY();
            var font = Minecraft.getInstance().font;

            int cx = x + 2;
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

            int overrides = this.anchor.getSoundOverrides().size();
            if (overrides > 0) context.text(font, font.plainSubstrByWidth(overrides + " snd", 58), x + 184, y + 28, 0xFFFFAA00);

            if ("box".equals(this.anchor.getShapeMode())) {
                int bx = x + 2;
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
                context.text(font, "R:", x + 6, y + 28, 0xFF999999);
                this.radiusBox.setX(x + 18); this.radiusBox.setY(y + 24);
                this.radiusBox.extractRenderState(context, mouseX, mouseY, tickDelta);
            }
        }

        @Override
        public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return "box".equals(this.anchor.getShapeMode())
                ? List.of(this.toggleButton, this.nameBox, this.modeButton, this.showRadiusButton, this.editButton, this.deleteButton, this.wBox, this.hBox, this.dBox)
                : List.of(this.toggleButton, this.nameBox, this.modeButton, this.showRadiusButton, this.editButton, this.deleteButton, this.radiusBox);
        }

        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return "box".equals(this.anchor.getShapeMode())
                ? List.of(this.toggleButton, this.nameBox, this.modeButton, this.showRadiusButton, this.editButton, this.deleteButton, this.wBox, this.hBox, this.dBox)
                : List.of(this.toggleButton, this.nameBox, this.modeButton, this.showRadiusButton, this.editButton, this.deleteButton, this.radiusBox);
        }
    }
}
