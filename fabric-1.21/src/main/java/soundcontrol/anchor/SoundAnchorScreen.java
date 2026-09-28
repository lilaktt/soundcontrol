package soundcontrol.anchor;

import soundcontrol.SoundConfig;
import soundcontrol.AnchorWorldContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.List;

public class SoundAnchorScreen extends Screen {
    private final Screen parent;
    private final String worldKey = AnchorWorldContext.currentKey();
    private AnchorListWidget anchorList;

    public SoundAnchorScreen(Screen parent) {
        super(Text.translatable("text.soundcontrol.anchors.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.anchorList = new AnchorListWidget(this.client, this.width, this.height - (SoundConfig.getLegacyAnchorCount() > 0 ? 104 : 80), 24, 48);
        this.addDrawableChild(this.anchorList);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.anchors.create"), button -> {
            if (this.client != null && this.client.player != null && AnchorWorldContext.currentKey() != null) {
                var player = this.client.player;
                String dim = player.getWorld().getRegistryKey().getValue().toString();
                int nextNum = SoundConfig.getAnchors().size() + 1;
                SoundAnchor anchor = new SoundAnchor(
                    "Anchor #" + nextNum, dim,
                    Math.round(player.getX()), Math.round(player.getY()), Math.round(player.getZ()), 16
                );
                SoundConfig.getAnchors().add(anchor);
                SoundConfig.saveSettings();
                refreshList();
            }
        }).dimensions(this.width / 2 - 100, this.height - 50, 200, 20).build());

