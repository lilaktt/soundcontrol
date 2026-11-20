package soundcontrol.gui;

import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

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
        super(Text.translatable("text.soundcontrol.recent.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.soundList = new RecentSoundList(this.client, this.width, this.height - 84, 32, 26);
        this.addDrawableChild(this.soundList);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.recent.clear"), button -> {
            synchronized (RECENT) { RECENT.clear(); }
            refreshList();
        }).dimensions(this.width / 2 - 160, this.height - 28, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.button.back"), button -> this.close())
            .dimensions(this.width / 2 + 60, this.height - 28, 100, 20).build());

        refreshList();
    }

    void refreshList() {
        this.soundList.clear();
        for (RecentSound s : getRecentSounds()) this.soundList.add(new RecentSoundEntry(s, this));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, SoundControlScreen.BACKDROP);
        context.fill(0, 32, this.width, this.height - 52, SoundControlScreen.LIST_PANEL);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("text.soundcontrol.recent.subtitle"), this.width / 2, 19, 0xAAFFFF88);
        if (this.soundList.children().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("text.soundcontrol.recent.empty"), this.width / 2, this.height / 2, 0xFF888888);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode;
        if (key == 256) { this.close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void close() { SoundConfig.flush(); Compat.setScreen(this.parent); }
    @Override public boolean shouldPause() { return false; }

    private static class RecentSoundList extends ElementListWidget<RecentSoundEntry> {
        RecentSoundList(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }
        @Override public int getRowWidth() { return Math.max(300, Math.min(380, this.width - 16)); }
        @Override protected int getScrollbarX() { return this.getRowLeft() + this.getRowWidth() + 4; }
        @Override protected void drawMenuListBackground(DrawContext context) {}
        @Override protected void drawHeaderAndFooterSeparators(DrawContext context) {}
        void clear() { this.clearEntries(); }
        void add(RecentSoundEntry entry) { this.addEntry(entry); }
    }

    private static class RecentSoundEntry extends ElementListWidget.Entry<RecentSoundEntry> {
        private final RecentSound sound;
        private final RecentSoundsPickerScreen parentScreen;
        private final ButtonWidget nameButton;
        private final ButtonWidget playButton;
        private final ButtonWidget muteButton;
        private final SoundSlider volumeSlider;
        private final ButtonWidget favoriteButton;
        private final ButtonWidget resetButton;
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

            this.nameButton = ButtonWidget.builder(Text.literal(""), b -> {
                Compat.copy(sid);
                this.copiedAt = System.currentTimeMillis();
            }).dimensions(0, 0, 170, 22)
              .tooltip(Tooltip.of(Text.literal(sid)))
              .build();
            this.nameButton.setAlpha(0.0f);

            this.playButton = ButtonWidget.builder(Text.literal("▶"), button -> {
                if (this.playingInstance != null && Compat.isPlaying(this.playingInstance)) {
                    Compat.stop(this.playingInstance);
                    this.playingInstance = null;
                } else {
                    this.playingInstance = Compat.playPreview(sid);
                }
            }).dimensions(0, 0, 20, 20).build();

            this.muteButton = ButtonWidget.builder(muteLabel(initialMuted), button -> {
                SoundConfig.SoundSettings s = SoundConfig.computeSound(sid, k -> new SoundConfig.SoundSettings());
                s.muted = !s.muted;
                button.setMessage(muteLabel(s.muted));
                SoundConfig.removeIfDefault(sid);
                SoundConfig.save();
            }).dimensions(0, 0, 38, 20).build();

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

            this.favoriteButton = ButtonWidget.builder(Text.literal(initialFavorite ? "★" : "☆"), button -> {
                SoundConfig.SoundSettings s = SoundConfig.computeSound(sid, k -> new SoundConfig.SoundSettings());
                s.favorite = !s.favorite;
                button.setMessage(Text.literal(s.favorite ? "★" : "☆"));
                SoundConfig.removeIfDefault(sid);
                SoundConfig.save();
            }).dimensions(0, 0, 20, 20)
              .tooltip(Tooltip.of(Text.translatable("tooltip.soundcontrol.favorite")))
              .build();

            this.resetButton = ButtonWidget.builder(Text.literal("⟲"), button -> {
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
            }).dimensions(0, 0, 20, 20)
              .tooltip(Tooltip.of(Text.translatable("text.soundcontrol.tooltip.reset")))
              .build();
        }

        private static Text muteLabel(boolean muted) {
            return Text.translatable(muted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute");
        }

        private static Text volumeLabel(double volume) {
            return Text.translatable("text.soundcontrol.slider.volume", (int) Math.round(volume * 100));
        }

        private static double clampSlider(double value) {
            return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.5;
        }

        private abstract static class SoundSlider extends SliderWidget {
            SoundSlider(int x, int y, int width, int height, Text message, double value) {
                super(x, y, width, height, message, value);
            }

            void resetValue(double value) {
                this.value = value;
                this.updateMessage();
            }
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryW, int entryH, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int rowWidth = this.parentScreen.soundList.getRowWidth();
            var font = MinecraftClient.getInstance().textRenderer;
            int btnX = x + rowWidth - 202;
            int nameWidth = btnX - x - 8;

            String raw = sound.soundId.substring(sound.soundId.indexOf(':') + 1);
            String display = font.trimToWidth(raw, nameWidth);
            if (display.length() < raw.length()) display = font.trimToWidth(raw, nameWidth - font.getWidth("...")) + "...";
            boolean showCopied = System.currentTimeMillis() - this.copiedAt < 2000;
            context.drawText(font, display, x + 2, y + 3, showCopied ? 0xFF55FF55 : 0xFFFFFFFF, true);
            if (showCopied) context.drawText(font, "✔", x + 2 + font.getWidth(display) + 4, y + 3, 0xFF55FF55, true);

            long secsAgo = Math.max(0, (System.currentTimeMillis() - sound.timestamp) / 1000);
            String time = secsAgo < 60 ? secsAgo + "s" : secsAgo < 3600 ? (secsAgo / 60) + "m" : (secsAgo / 3600) + "h";
            context.drawText(font, time, x + 2, y + 14, 0xFF888888, true);
            context.drawText(font, String.format(Locale.ROOT, "(%.0f, %.0f, %.0f)", sound.x, sound.y, sound.z), x + 30, y + 14, 0xFF666666, true);

            this.nameButton.setX(x);
            this.nameButton.setY(y);
            this.nameButton.setWidth(nameWidth + 4);
            this.nameButton.render(context, mouseX, mouseY, tickDelta);

            boolean playing = this.playingInstance != null && Compat.isPlaying(this.playingInstance);
            if (!playing) this.playingInstance = null;
            this.playButton.setMessage(Text.literal(playing ? "■" : "▶"));

            this.playButton.setX(btnX); this.playButton.setY(y + 3);
            this.playButton.render(context, mouseX, mouseY, tickDelta);
            this.muteButton.setX(btnX + 22); this.muteButton.setY(y + 3);
            this.muteButton.render(context, mouseX, mouseY, tickDelta);
            this.volumeSlider.setX(btnX + 62); this.volumeSlider.setY(y + 3);
            this.volumeSlider.render(context, mouseX, mouseY, tickDelta);
            this.favoriteButton.setX(btnX + 144); this.favoriteButton.setY(y + 3);
            this.favoriteButton.render(context, mouseX, mouseY, tickDelta);
            this.resetButton.setX(btnX + 166); this.resetButton.setY(y + 3);
            this.resetButton.render(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public List<? extends net.minecraft.client.gui.Element> children() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton, this.resetButton, this.nameButton);
        }

        @Override
        public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton, this.resetButton, this.nameButton);
        }
    }
}
