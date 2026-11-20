package soundcontrol.anchor;

import soundcontrol.AnchorWorldContext;
import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import soundcontrol.gui.AllSoundsPickerScreen;
import soundcontrol.gui.SoundControlScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.*;

public class SoundAnchorEditScreen extends Screen {
    private final Screen parent;
    private final String worldKey = AnchorWorldContext.currentKey();
    private final SoundAnchor anchor;
    TextFieldWidget searchBox;
    AnchorSoundList soundList;
    private String searchQuery = "";

    public SoundAnchorEditScreen(Screen parent, SoundAnchor anchor) {
        super(Text.translatable("text.soundcontrol.anchors.edit_title", anchor.getName()));
        this.parent = parent;
        this.anchor = anchor;
    }

    @Override
    protected void init() {
        this.searchBox = new TextFieldWidget(this.textRenderer, this.width / 2 - 90, 18, 180, 16, Text.translatable("text.soundcontrol.anchors.search"));
        this.searchBox.setMaxLength(256);
        this.searchBox.setText(this.searchQuery);
        this.searchBox.setChangedListener(query -> { this.searchQuery = query; loadSounds(); });
        this.addDrawableChild(this.searchBox);

        this.soundList = new AnchorSoundList(this.client, this.width, this.height - 88, 40, 25);
        this.addDrawableChild(this.soundList);

        int footerWidth = Math.min(360, this.width - 20), footerX = (this.width - footerWidth) / 2, buttonWidth = (footerWidth - 12) / 4;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.anchors.recent"),
            button -> Compat.setScreen(new AllSoundsPickerScreen(this, this.anchor, true)))
            .dimensions(footerX, this.height - 42, buttonWidth, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.anchors.browse"),
            button -> Compat.setScreen(new AllSoundsPickerScreen(this, this.anchor, false)))
            .dimensions(footerX + buttonWidth + 4, this.height - 42, buttonWidth, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.anchors.clear"), button -> {
            this.anchor.getSoundOverrides().clear();
            SoundConfig.saveSettings();
            loadSounds();
        }).dimensions(footerX + 2 * (buttonWidth + 4), this.height - 42, buttonWidth, 20).build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> this.close())
            .dimensions(footerX + 3 * (buttonWidth + 4), this.height - 42, buttonWidth, 20).build());

        loadSounds();
    }

    public void loadSounds() {
        if (this.soundList == null) return;
        this.soundList.clear();
        String lowerQuery = this.searchQuery.toLowerCase(Locale.ROOT);
        List<String> ids = new ArrayList<>(this.anchor.getSoundOverrides().keySet());
        Collections.sort(ids);
        for (String id : ids) {
            if (id.toLowerCase(Locale.ROOT).contains(lowerQuery)) this.soundList.add(new AnchorSoundEntry(id, this.anchor, this));
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, SoundControlScreen.BACKDROP);
        context.fill(0, 40, this.width, this.height - 48, SoundControlScreen.LIST_PANEL);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 5, 0xFF55FFFF);
        if (this.soundList.children().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("text.soundcontrol.anchors.no_sounds"), this.width / 2, this.height / 2, 0xFF888888);
        }
    }

    @Override public void tick() {
        super.tick();
        if (!java.util.Objects.equals(worldKey, AnchorWorldContext.currentKey())) Compat.setScreen(null);
    }

    @Override public void close() { SoundConfig.flush(); Compat.setScreen(this.parent); }
    @Override public boolean shouldPause() { return false; }

    static class AnchorSoundList extends ElementListWidget<AnchorSoundEntry> {
        AnchorSoundList(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }
        @Override public int getRowWidth() { return Math.min(360, this.width - 24); }
        @Override protected int getScrollbarX() { return this.getRowLeft() + getRowWidth() + 4; }
        @Override protected void drawMenuListBackground(DrawContext context) {}
        @Override protected void drawHeaderAndFooterSeparators(DrawContext context) {}
        void clear() { this.clearEntries(); }
        void add(AnchorSoundEntry entry) { this.addEntry(entry); }
    }

    static class AnchorSoundEntry extends ElementListWidget.Entry<AnchorSoundEntry> {
        private final String soundId;
        private final SoundAnchorEditScreen parentScreen;
        private final ButtonWidget muteButton;
        private final ButtonWidget removeButton;
        private final VolumeSlider slider;

        AnchorSoundEntry(String soundId, SoundAnchor anchor, SoundAnchorEditScreen parentScreen) {
            this.soundId = soundId;
            this.parentScreen = parentScreen;
            SoundConfig.SoundSettings s = anchor.getSoundOverrides().getOrDefault(soundId, new SoundConfig.SoundSettings());

            this.muteButton = ButtonWidget.builder(Text.translatable(s.muted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute"), b -> {
                SoundConfig.SoundSettings ss = anchor.getSoundOverrides().computeIfAbsent(soundId, k -> new SoundConfig.SoundSettings());
                ss.muted = !ss.muted;
                b.setMessage(Text.translatable(ss.muted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute"));
                SoundConfig.saveSettings();
            }).dimensions(0, 0, 50, 20).build();

            this.slider = new VolumeSlider(0, 0, 100, 20, s.volume, soundId, anchor);

            this.removeButton = ButtonWidget.builder(Text.literal("✕"), b -> {
                anchor.getSoundOverrides().remove(soundId);
                SoundConfig.saveSettings();
                parentScreen.loadSounds();
            }).dimensions(0, 0, 20, 20)
              .tooltip(Tooltip.of(Text.translatable("text.soundcontrol.anchors.remove")))
              .build();
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryW, int entryH, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            var font = MinecraftClient.getInstance().textRenderer;
            int controlX = x + this.parentScreen.soundList.getRowWidth() - 182;
            int maxW = Math.max(20, controlX - x - 5);
            String display = font.trimToWidth(this.soundId, maxW);
            if (display.length() < this.soundId.length()) display = font.trimToWidth(this.soundId, maxW - font.getWidth("...")) + "...";
            context.drawText(font, display, x + 2, y + 6, this.soundId.startsWith("#global:") ? 0xFFFFAA00 : 0xFFFFFFFF, true);
            this.muteButton.setX(controlX); this.muteButton.setY(y);
            this.muteButton.render(context, mouseX, mouseY, tickDelta);
            this.slider.setX(controlX + 55); this.slider.setY(y);
            this.slider.render(context, mouseX, mouseY, tickDelta);
            this.removeButton.setX(controlX + 160); this.removeButton.setY(y);
            this.removeButton.render(context, mouseX, mouseY, tickDelta);
        }

        @Override public List<? extends net.minecraft.client.gui.Element> children() { return List.of(this.muteButton, this.slider, this.removeButton); }
        @Override public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() { return List.of(this.muteButton, this.slider, this.removeButton); }
    }

    static class VolumeSlider extends SliderWidget {
        private final String soundId;
        private final SoundAnchor anchor;

        VolumeSlider(int x, int y, int w, int h, float volume, String soundId, SoundAnchor anchor) {
            super(x, y, w, h, Text.translatable("text.soundcontrol.slider.volume", Math.round(volume * 100)),
                Float.isFinite(volume) ? Math.max(0.0, Math.min(1.0, volume / 2.0)) : 0.5);
            this.soundId = soundId;
            this.anchor = anchor;
        }

        @Override protected void updateMessage() {
            this.setMessage(Text.translatable("text.soundcontrol.slider.volume", (int) Math.round(this.value * 200)));
        }

        @Override protected void applyValue() {
            SoundConfig.SoundSettings s = this.anchor.getSoundOverrides().computeIfAbsent(this.soundId, k -> new SoundConfig.SoundSettings());
            s.volume = (float) (this.value * 2.0);
            SoundConfig.saveSettings();
        }
    }
}
