package soundcontrol.gui;

import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class RecentSoundsPickerScreen extends Screen {
    private static final LinkedList<RecentSound> RECENT = new LinkedList<>();
    private static final int MAX_RECENT = 100;

    private final Screen parent;
    private RecentSoundList soundList;

    public static class RecentSound {
        public final String soundId;
        public final long timestamp;
        public final double x, y, z;
        RecentSound(String soundId, long timestamp, double x, double y, double z) {
            this.soundId = soundId; this.timestamp = timestamp; this.x = x; this.y = y; this.z = z;
        }
    }

    public static void recordRecentSound(String soundId, double x, double y, double z) {
        synchronized (RECENT) {
            RECENT.removeIf(s -> s.soundId.equals(soundId));
            RECENT.addFirst(new RecentSound(soundId, System.currentTimeMillis(), x, y, z));
            while (RECENT.size() > MAX_RECENT) RECENT.removeLast();
        }
    }

    public static List<RecentSound> getRecentSounds() {
        synchronized (RECENT) { return new ArrayList<>(RECENT); }
    }

    public RecentSoundsPickerScreen(Screen parent) {
        super(Component.translatable("text.soundcontrol.recent.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.soundList = new RecentSoundList(this.minecraft, this.width, this.height - 84, 32, 26);
        this.addRenderableWidget(this.soundList);

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.recent.clear"), button -> {
            synchronized (RECENT) { RECENT.clear(); }
            refreshList();
        }).bounds(this.width / 2 - 160, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.button.back"), button -> this.onClose())
            .bounds(this.width / 2 + 60, this.height - 28, 100, 20).build());

        refreshList();
    }

    void refreshList() {
        this.soundList.clear();
        for (RecentSound s : getRecentSounds()) this.soundList.add(new RecentSoundEntry(s, this));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, SoundControlScreen.BACKDROP);
        context.fill(0, 32, this.width, this.height - 52, SoundControlScreen.LIST_PANEL);
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        context.centeredText(this.font, Component.translatable("text.soundcontrol.recent.subtitle"), this.width / 2, 19, 0xAAFFFF88);
        if (this.soundList.children().isEmpty()) {
            context.centeredText(this.font, Component.translatable("text.soundcontrol.recent.empty"), this.width / 2, this.height / 2, 0xFF888888);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (key == 256) { this.onClose(); return true; }
        return super.keyPressed(input);
    }

    @Override public void onClose() { SoundConfig.flush(); Compat.setScreen(this.parent); }
    @Override public boolean isPauseScreen() { return false; }

    private static class RecentSoundList extends ContainerObjectSelectionList<RecentSoundEntry> {
        RecentSoundList(Minecraft client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }
        @Override public int getRowWidth() { return Math.max(300, Math.min(380, this.width - 16)); }
        @Override protected int scrollBarX() { return this.getRowLeft() + this.getRowWidth() + 4; }
        @Override protected void extractListBackground(GuiGraphicsExtractor context) {}
        @Override protected void extractListSeparators(GuiGraphicsExtractor context) {}
        void clear() { this.clearEntries(); }
        void add(RecentSoundEntry entry) { this.addEntry(entry); }
    }

    private static class RecentSoundEntry extends ContainerObjectSelectionList.Entry<RecentSoundEntry> {
        private final RecentSound sound;
        private final RecentSoundsPickerScreen parentScreen;
        private final Button nameButton;
        private final Button playButton;
        private final Button muteButton;
        private final SoundSlider volumeSlider;
        private final Button favoriteButton;
        private final Button resetButton;
        private Object playingInstance;
        private long copiedAt = 0;

        RecentSoundEntry(RecentSound sound, RecentSoundsPickerScreen parentScreen) {
            this.sound = sound;
            this.parentScreen = parentScreen;
            String sid = sound.soundId;

            SoundConfig.SoundSettings initial = SoundConfig.getSound(sid);
            boolean initialMuted = initial != null && initial.muted;
            float initialVolume = initial != null ? initial.volume : 1.0f;
            boolean initialFavorite = initial != null && initial.favorite;

            this.nameButton = Button.builder(Component.literal(""), b -> {
                Compat.copy(sid);
                this.copiedAt = System.currentTimeMillis();
            }).bounds(0, 0, 170, 22)
              .tooltip(Tooltip.create(Component.literal(sid)))
              .build();
            this.nameButton.setAlpha(0.0f);

            this.playButton = Button.builder(Component.literal("▶"), button -> {
                if (this.playingInstance != null && Compat.isPlaying(this.playingInstance)) {
                    Compat.stop(this.playingInstance);
                    this.playingInstance = null;
                } else {
                    this.playingInstance = Compat.playPreview(sid);
                }
            }).bounds(0, 0, 20, 20).build();

            this.muteButton = Button.builder(muteLabel(initialMuted), button -> {
                SoundConfig.SoundSettings s = SoundConfig.computeSound(sid, k -> new SoundConfig.SoundSettings());
                s.muted = !s.muted;
                button.setMessage(muteLabel(s.muted));
                SoundConfig.removeIfDefault(sid);
                SoundConfig.save();
            }).bounds(0, 0, 38, 20).build();

            this.volumeSlider = new SoundSlider(0, 0, 80, 20, volumeLabel(initialVolume), clampSlider(initialVolume / 2.0)) {
                @Override
                protected void updateMessage() {
                    this.setMessage(volumeLabel(this.value * 2.0));
                }

                @Override
                protected void applyValue() {
                    SoundConfig.SoundSettings s = SoundConfig.computeSound(sid, k -> new SoundConfig.SoundSettings());
                    s.volume = (float) (this.value * 2.0);
                    SoundConfig.removeIfDefault(sid);
                    SoundConfig.save();
                }
            };

            this.favoriteButton = Button.builder(Component.literal(initialFavorite ? "★" : "☆"), button -> {
                SoundConfig.SoundSettings s = SoundConfig.computeSound(sid, k -> new SoundConfig.SoundSettings());
                s.favorite = !s.favorite;
                button.setMessage(Component.literal(s.favorite ? "★" : "☆"));
                SoundConfig.removeIfDefault(sid);
                SoundConfig.save();
            }).bounds(0, 0, 20, 20)
              .tooltip(Tooltip.create(Component.translatable("tooltip.soundcontrol.favorite")))
              .build();

            this.resetButton = Button.builder(Component.literal("⟲"), button -> {
                SoundConfig.SoundSettings current = SoundConfig.getSound(sid);
                if (current != null) {
                    current.volume = 1.0f;
                    current.muted = false;
                    current.overrideParent = false;
                }
                SoundConfig.removeIfDefault(sid);
                SoundConfig.save();
                this.muteButton.setMessage(muteLabel(false));
                this.volumeSlider.resetValue(0.5);
            }).bounds(0, 0, 20, 20)
              .tooltip(Tooltip.create(Component.translatable("text.soundcontrol.tooltip.reset")))
              .build();
        }

        private static Component muteLabel(boolean muted) {
            return Component.translatable(muted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute");
        }

        private static Component volumeLabel(double volume) {
            return Component.translatable("text.soundcontrol.slider.volume", (int) Math.round(volume * 100));
        }

        private static double clampSlider(double value) {
            return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.5;
        }

        private abstract static class SoundSlider extends AbstractSliderButton {
            SoundSlider(int x, int y, int width, int height, Component message, double value) {
                super(x, y, width, height, message, value);
            }

            void resetValue(double value) {
                this.value = value;
                this.updateMessage();
            }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = this.getContentX();
            int y = this.getContentY();
            int rowWidth = this.parentScreen.soundList.getRowWidth();
            var font = Minecraft.getInstance().font;
            int btnX = x + rowWidth - 202;
            int nameWidth = btnX - x - 8;

            String raw = sound.soundId.substring(sound.soundId.indexOf(':') + 1);
            String display = font.plainSubstrByWidth(raw, nameWidth);
            if (display.length() < raw.length()) display = font.plainSubstrByWidth(raw, nameWidth - font.width("...")) + "...";
            boolean showCopied = System.currentTimeMillis() - this.copiedAt < 2000;
            context.text(font, display, x + 2, y + 3, showCopied ? 0xFF55FF55 : 0xFFFFFFFF);
            if (showCopied) context.text(font, "✔", x + 2 + font.width(display) + 4, y + 3, 0xFF55FF55);

            long secsAgo = Math.max(0, (System.currentTimeMillis() - sound.timestamp) / 1000);
            String time = secsAgo < 60 ? secsAgo + "s" : secsAgo < 3600 ? (secsAgo / 60) + "m" : (secsAgo / 3600) + "h";
            context.text(font, time, x + 2, y + 14, 0xFF888888);
            context.text(font, String.format(Locale.ROOT, "(%.0f, %.0f, %.0f)", sound.x, sound.y, sound.z), x + 30, y + 14, 0xFF666666);

            this.nameButton.setX(x);
            this.nameButton.setY(y);
            this.nameButton.setWidth(nameWidth + 4);
            this.nameButton.extractRenderState(context, mouseX, mouseY, tickDelta);

            boolean playing = this.playingInstance != null && Compat.isPlaying(this.playingInstance);
            if (!playing) this.playingInstance = null;
            this.playButton.setMessage(Component.literal(playing ? "■" : "▶"));

            this.playButton.setX(btnX); this.playButton.setY(y + 3);
            this.playButton.extractRenderState(context, mouseX, mouseY, tickDelta);
            this.muteButton.setX(btnX + 22); this.muteButton.setY(y + 3);
            this.muteButton.extractRenderState(context, mouseX, mouseY, tickDelta);
            this.volumeSlider.setX(btnX + 62); this.volumeSlider.setY(y + 3);
            this.volumeSlider.extractRenderState(context, mouseX, mouseY, tickDelta);
            this.favoriteButton.setX(btnX + 144); this.favoriteButton.setY(y + 3);
            this.favoriteButton.extractRenderState(context, mouseX, mouseY, tickDelta);
            this.resetButton.setX(btnX + 166); this.resetButton.setY(y + 3);
            this.resetButton.extractRenderState(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton, this.resetButton, this.nameButton);
        }

        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton, this.resetButton, this.nameButton);
        }
    }
}
