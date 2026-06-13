package soundcontrol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

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
        super(Component.translatable("text.soundcontrol.recent.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.soundList = new RecentSoundList(this.minecraft, this.width, this.height - 84, 28, 26);
        this.addRenderableWidget(this.soundList);

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.recent.clear"), button -> {
            synchronized (recentSounds) { recentSounds.clear(); }
            refreshList();
        }).bounds(this.width / 2 - 160, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 + 60, this.height - 28, 100, 20).build());

        refreshList();
    }

    private void refreshList() {
        this.soundList.clearAll();
        for (RecentSound s : getRecentSounds()) {
            this.soundList.addOne(new RecentSoundEntry(s, this));
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        if (getRecentSounds().isEmpty()) {
            context.drawCenteredString(this.font, Component.translatable("text.soundcontrol.recent.empty"),
                    this.width / 2, this.height / 2, 0xFF888888);
        }
        context.drawCenteredString(this.font, Component.translatable("text.soundcontrol.recent.subtitle"), this.width / 2, 18, 0xAAFFFF88);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) { this.onClose(); return true; }
        return super.keyPressed(input);
    }

    @Override public void onClose() { this.minecraft.setScreen(this.parent); }
    @Override public boolean isPauseScreen() { return false; }

    private static class RecentSoundList extends ContainerObjectSelectionList<RecentSoundEntry> {
        public RecentSoundList(Minecraft client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }
        @Override public int getRowWidth() { return 380; }
        public void clearAll() { this.clearEntries(); }
        public void addOne(RecentSoundEntry entry) { this.addEntry(entry); }
    }

    private static class RecentSoundEntry extends ContainerObjectSelectionList.Entry<RecentSoundEntry> {
        private final RecentSound sound;
        private final RecentSoundsPickerScreen parentScreen;
        private final Button playButton;
        private final Button muteButton;
        private final SoundSlider volumeSlider;
        private final Button favoriteButton;
        private final Button resetButton;
        private final Button textHoverButton;
        private SimpleSoundInstance playingInstance;
        private boolean sliderDragging = false;
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

            this.playButton = Button.builder(Component.literal("\u25B6"), button -> {
                if (parsedId != null) {
                    Minecraft client = Minecraft.getInstance();
                    if (this.playingInstance != null && client.getSoundManager().isActive(this.playingInstance)) {
                        client.getSoundManager().stop(this.playingInstance);
                        this.playingInstance = null;
                    } else {
                        this.playingInstance = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(parsedId), 1.0F, 1.0F);
                        client.getSoundManager().play(this.playingInstance);
                    }
                }
            }).bounds(0, 0, 20, 20).build();
            this.playButton.active = parsedId != null;

            final String sid = sound.soundId;
            this.muteButton = Button.builder(
                Component.translatable(initialMuted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute"),
                button -> {
                    SoundConfig.SoundSettings s = SoundConfig.computeSound(sid, k -> new SoundConfig.SoundSettings());
                    s.muted = !s.muted;
                    button.setMessage(Component.translatable(s.muted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute"));
                    if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.removeSound(sid);
                    SoundConfig.save();
                }
            ).bounds(0, 0, 38, 20).build();

            this.volumeSlider = new SoundSlider(0, 0, 80, 20, Component.translatable("text.soundcontrol.slider.volume", (int)(initialVolume * 100)), initialVolume / 2.0f) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Component.translatable("text.soundcontrol.slider.volume", (int)(this.value * 200)));
                }
                @Override
                protected void applyValue() {
                    SoundConfig.SoundSettings s = SoundConfig.computeSound(sid, k -> new SoundConfig.SoundSettings());
                    s.volume = (float)(this.value * 2.0f);
                    if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.removeSound(sid);
                    SoundConfig.save();
                }
            };

            this.favoriteButton = Button.builder(Component.literal(initialFavorite ? "\u2605" : "\u2606"), button -> {
                SoundConfig.SoundSettings s = SoundConfig.computeSound(sid, k -> new SoundConfig.SoundSettings());
                s.favorite = !s.favorite;
                button.setMessage(Component.literal(s.favorite ? "\u2605" : "\u2606"));
                if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.removeSound(sid);
                SoundConfig.save();
            }).bounds(0, 0, 20, 20).build();

            this.resetButton = Button.builder(Component.literal("\u27F2"), button -> {
                SoundConfig.SoundSettings cur = SoundConfig.getSound(sid);
                boolean wasFav = cur != null && cur.favorite;
                if (wasFav) { cur.volume = 1.0f; cur.muted = false; } else { SoundConfig.removeSound(sid); }
                SoundConfig.save();
                this.muteButton.setMessage(Component.translatable("text.soundcontrol.button.mute"));
                this.volumeSlider.resetValue(0.5);
            }).bounds(0, 0, 20, 20)
            .tooltip(Tooltip.create(Component.translatable("text.soundcontrol.tooltip.reset")))
            .build();
            
            this.textHoverButton = Button.builder(Component.literal(""), b -> {
                if (!sid.startsWith("#global:")) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(sid);
                    this.copiedAt = System.currentTimeMillis();
                }
            }).bounds(0, 0, 100, 20).build();
        }

        private static abstract class SoundSlider extends AbstractSliderButton {
            public SoundSlider(int x, int y, int width, int height, Component message, double value) {
                super(x, y, width, height, message, value);
            }
            public void resetValue(double val) { this.value = val; this.updateMessage(); }
        }

        @Override
        public void renderContent(GuiGraphics ctx, int mouseX, int mouseY, boolean hovered, float delta) {
            var font = Minecraft.getInstance().font;

            String rawDisplay = sound.soundId.contains(":") ? sound.soundId.substring(sound.soundId.indexOf(':') + 1) : sound.soundId;
            int maxW = 165;
            String display = rawDisplay;
            if (font.width(display) > maxW) {
                while (font.width(display + "...") > maxW && display.length() > 1) display = display.substring(0, display.length() - 1);
                display = display + "...";
            }

            boolean showCopied = (System.currentTimeMillis() - this.copiedAt) < 2000;
            int nameColor = showCopied ? 0xFF55FF55 : 0xFFFFFFFF;
            ctx.drawString(font, display, this.getX() + 2, this.getY() + 3, nameColor);
            if (showCopied) {
                ctx.drawString(font, "\u2714", this.getX() + 2 + font.width(display) + 4, this.getY() + 3, 0xFF55FF55);
            }

            this.textHoverButton.setX(this.getX());
            this.textHoverButton.setY(this.getY());
            this.textHoverButton.setWidth(maxW);
            this.textHoverButton.setAlpha(0.0f);
            if (!display.equals(rawDisplay)) {
                this.textHoverButton.setTooltip(Tooltip.create(Component.literal(rawDisplay)));
            } else if (!this.sound.soundId.startsWith("#global:")) {
                this.textHoverButton.setTooltip(Tooltip.create(Component.translatable("text.soundcontrol.tooltip.copy")));
            } else {
                this.textHoverButton.setTooltip(null);
            }
            this.textHoverButton.render(ctx, mouseX, mouseY, delta);

            long secsAgo = (System.currentTimeMillis() - sound.timestamp) / 1000;
            String timeStr = secsAgo < 60 ? secsAgo + "s" : secsAgo < 3600 ? (secsAgo / 60) + "m" : (secsAgo / 3600) + "h";
            ctx.drawString(font, timeStr, this.getX() + 2, this.getY() + 14, 0xFF888888);
            String posStr = String.format(Locale.US, "(%.0f, %.0f, %.0f)", sound.x, sound.y, sound.z);
            ctx.drawString(font, posStr, this.getX() + 30, this.getY() + 14, 0xFF666666);

            boolean isPlaying = this.playingInstance != null && Minecraft.getInstance().getSoundManager().isActive(this.playingInstance);
            this.playButton.setMessage(Component.literal(isPlaying ? "\u25A0" : "\u25B6"));

            int btnX = this.getX() + 178;
            this.playButton.setX(btnX); this.playButton.setY(this.getY() + 3);
            this.playButton.render(ctx, mouseX, mouseY, delta);
            this.muteButton.setX(btnX + 22); this.muteButton.setY(this.getY() + 3);
            this.muteButton.render(ctx, mouseX, mouseY, delta);
            this.volumeSlider.setX(btnX + 62); this.volumeSlider.setY(this.getY() + 3);
            this.volumeSlider.render(ctx, mouseX, mouseY, delta);
            this.favoriteButton.setX(btnX + 144); this.favoriteButton.setY(this.getY() + 3);
            this.favoriteButton.render(ctx, mouseX, mouseY, delta);
            this.resetButton.setX(btnX + 166); this.resetButton.setY(this.getY() + 3);
            this.resetButton.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public void visitWidgets(Consumer<AbstractWidget> consumer) {
            consumer.accept(this.playButton);
            consumer.accept(this.muteButton);
            consumer.accept(this.volumeSlider);
            consumer.accept(this.favoriteButton);
            consumer.accept(this.resetButton);
            consumer.accept(this.textHoverButton);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton, this.resetButton, this.textHoverButton);
        }

        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton, this.resetButton, this.textHoverButton);
        }
    }
}
