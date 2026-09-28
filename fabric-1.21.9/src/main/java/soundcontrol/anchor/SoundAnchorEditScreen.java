package soundcontrol.anchor;

import soundcontrol.gui.AllSoundsPickerScreen;
import soundcontrol.gui.RecentSoundsPickerScreen;
import soundcontrol.SoundConfig;
import soundcontrol.AnchorWorldContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
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
    private AnchorSoundList soundList;
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
        this.searchBox.setText(searchQuery);
        this.searchBox.setChangedListener(this::onSearch);
        this.addDrawableChild(this.searchBox);

        this.soundList = new AnchorSoundList(this.client, this.width, this.height - 88, 40, 25);
        this.addDrawableChild(this.soundList);

        int footerWidth = Math.min(360, this.width - 20), footerX = (this.width - footerWidth) / 2, buttonWidth = (footerWidth - 12) / 4;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.anchors.recent"), button -> {
            this.client.setScreen(new AllSoundsPickerScreen(this, this.anchor, true));
        }).dimensions(footerX, this.height - 42, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.anchors.browse"), button -> {
            this.client.setScreen(new AllSoundsPickerScreen(this, this.anchor));
        }).dimensions(footerX + buttonWidth + 4, this.height - 42, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.anchors.clear"), button -> {
            this.anchor.getSoundOverrides().clear();
            SoundConfig.saveSettings();
            loadSounds(this.searchBox.getText());
        }).dimensions(footerX + 2 * (buttonWidth + 4), this.height - 42, buttonWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> this.close())
                .dimensions(footerX + 3 * (buttonWidth + 4), this.height - 42, buttonWidth, 20).build());

        loadSounds(searchQuery);
    }

    void loadSounds(String query) {
        if (this.soundList == null) return;
        this.soundList.clear();
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        List<Map.Entry<String, SoundConfig.SoundSettings>> overrides = new ArrayList<>(anchor.getSoundOverrides().entrySet());
        overrides.sort(Comparator.comparing(Map.Entry::getKey));
        for (var entry : overrides) {
            if (entry.getKey().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                this.soundList.add(new AnchorSoundEntry(entry.getKey(), anchor, this));
            }
        }
    }

    private void onSearch(String query) { searchQuery = query; loadSounds(query); }

    public void addSoundOverride(String soundId) {
        if (!this.anchor.getSoundOverrides().containsKey(soundId)) {
            SoundConfig.SoundSettings s = new SoundConfig.SoundSettings();
            s.muted = true;
            this.anchor.getSoundOverrides().put(soundId, s);
            SoundConfig.saveSettings();
        }
        loadSounds(this.searchBox != null ? this.searchBox.getText() : "");
    }

    public void removeSoundOverride(String soundId) {
        this.anchor.getSoundOverrides().remove(soundId);
        SoundConfig.saveSettings();
        loadSounds(this.searchBox != null ? this.searchBox.getText() : "");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 5, 0xFF55FFFF);
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

    private static class AnchorSoundList extends ElementListWidget<AnchorSoundEntry> {
        public AnchorSoundList(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }
        @Override public int getRowWidth() { return Math.min(360, this.width - 24); }
        @Override protected int getScrollbarX() { return this.width / 2 + getRowWidth() / 2 + 4; }
        public void clear() { this.clearEntries(); }
        public void add(AnchorSoundEntry entry) { this.addEntry(entry); }
    }

    private static class AnchorSoundEntry extends ElementListWidget.Entry<AnchorSoundEntry> {
        private final String soundId;
        private final SoundAnchor anchor;
        private final SoundAnchorEditScreen parentScreen;
        private final ButtonWidget muteButton;
        private final ButtonWidget removeButton;
        private final VolumeSlider slider;

        public AnchorSoundEntry(String soundId, SoundAnchor anchor, SoundAnchorEditScreen parentScreen) {
            this.soundId = soundId;
            this.anchor = anchor;
            this.parentScreen = parentScreen;
            SoundConfig.SoundSettings s = anchor.getSoundOverrides().getOrDefault(soundId, new SoundConfig.SoundSettings());

            this.muteButton = ButtonWidget.builder(Text.translatable(s.muted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute"), b -> {
                SoundConfig.SoundSettings ss = anchor.getSoundOverrides().computeIfAbsent(soundId, k -> new SoundConfig.SoundSettings());
                ss.muted = !ss.muted;
                b.setMessage(Text.translatable(ss.muted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute"));
                SoundConfig.saveSettings();
            }).dimensions(0, 0, 50, 20).build();

            this.slider = new VolumeSlider(0, 0, 100, 20, s.volume, soundId, anchor);

            this.removeButton = ButtonWidget.builder(Text.literal("\u2715"), b -> {
                anchor.getSoundOverrides().remove(soundId);
                SoundConfig.saveSettings();
                parentScreen.loadSounds(parentScreen.searchBox != null ? parentScreen.searchBox.getText() : "");
            }).dimensions(0, 0, 20, 20).build();
            this.removeButton.setTooltip(Tooltip.of(Text.translatable("text.soundcontrol.anchors.remove")));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = parentScreen.soundList.getRowLeft(); int y = this.getY();
            var font = MinecraftClient.getInstance().textRenderer;
            String display = soundId;
            int controlX = x + parentScreen.soundList.getRowWidth() - 182;
            int maxW = Math.max(20, controlX - x - 5);
            String truncated = font.trimToWidth(display, maxW);
            if (truncated.length() < display.length()) {
                display = font.trimToWidth(display, maxW - font.getWidth("...")) + "...";
            } else {
                display = truncated;
            }
            context.drawText(font, display, x + 2, y + 5, 0xFFFFFFFF, true);
            this.muteButton.setX(controlX); this.muteButton.setY(y); this.muteButton.render(context, mouseX, mouseY, tickDelta);
            this.slider.setX(controlX + 55); this.slider.setY(y); this.slider.render(context, mouseX, mouseY, tickDelta);
            this.removeButton.setX(controlX + 160); this.removeButton.setY(y); this.removeButton.render(context, mouseX, mouseY, tickDelta);
        }

        @Override public List<? extends net.minecraft.client.gui.Element> children() { return List.of(muteButton, slider, removeButton); }
        @Override public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() { return List.of(muteButton, slider, removeButton); }
    }

    private static class VolumeSlider extends SliderWidget {
        private final String soundId;
        private final SoundAnchor anchor;
        public VolumeSlider(int x, int y, int w, int h, float vol, String soundId, SoundAnchor anchor) {
            super(x, y, w, h, Text.literal((int)(vol * 100) + "%"), vol / 2.0);
            this.soundId = soundId; this.anchor = anchor;
        }
        @Override protected void updateMessage() { this.setMessage(Text.literal((int)(this.value * 200) + "%")); }
        @Override protected void applyValue() {
            SoundConfig.SoundSettings s = anchor.getSoundOverrides().computeIfAbsent(soundId, k -> new SoundConfig.SoundSettings());
            s.volume = (float)(this.value * 2.0); SoundConfig.saveSettings();
        }
    }
}

