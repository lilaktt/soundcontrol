package soundcontrol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class SoundAnchorEditScreen extends Screen {
    private final Screen parent;
    private final SoundAnchor anchor;
    EditBox searchBox;
    private AnchorSoundList soundList;

    public SoundAnchorEditScreen(Screen parent, SoundAnchor anchor) {
        super(Component.literal("Edit Anchor: " + anchor.getName()));
        this.parent = parent;
        this.anchor = anchor;
    }

    @Override
    protected void init() {
        this.searchBox = new EditBox(this.font, this.width / 2 - 90, 18, 180, 16, Component.literal("Search"));
        this.searchBox.setResponder(this::onSearch);
        this.addWidget(this.searchBox);
        this.addRenderableWidget(this.searchBox);

        this.soundList = new AnchorSoundList(this.minecraft, this.width, this.height - 88, 40, 25);
        this.addWidget(this.soundList);
        this.addRenderableWidget(this.soundList);

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.anchors.recent"), button -> {
            this.minecraft.setScreenAndShow(new RecentSoundsPickerScreen(this, this.anchor));
        }).bounds(this.width / 2 - 165, this.height - 42, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.anchors.browse"), button -> {
            this.minecraft.setScreenAndShow(new AllSoundsPickerScreen(this, this.anchor));
        }).bounds(this.width / 2 - 90, this.height - 42, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.anchors.clear"), button -> {
            this.anchor.getSoundOverrides().clear();
            SoundConfig.save();
            loadSounds("");
        }).bounds(this.width / 2 + 0, this.height - 42, 70, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 + 80, this.height - 42, 80, 20).build());

        loadSounds("");
    }

    void loadSounds(String query) {
        this.soundList.clear();
        String lowerQuery = query.toLowerCase();
        List<Map.Entry<String, SoundConfig.SoundSettings>> overrides = new ArrayList<>(anchor.getSoundOverrides().entrySet());
        overrides.sort(Comparator.comparing(Map.Entry::getKey));
        for (var entry : overrides) {
            if (entry.getKey().toLowerCase().contains(lowerQuery)) {
                this.soundList.add(new AnchorSoundEntry(entry.getKey(), anchor, this));
            }
        }
    }

    private void onSearch(String query) { loadSounds(query); }

    public void addSoundOverride(String soundId) {
        if (!this.anchor.getSoundOverrides().containsKey(soundId)) {
            SoundConfig.SoundSettings s = new SoundConfig.SoundSettings();
            s.muted = true;
            this.anchor.getSoundOverrides().put(soundId, s);
            SoundConfig.save();
        }
        loadSounds(this.searchBox != null ? this.searchBox.getValue() : "");
    }

    public void removeSoundOverride(String soundId) {
        this.anchor.getSoundOverrides().remove(soundId);
        SoundConfig.save();
        loadSounds(this.searchBox != null ? this.searchBox.getValue() : "");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 5, 0xFF55FFFF);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { this.onClose(); return true; }
        if (this.searchBox.keyPressed(input) || this.searchBox.isFocused()) return true;
        return super.keyPressed(input);
    }

    @Override public void onClose() { if (this.parent != null) { this.minecraft.setScreenAndShow(this.parent); } else { super.onClose(); } }
    @Override public boolean isPauseScreen() { return false; }

    private static class AnchorSoundList extends ContainerObjectSelectionList<AnchorSoundEntry> {
        public AnchorSoundList(Minecraft client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }
        @Override public int getRowWidth() { return 360; }
        public void clear() { this.clearEntries(); }
        public void add(AnchorSoundEntry entry) { this.addEntry(entry); }
    }


    private static class AnchorSoundEntry extends ContainerObjectSelectionList.Entry<AnchorSoundEntry> {
        private final String soundId;
        private final SoundAnchor anchor;
        private final SoundAnchorEditScreen parentScreen;
        private final Button muteButton;
        private final Button removeButton;
        private final VolumeSlider slider;

        public AnchorSoundEntry(String soundId, SoundAnchor anchor, SoundAnchorEditScreen parentScreen) {
            this.soundId = soundId;
            this.anchor = anchor;
            this.parentScreen = parentScreen;
            SoundConfig.SoundSettings s = anchor.getSoundOverrides().getOrDefault(soundId, new SoundConfig.SoundSettings());

            this.muteButton = Button.builder(Component.literal(s.muted ? "Unmute" : "Mute"), b -> {
                SoundConfig.SoundSettings ss = anchor.getSoundOverrides().computeIfAbsent(soundId, k -> new SoundConfig.SoundSettings());
                ss.muted = !ss.muted;
                b.setMessage(Component.literal(ss.muted ? "Unmute" : "Mute"));
                SoundConfig.save();
            }).bounds(0, 0, 50, 20).build();

            this.slider = new VolumeSlider(0, 0, 100, 20, s.volume, soundId, anchor);

            this.removeButton = Button.builder(Component.literal("\u2715"), b -> {
                anchor.getSoundOverrides().remove(soundId);
                SoundConfig.save();
                parentScreen.loadSounds(parentScreen.searchBox != null ? parentScreen.searchBox.getValue() : "");
            }).bounds(0, 0, 20, 20).build();
            this.removeButton.setTooltip(Tooltip.create(Component.literal("Remove override")));
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = this.getX(); int y = this.getY();
            var font = Minecraft.getInstance().font;
            String display = soundId.contains(":") ? soundId.substring(soundId.indexOf(':') + 1) : soundId;
            int maxW = 155;
            String truncated = font.plainSubstrByWidth(display, maxW);
            if (truncated.length() < display.length()) {
                display = font.plainSubstrByWidth(display, maxW - font.width("...")) + "...";
            } else {
                display = truncated;
            }
            context.text(font, display, x + 2, y + 5, 0xFFFFFFFF);
            this.muteButton.setX(x + 160); this.muteButton.setY(y); this.muteButton.extractRenderState(context, mouseX, mouseY, tickDelta);
            this.slider.setX(x + 215); this.slider.setY(y); this.slider.extractRenderState(context, mouseX, mouseY, tickDelta);
            this.removeButton.setX(x + 320); this.removeButton.setY(y); this.removeButton.extractRenderState(context, mouseX, mouseY, tickDelta);
        }

        @Override public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() { return List.of(muteButton, slider, removeButton); }
        @Override public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() { return List.of(muteButton, slider, removeButton); }
    }

    private static class VolumeSlider extends AbstractSliderButton {
        private final String soundId;
        private final SoundAnchor anchor;
        public VolumeSlider(int x, int y, int w, int h, float vol, String soundId, SoundAnchor anchor) {
            super(x, y, w, h, Component.literal((int)(vol * 100) + "%"), vol / 2.0);
            this.soundId = soundId; this.anchor = anchor;
        }
        @Override protected void updateMessage() { this.setMessage(Component.literal((int)(this.value * 200) + "%")); }
        @Override protected void applyValue() {
            SoundConfig.SoundSettings s = anchor.getSoundOverrides().computeIfAbsent(soundId, k -> new SoundConfig.SoundSettings());
            s.volume = (float)(this.value * 2.0); SoundConfig.save();
        }
    }
}
