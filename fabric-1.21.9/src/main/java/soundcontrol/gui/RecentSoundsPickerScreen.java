package soundcontrol.gui;

import soundcontrol.SoundConfig;
import soundcontrol.SoundControl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.util.Identifier;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class RecentSoundsPickerScreen extends Screen {
    private final Screen parent;
    private RecentSoundList soundList;

    private static final LinkedList<RecentSound> recentSounds = new LinkedList<>();
    private static final int MAX_RECENT = 100;

    public static class RecentSound {
        public final String soundId;
        public final long timestamp;
        public final double x, y, z;
        public RecentSound(String soundId, long timestamp, double x, double y, double z) {
            this.soundId = soundId; this.timestamp = timestamp; this.x = x; this.y = y; this.z = z;
        }
    }

    public static void recordRecentSound(String soundId, double x, double y, double z) {
        synchronized (recentSounds) {
            recentSounds.removeIf(s -> s.soundId.equals(soundId));
            recentSounds.addFirst(new RecentSound(soundId, System.currentTimeMillis(), x, y, z));
            while (recentSounds.size() > MAX_RECENT) recentSounds.removeLast();
        }
    }

    public static List<RecentSound> getRecentSounds() {
        synchronized (recentSounds) { return new ArrayList<>(recentSounds); }
    }

    public RecentSoundsPickerScreen(Screen parent) {
        super(Text.translatable("text.soundcontrol.recent.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.soundList = new RecentSoundList(this.client, this.width, this.height - 84, 28, 26);
        this.addSelectableChild(this.soundList);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.recent.clear"), button -> {
            synchronized (recentSounds) { recentSounds.clear(); }
            refreshList();
        }).dimensions(this.width / 2 - 160, this.height - 28, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.BACK, button -> this.close())
                .dimensions(this.width / 2 + 60, this.height - 28, 100, 20).build());

        refreshList();
    }

    private void refreshList() {
        this.soundList.clearEntries();
        for (RecentSound s : getRecentSounds()) {
            this.soundList.addEntry(new RecentSoundEntry(s, this));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.soundList.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
        if (getRecentSounds().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("text.soundcontrol.recent.empty"),
                    this.width / 2, this.height / 2, 0xFF888888);
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("text.soundcontrol.recent.subtitle"), this.width / 2, 18, 0xAAFFFF88);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) { this.close(); return true; }
        return super.keyPressed(input);
    }

    @Override public void close() { this.client.setScreen(this.parent); }
    @Override public boolean shouldPause() { return false; }

    

    private static class RecentSoundList extends ElementListWidget<RecentSoundEntry> {
        public RecentSoundList(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }
        @Override public int getRowWidth() { return 380; }
        @Override protected int getScrollbarX() { return this.getX() + this.width / 2 + 195; }
        public void clearEntries() { super.clearEntries(); }
        public int addEntry(RecentSoundEntry entry) { return super.addEntry(entry); }
    }

    

    private static class RecentSoundEntry extends ElementListWidget.Entry<RecentSoundEntry> {
        private final RecentSound sound;
        private final RecentSoundsPickerScreen parentScreen;
        private final ButtonWidget playButton;
        private final ButtonWidget muteButton;
        private final SliderWidget volumeSlider;
        private final ButtonWidget favoriteButton;
        private final ButtonWidget resetButton;
        private final ButtonWidget nameTooltipButton;
        private PositionedSoundInstance playingInstance;
        private long copiedAt = 0;

        public RecentSoundEntry(RecentSound sound, RecentSoundsPickerScreen parentScreen) {
            this.sound = sound;
            this.parentScreen = parentScreen;

            boolean initialMuted = false;
            float initialVolume = 1.0f;
            boolean initialFavorite = false;
            if (SoundConfig.containsSound(sound.soundId)) {
                SoundConfig.SoundSettings ss = SoundConfig.getSound(sound.soundId);
                initialMuted = ss.muted;
                initialVolume = ss.volume;
                initialFavorite = ss.favorite;
            }

            Identifier parsedId = Identifier.tryParse(sound.soundId);

            this.playButton = ButtonWidget.builder(Text.literal("\u25B6"), button -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (this.playingInstance != null && client.getSoundManager().isPlaying(this.playingInstance)) {
                    client.getSoundManager().stop(this.playingInstance);
                    this.playingInstance = null;
                } else if (parsedId != null) {
                    this.playingInstance = PositionedSoundInstance.master(SoundEvent.of(parsedId), 1.0F, 1.0F);
                    client.getSoundManager().play(this.playingInstance);
                }
            }).dimensions(0, 0, 20, 20).tooltip(Tooltip.of(Text.translatable("text.soundcontrol.tooltip.play"))).build();

            final String sid = sound.soundId;
            this.muteButton = ButtonWidget.builder(
                Text.translatable(initialMuted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute"),
                button -> {
                    SoundConfig.SoundSettings s = SoundConfig.computeSound(sid, k -> new SoundConfig.SoundSettings());
                    s.muted = !s.muted;
                    button.setMessage(Text.translatable(s.muted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute"));
                    if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.removeSound(sid);
                    SoundConfig.save();
                }
            ).dimensions(0, 0, 38, 20).tooltip(Tooltip.of(Text.translatable("text.soundcontrol.tooltip.mute"))).build();

            class VSlider extends SliderWidget {
                public VSlider(int x, int y, int w, int h, Text msg, double val) { super(x, y, w, h, msg, val); }
                @Override protected void updateMessage() { this.setMessage(Text.translatable("text.soundcontrol.slider.volume", (int)(this.value * 200))); }
                @Override protected void applyValue() {
                    SoundConfig.SoundSettings s = SoundConfig.computeSound(sid, k -> new SoundConfig.SoundSettings());
                    s.volume = (float)(this.value * 2.0f);
                    if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.removeSound(sid);
                    SoundConfig.save();
                }
                public void resetValue() { this.value = 0.5; this.updateMessage(); }
            }
            this.volumeSlider = new VSlider(0, 0, 80, 20, Text.translatable("text.soundcontrol.slider.volume", (int)(initialVolume * 100)), initialVolume / 2.0f);

            this.favoriteButton = ButtonWidget.builder(Text.literal(initialFavorite ? "\u2605" : "\u2606"), button -> {
                SoundConfig.SoundSettings s = SoundConfig.computeSound(sid, k -> new SoundConfig.SoundSettings());
                s.favorite = !s.favorite;
                button.setMessage(Text.literal(s.favorite ? "\u2605" : "\u2606"));
                if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.removeSound(sid);
                SoundConfig.save();
            }).dimensions(0, 0, 20, 20).tooltip(Tooltip.of(Text.translatable("tooltip.soundcontrol.favorite"))).build();

            this.resetButton = ButtonWidget.builder(Text.literal("\u27F2"), button -> {
                SoundConfig.SoundSettings cur = SoundConfig.getSound(sid);
                boolean wasFav = cur != null && cur.favorite;
                if (wasFav) { cur.volume = 1.0f; cur.muted = false; } else { SoundConfig.removeSound(sid); }
                SoundConfig.save();
                this.muteButton.setMessage(Text.translatable("text.soundcontrol.button.mute"));
                ((VSlider) this.volumeSlider).resetValue();
            }).dimensions(0, 0, 20, 20).tooltip(Tooltip.of(Text.translatable("text.soundcontrol.tooltip.reset"))).build();

            this.nameTooltipButton = ButtonWidget.builder(Text.empty(), b -> {
                MinecraftClient.getInstance().keyboard.setClipboard(sound.soundId);
                this.copiedAt = System.currentTimeMillis();
            }).dimensions(0, 0, 170, 20).build();
            this.nameTooltipButton.setAlpha(0.0f);
            this.nameTooltipButton.setTooltip(Tooltip.of(Text.literal(sound.soundId)));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = this.parentScreen.soundList.getRowLeft();
            int y = this.getY();
            var font = MinecraftClient.getInstance().textRenderer;

            String display = sound.soundId.contains(":") ? sound.soundId.substring(sound.soundId.indexOf(':') + 1) : sound.soundId;
            int maxW = 165;
            String truncated = font.trimToWidth(display, maxW);
            if (truncated.length() < display.length()) {
                display = font.trimToWidth(display, maxW - font.getWidth("...")) + "...";
            } else {
                display = truncated;
            }
            boolean showCopied = (System.currentTimeMillis() - this.copiedAt) < 2000;
            int nameColor = showCopied ? 0xFF55FF55 : 0xFFFFFFFF;
            context.drawTextWithShadow(font, display, x + 2, y + 3, nameColor);
            if (showCopied) {
                int checkX = x + 2 + font.getWidth(display) + 4;
                context.drawTextWithShadow(font, "✔", checkX, y + 3, 0xFF55FF55);
            }

            long secsAgo = (System.currentTimeMillis() - sound.timestamp) / 1000;
            String timeStr = secsAgo < 60 ? secsAgo + "s" : secsAgo < 3600 ? (secsAgo / 60) + "m" : (secsAgo / 3600) + "h";
            context.drawTextWithShadow(font, timeStr, x + 2, y + 14, 0xFF888888);
            String posStr = String.format(Locale.US, "(%.0f, %.0f, %.0f)", sound.x, sound.y, sound.z);
            context.drawTextWithShadow(font, posStr, x + 30, y + 14, 0xFF666666);

            boolean isPlaying = this.playingInstance != null && MinecraftClient.getInstance().getSoundManager().isPlaying(this.playingInstance);
            this.playButton.setMessage(Text.literal(isPlaying ? "\u25A0" : "\u25B6"));

            this.nameTooltipButton.setX(x + 2); this.nameTooltipButton.setY(y);
            this.nameTooltipButton.render(context, mouseX, mouseY, tickDelta);

            this.playButton.setX(x + 178); this.playButton.setY(y + 3);
            this.playButton.render(context, mouseX, mouseY, tickDelta);
            this.muteButton.setX(x + 200); this.muteButton.setY(y + 3);
            this.muteButton.render(context, mouseX, mouseY, tickDelta);
            this.volumeSlider.setX(x + 240); this.volumeSlider.setY(y + 3);
            this.volumeSlider.render(context, mouseX, mouseY, tickDelta);
            this.favoriteButton.setX(x + 322); this.favoriteButton.setY(y + 3);
            this.favoriteButton.render(context, mouseX, mouseY, tickDelta);
            this.resetButton.setX(x + 344); this.resetButton.setY(y + 3);
            this.resetButton.render(context, mouseX, mouseY, tickDelta);
        }

        @Override public List<? extends net.minecraft.client.gui.Element> children() {
            return List.of(nameTooltipButton, playButton, muteButton, volumeSlider, favoriteButton, resetButton);
        }
        @Override public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
            return List.of(playButton, muteButton, volumeSlider, favoriteButton, resetButton);
        }
    }
}