        if (SoundConfig.getLegacyAnchorCount() > 0) {
            this.addDrawableChild(ButtonWidget.builder(
                    Text.translatable("text.soundcontrol.anchors.import_legacy", SoundConfig.getLegacyAnchorCount()), b -> {
                        SoundConfig.importLegacyAnchors();
                        this.client.setScreen(new SoundAnchorScreen(this.parent));
                    }).dimensions(this.width / 2 - 140, this.height - 74, 280, 20).build());
        }

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> this.close())
                .dimensions(this.width / 2 - 100, this.height - 26, 200, 20).build());

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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
    }


    @Override public void tick() {
        super.tick();
        // Do not leave an editor holding another world's anchor after disconnect/reconnect.
        if (!java.util.Objects.equals(worldKey, AnchorWorldContext.currentKey())) {
            this.client.setScreen(null);
        }
    }

    @Override public void close() { this.client.setScreen(this.parent); }
    @Override public boolean shouldPause() { return false; }

    private static class AnchorListWidget extends ElementListWidget<AnchorEntry> {
        public AnchorListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }
        @Override public int getRowWidth() { return Math.min(248, this.width - 24); }
        @Override public int getRowLeft() { return (this.width - getRowWidth() - 10) / 2; }
        @Override protected int getScrollbarX() { return getRowLeft() + getRowWidth() + 4; }
        public void clear() { this.clearEntries(); }
        public void add(AnchorEntry entry) { this.addEntry(entry); }
    }

    private static class AnchorEntry extends ElementListWidget.Entry<AnchorEntry> {
        private final SoundAnchor anchor;
        private final int index;
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

        public AnchorEntry(SoundAnchor anchor, int index, SoundAnchorScreen parentScreen) {
            this.anchor = anchor;
            this.index = index;
            this.parentScreen = parentScreen;
            var font = MinecraftClient.getInstance().textRenderer;

            this.toggleButton = ButtonWidget.builder(
                Text.literal(anchor.isEnabled() ? "\u2713" : "\u2717"),
                b -> { this.anchor.setEnabled(!this.anchor.isEnabled()); b.setMessage(Text.literal(this.anchor.isEnabled() ? "\u2713" : "\u2717")); SoundConfig.saveSettings(); }
            ).dimensions(0, 0, 20, 20).build();

            this.nameBox = new TextFieldWidget(font, 0, 0, 80, 16, Text.literal(""));
            this.nameBox.setText(anchor.getName());
            this.nameBox.setChangedListener(name -> { this.anchor.setName(name); SoundConfig.saveSettings(); });

            this.modeButton = ButtonWidget.builder(
                Text.literal("radius".equals(anchor.getShapeMode()) ? "\u25CF R" : "\u25A0 Box"),
                b -> {
                    if ("radius".equals(this.anchor.getShapeMode())) {
                        this.anchor.setShapeMode("box");
                        b.setMessage(Text.literal("\u25A0 Box"));
                    } else {
                        this.anchor.setShapeMode("radius");
                        b.setMessage(Text.literal("\u25CF R"));
                    }
                    updateShapeFields();
                    SoundConfig.saveSettings();
                }
            ).dimensions(0, 0, 42, 20).build();

            this.showRadiusButton = ButtonWidget.builder(
                Text.literal(anchor.isShowRadius() ? "\u25CB" : "\u2022"),
                b -> { this.anchor.setShowRadius(!this.anchor.isShowRadius()); b.setMessage(Text.literal(this.anchor.isShowRadius() ? "\u25CB" : "\u2022")); SoundConfig.saveSettings(); }
            ).dimensions(0, 0, 20, 20).build();

            this.editButton = ButtonWidget.builder(Text.translatable("text.soundcontrol.anchors.edit"), b -> {
                parentScreen.client.setScreen(new SoundAnchorEditScreen(parentScreen, this.anchor));
            }).dimensions(0, 0, 40, 20).build();

            this.deleteButton = ButtonWidget.builder(Text.literal("\u2715"), b -> {
                SoundConfig.getAnchors().remove(this.anchor);
                SoundConfig.saveSettings();
                parentScreen.refreshList();
            }).dimensions(0, 0, 20, 20).build();

            this.radiusBox = new TextFieldWidget(font, 0, 0, 40, 16, Text.literal(""));
            this.radiusBox.setText(String.valueOf(anchor.getRadius()));
            this.radiusBox.setChangedListener(val -> {
                try { int r = Integer.parseInt(val); if (r >= 1 && r <= 999) { this.anchor.setRadius(r); SoundConfig.saveSettings(); } } catch (NumberFormatException ignored) {}
            });

            this.wBox = new TextFieldWidget(font, 0, 0, 40, 16, Text.literal(""));
            this.wBox.setText(String.valueOf(anchor.getBoxW()));
            this.wBox.setChangedListener(val -> {
                try { int v = Integer.parseInt(val); if (v >= 1 && v <= 999) { this.anchor.setBoxW(v); SoundConfig.saveSettings(); } } catch (NumberFormatException ignored) {}
            });

            this.hBox = new TextFieldWidget(font, 0, 0, 40, 16, Text.literal(""));
            this.hBox.setText(String.valueOf(anchor.getBoxH()));
            this.hBox.setChangedListener(val -> {
                try { int v = Integer.parseInt(val); if (v >= 1 && v <= 999) { this.anchor.setBoxH(v); SoundConfig.saveSettings(); } } catch (NumberFormatException ignored) {}
            });

            this.dBox = new TextFieldWidget(font, 0, 0, 40, 16, Text.literal(""));
            this.dBox.setText(String.valueOf(anchor.getBoxD()));
            this.dBox.setChangedListener(val -> {
                try { int v = Integer.parseInt(val); if (v >= 1 && v <= 999) { this.anchor.setBoxD(v); SoundConfig.saveSettings(); } } catch (NumberFormatException ignored) {}
            });
            updateShapeFields();
        }

        private void updateShapeFields() {
            boolean box = "box".equals(anchor.getShapeMode());
            radiusBox.visible = radiusBox.active = !box;
            wBox.visible = wBox.active = box;
            hBox.visible = hBox.active = box;
            dBox.visible = dBox.active = box;
            if (box) radiusBox.setFocused(false);
            else { wBox.setFocused(false); hBox.setFocused(false); dBox.setFocused(false); }
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
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
            cx += 24;

            int overrides = anchor.getSoundOverrides().size();
            if (overrides > 0) {
                context.drawText(font, font.trimToWidth(overrides + " snd", 58), x + 184, y + 28, 0xFFFFAA00, true);
            }

            if ("box".equals(anchor.getShapeMode())) {
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
        public java.util.List<? extends net.minecraft.client.gui.Element> children() {
            return "box".equals(anchor.getShapeMode()) ? java.util.List.of(toggleButton, nameBox, modeButton, showRadiusButton, editButton, deleteButton, wBox, hBox, dBox) : java.util.List.of(toggleButton, nameBox, modeButton, showRadiusButton, editButton, deleteButton, radiusBox);
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
            return "box".equals(anchor.getShapeMode()) ? java.util.List.of(toggleButton, nameBox, modeButton, showRadiusButton, editButton, deleteButton, wBox, hBox, dBox) : java.util.List.of(toggleButton, nameBox, modeButton, showRadiusButton, editButton, deleteButton, radiusBox);
        }
    }
}

