package soundcontrol.anchor;

import soundcontrol.AnchorWorldContext;
import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import soundcontrol.gui.SoundControlScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.List;

public class SoundAnchorScreen extends Screen {
    private final Screen parent;
    private final String worldKey = AnchorWorldContext.currentKey();
    AnchorListWidget anchorList;

    public SoundAnchorScreen(Screen parent) {
        super(Text.translatable("text.soundcontrol.anchors.title"));
        this.parent = parent;
    }

    private int listBottom() {
        return this.height - (SoundConfig.getLegacyAnchorCount() > 0 ? 80 : 56);
    }

    @Override
    protected void init() {
        this.anchorList = new AnchorListWidget(this.client, this.width, listBottom() - 24, 24, 48);
        this.addDrawableChild(this.anchorList);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.anchors.create"), button -> {
            double[] pos = Compat.playerPos();
            if (pos != null && AnchorWorldContext.currentKey() != null) {
                int nextNum = SoundConfig.getAnchors().size() + 1;
                SoundAnchor anchor = new SoundAnchor("Anchor #" + nextNum, Compat.dimensionKey(),
                    Math.floor(pos[0]) + 0.5, Math.floor(pos[1]), Math.floor(pos[2]) + 0.5, 16);
                SoundConfig.getAnchors().add(anchor);
                SoundConfig.saveSettings();
                refreshList();
            }
        }).dimensions(this.width / 2 - 100, this.height - 50, 200, 20).build());

        if (SoundConfig.getLegacyAnchorCount() > 0) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("text.soundcontrol.anchors.import_legacy", SoundConfig.getLegacyAnchorCount()), b -> {
                        SoundConfig.importLegacyAnchors();
                        Compat.setScreen(new SoundAnchorScreen(this.parent));
                    }).dimensions(this.width / 2 - 140, this.height - 74, 280, 20).build());
        }

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> this.close())
                .dimensions(this.width / 2 - 100, this.height - 26, 200, 20).build());

        refreshList();
    }

    void refreshList() {
        this.anchorList.clear();
        for (SoundAnchor anchor : SoundConfig.getAnchors()) this.anchorList.add(new AnchorEntry(anchor, this));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, SoundControlScreen.BACKDROP);
        context.fill(0, 24, this.width, listBottom(), SoundControlScreen.LIST_PANEL);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
        if (this.anchorList.children().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("text.soundcontrol.anchors.empty"), this.width / 2, (24 + listBottom()) / 2, 0xFF888888);
        }
    }

    @Override public void tick() {
        super.tick();
        if (!java.util.Objects.equals(worldKey, AnchorWorldContext.currentKey())) Compat.setScreen(null);
    }

    @Override public void close() { SoundConfig.flush(); Compat.setScreen(this.parent); }
    @Override public boolean shouldPause() { return false; }

    static class AnchorListWidget extends ElementListWidget<AnchorEntry> {
        AnchorListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, y + height, itemHeight); this.setRenderBackground(false); this.setRenderHorizontalShadows(false);
        }
        @Override public int getRowWidth() { return Math.min(248, this.width - 24); }
        @Override public int getRowLeft() { return this.left + (this.width - getRowWidth() - 10) / 2; }
        @Override protected int getScrollbarPositionX() { return getRowLeft() + getRowWidth() + 4; }
        void clear() { this.clearEntries(); }
        void add(AnchorEntry entry) { this.addEntry(entry); }
    }

    static class AnchorEntry extends ElementListWidget.Entry<AnchorEntry> {
        private final SoundAnchor anchor;
        private final SoundAnchorScreen parentScreen;
        private final ButtonWidget toggleButton;
        private final TextFieldWidget nameBox;
        private final ButtonWidget modeButton;
        private final ButtonWidget showRadiusButton;
        private final ButtonWidget editButton;
        private final ButtonWidget deleteButton;
        private final TextFieldWidget radiusBox;
        private final TextFieldWidget wBox;
        private final TextFieldWidget hBox;
        private final TextFieldWidget dBox;

        AnchorEntry(SoundAnchor anchor, SoundAnchorScreen parentScreen) {
            this.anchor = anchor;
            this.parentScreen = parentScreen;
            var font = MinecraftClient.getInstance().textRenderer;

            this.toggleButton = ButtonWidget.builder(Text.literal(anchor.isEnabled() ? "✓" : "✗"), b -> {
                this.anchor.setEnabled(!this.anchor.isEnabled());
                b.setMessage(Text.literal(this.anchor.isEnabled() ? "✓" : "✗"));
                SoundConfig.saveSettings();
            }).dimensions(0, 0, 20, 20).build();

            this.nameBox = new TextFieldWidget(font, 0, 0, 80, 16, Text.translatable("text.soundcontrol.anchors.name"));
            this.nameBox.setMaxLength(48);
            this.nameBox.setText(anchor.getName());
            this.nameBox.setChangedListener(name -> { this.anchor.setName(name); SoundConfig.saveSettings(); });

            this.modeButton = ButtonWidget.builder(Text.literal(modeLabel()), b -> {
                this.anchor.setShapeMode("radius".equals(this.anchor.getShapeMode()) ? "box" : "radius");
                b.setMessage(Text.literal(modeLabel()));
                updateShapeFields();
                SoundConfig.saveSettings();
            }).dimensions(0, 0, 42, 20).build();

            this.showRadiusButton = ButtonWidget.builder(Text.literal(anchor.isShowRadius() ? "○" : "•"), b -> {
                this.anchor.setShowRadius(!this.anchor.isShowRadius());
                b.setMessage(Text.literal(this.anchor.isShowRadius() ? "○" : "•"));
                SoundConfig.saveSettings();
            }).dimensions(0, 0, 20, 20).build();

            this.editButton = ButtonWidget.builder(Text.translatable("text.soundcontrol.anchors.edit"),
                b -> Compat.setScreen(new SoundAnchorEditScreen(parentScreen, this.anchor))).dimensions(0, 0, 40, 20).build();

            this.deleteButton = ButtonWidget.builder(Text.literal("✕"), b -> {
                SoundConfig.getAnchors().remove(this.anchor);
                SoundConfig.saveSettings();
                parentScreen.refreshList();
            }).dimensions(0, 0, 20, 20).build();

            this.radiusBox = sizeBox(font, anchor.getRadius(), this.anchor::setRadius);
            this.wBox = sizeBox(font, anchor.getBoxW(), this.anchor::setBoxW);
            this.hBox = sizeBox(font, anchor.getBoxH(), this.anchor::setBoxH);
            this.dBox = sizeBox(font, anchor.getBoxD(), this.anchor::setBoxD);
            updateShapeFields();
        }

        private String modeLabel() {
            return "radius".equals(this.anchor.getShapeMode()) ? "● R" : "■ Box";
        }

        private static TextFieldWidget sizeBox(net.minecraft.client.font.TextRenderer font, int value, java.util.function.IntConsumer setter) {
            TextFieldWidget box = new TextFieldWidget(font, 0, 0, 40, 16, Text.literal(""));
            box.setMaxLength(3);
            box.setText(String.valueOf(value));
            box.setChangedListener(text -> {
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
        public void render(DrawContext context, int index, int y, int x, int entryW, int entryH, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            var font = MinecraftClient.getInstance().textRenderer;

            int cx = x + 2;
            this.toggleButton.setX(cx); this.toggleButton.setY(y + 2);
            this.toggleButton.render(context, mouseX, mouseY, tickDelta);
            cx += 22;
            this.nameBox.setX(cx); this.nameBox.setY(y + 4);
            this.nameBox.render(context, mouseX, mouseY, tickDelta);
            cx += 84;
            this.modeButton.setX(cx); this.modeButton.setY(y + 2);
            this.modeButton.render(context, mouseX, mouseY, tickDelta);
            cx += 46;
            this.showRadiusButton.setX(cx); this.showRadiusButton.setY(y + 2);
            this.showRadiusButton.render(context, mouseX, mouseY, tickDelta);
            cx += 24;
            this.editButton.setX(cx); this.editButton.setY(y + 2);
            this.editButton.render(context, mouseX, mouseY, tickDelta);
            cx += 44;
            this.deleteButton.setX(cx); this.deleteButton.setY(y + 2);
            this.deleteButton.render(context, mouseX, mouseY, tickDelta);

            int overrides = this.anchor.getSoundOverrides().size();
            if (overrides > 0) context.drawText(font, font.trimToWidth(overrides + " snd", 58), x + 184, y + 28, 0xFFFFAA00, true);

            if ("box".equals(this.anchor.getShapeMode())) {
                int bx = x + 2;
                context.drawText(font, "W:", bx + 4, y + 28, 0xFF999999, true);
                this.wBox.setX(bx + 16); this.wBox.setY(y + 24);
                this.wBox.render(context, mouseX, mouseY, tickDelta);
                context.drawText(font, "H:", bx + 62, y + 28, 0xFF999999, true);
                this.hBox.setX(bx + 74); this.hBox.setY(y + 24);
                this.hBox.render(context, mouseX, mouseY, tickDelta);
                context.drawText(font, "D:", bx + 120, y + 28, 0xFF999999, true);
                this.dBox.setX(bx + 132); this.dBox.setY(y + 24);
                this.dBox.render(context, mouseX, mouseY, tickDelta);
            } else {
                context.drawText(font, "R:", x + 6, y + 28, 0xFF999999, true);
                this.radiusBox.setX(x + 18); this.radiusBox.setY(y + 24);
                this.radiusBox.render(context, mouseX, mouseY, tickDelta);
            }
        }

        @Override
        public List<? extends net.minecraft.client.gui.Element> children() {
            return "box".equals(this.anchor.getShapeMode())
                ? List.of(this.toggleButton, this.nameBox, this.modeButton, this.showRadiusButton, this.editButton, this.deleteButton, this.wBox, this.hBox, this.dBox)
                : List.of(this.toggleButton, this.nameBox, this.modeButton, this.showRadiusButton, this.editButton, this.deleteButton, this.radiusBox);
        }

        @Override
        public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
            return "box".equals(this.anchor.getShapeMode())
                ? List.of(this.toggleButton, this.nameBox, this.modeButton, this.showRadiusButton, this.editButton, this.deleteButton, this.wBox, this.hBox, this.dBox)
                : List.of(this.toggleButton, this.nameBox, this.modeButton, this.showRadiusButton, this.editButton, this.deleteButton, this.radiusBox);
        }
    }
}
