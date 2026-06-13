package soundcontrol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Shows recently played sounds. Also used as a picker for adding sounds to anchors.
 */
public class RecentSoundsPickerScreen extends Screen {
    private final Screen parent;
    public final SoundAnchor targetAnchor;
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

    public RecentSoundsPickerScreen(Screen parent, SoundAnchor targetAnchor) {
        super(Component.translatable(targetAnchor != null ? "text.soundcontrol.recent.pick" : "text.soundcontrol.recent.title"));
        this.parent = parent; this.targetAnchor = targetAnchor;
    }

    public RecentSoundsPickerScreen(Screen parent) { this(parent, null); }

    @Override
    protected void init() {
        this.soundList = new RecentSoundList(this.minecraft, this.width, this.height - 84, 28, 26);
        this.addWidget(this.soundList);
        this.addRenderableWidget(this.soundList);

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.recent.clear"), button -> {
            synchronized (recentSounds) { recentSounds.clear(); }
            refreshList();
        }).bounds(this.width / 2 - 160, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.button.back"), button -> this.onClose())
                .bounds(this.width / 2 + 60, this.height - 28, 100, 20).build());

        refreshList();
    }

    public void refreshList() {
        this.soundList.clear();
        for (RecentSound s : getRecentSounds()) {
            this.soundList.add(new RecentSoundEntry(s, this));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        if (getRecentSounds().isEmpty()) {
            context.centeredText(this.font, Component.translatable("text.soundcontrol.recent.empty"),
                    this.width / 2, this.height / 2, 0xFF888888);
        }
        context.centeredText(this.font, Component.translatable(targetAnchor != null ? "text.soundcontrol.recent.pick_hint" : "text.soundcontrol.recent.subtitle"), this.width / 2, 18, 0xAAFFFF88);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { this.onClose(); return true; }
        return super.keyPressed(input);
    }

    @Override public void onClose() { this.minecraft.setScreen(this.parent); }
    @Override public boolean isPauseScreen() { return false; }

    // ========== List ==========

    private static class RecentSoundList extends ContainerObjectSelectionList<RecentSoundEntry> {
        public RecentSoundList(Minecraft client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
        }
        @Override public int getRowWidth() { return 380; }
        public void clear() { this.clearEntries(); }
        public void add(RecentSoundEntry entry) { this.addEntry(entry); }
    }

    // ========== Entry ==========

    private static class RecentSoundEntry extends ContainerObjectSelectionList.Entry<RecentSoundEntry> {
        private final RecentSound sound;
        private final RecentSoundsPickerScreen parentScreen;
        private final Button playButton;
        private final Button muteButton;
        private final SoundSlider volumeSlider;
        private final Button favoriteButton;
        private final Button resetButton;
        private final Button nameTooltipButton;
        private final Button anchorAddButton;
        private SimpleSoundInstance playingInstance;
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
                        button.setMessage(Component.literal("\u25B6"));
                    } else {
                        SoundEvent event = SoundEvent.createVariableRangeEvent(parsedId);
                        this.playingInstance = SimpleSoundInstance.forUI(event, 1.0F);
                        client.getSoundManager().play(this.playingInstance);
                        button.setMessage(Component.literal("\u25A0"));
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
            }).bounds(0, 0, 20, 20).build();
            this.resetButton.setTooltip(Tooltip.create(Component.translatable("text.soundcontrol.tooltip.reset")));

            this.nameTooltipButton = Button.builder(Component.literal(""), b -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(sound.soundId);
                this.copiedAt = System.currentTimeMillis();
            }).bounds(0, 0, 170, 20).build();
            this.nameTooltipButton.setTooltip(Tooltip.create(Component.literal(sound.soundId)));

            if (parentScreen.targetAnchor != null) {
                boolean alreadyAdded = parentScreen.targetAnchor.getSoundOverrides().containsKey(sound.soundId);
                this.anchorAddButton = Button.builder(
                    Component.literal(alreadyAdded ? "\u2715" : "+"),
                    b -> {
                        if (parentScreen.parent instanceof SoundAnchorEditScreen editScreen) {
                            boolean isAdded = parentScreen.targetAnchor.getSoundOverrides().containsKey(sound.soundId);
                            if (isAdded) {
                                editScreen.removeSoundOverride(sound.soundId);
                                b.setMessage(Component.literal("+"));
                            } else {
                                editScreen.addSoundOverride(sound.soundId);
                                b.setMessage(Component.literal("\u2715"));
                            }
                        }
                    }
                ).bounds(0, 0, 20, 20).build();
            } else {
                this.anchorAddButton = null;
            }
        }

        private static abstract class SoundSlider extends AbstractSliderButton {
            public SoundSlider(int x, int y, int width, int height, Component message, double value) {
                super(x, y, width, height, message, value);
            }
            public void resetValue(double val) {
                this.value = val;
                this.updateMessage();
            }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = this.getX(); int y = this.getY();
            var font = Minecraft.getInstance().font;

            // Sound name (without minecraft: prefix)
            String rawDisplay = sound.soundId.contains(":") ? sound.soundId.substring(sound.soundId.indexOf(':') + 1) : sound.soundId;
            int maxW = 165;
            String display = font.plainSubstrByWidth(rawDisplay, maxW);
            if (display.length() < rawDisplay.length()) {
                display = font.plainSubstrByWidth(rawDisplay, maxW - font.width("...")) + "...";
            }
            boolean showCopied = (System.currentTimeMillis() - this.copiedAt) < 2000;
            int nameColor = showCopied ? 0xFF55FF55 : 0xFFFFFFFF;
            context.text(font, display, x + 2, y + 3, nameColor);
            if (showCopied) {
                int checkX = x + 2 + font.width(display) + 4;
                context.text(font, "\u2714", checkX, y + 3, 0xFF55FF55);
            }

            // Time and coordinates on second line, close to text
            long secsAgo = (System.currentTimeMillis() - sound.timestamp) / 1000;
            String timeStr = secsAgo < 60 ? secsAgo + "s" : secsAgo < 3600 ? (secsAgo / 60) + "m" : (secsAgo / 3600) + "h";
            context.text(font, timeStr, x + 2, y + 14, 0xFF888888);
            String posStr = String.format("(%.0f, %.0f, %.0f)", sound.x, sound.y, sound.z);
            context.text(font, posStr, x + 30, y + 14, 0xFF666666);

            boolean isPlaying = this.playingInstance != null && Minecraft.getInstance().getSoundManager().isActive(this.playingInstance);
            this.playButton.setMessage(Component.literal(isPlaying ? "\u25A0" : "\u25B6"));

            // Invisible click area for copying
            this.nameTooltipButton.setX(x + 2);
            this.nameTooltipButton.setY(y);
            this.nameTooltipButton.setAlpha(0.0f);
            this.nameTooltipButton.extractRenderState(context, mouseX, mouseY, tickDelta);

            // Buttons on the right, matching screenshot layout
            int btnX = x + 178;

            if (this.anchorAddButton != null) {
                this.anchorAddButton.setX(btnX - 25);
                this.anchorAddButton.setY(y + 3);
                this.anchorAddButton.extractRenderState(context, mouseX, mouseY, tickDelta);
            }

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
            List<net.minecraft.client.gui.components.events.GuiEventListener> list = new ArrayList<>();
            list.add(nameTooltipButton);
            if (anchorAddButton != null) list.add(anchorAddButton);
            list.add(playButton); list.add(muteButton); list.add(volumeSlider);
            list.add(favoriteButton); list.add(resetButton);
            return list;
        }

        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return List.of(playButton, muteButton, volumeSlider, favoriteButton, resetButton);
        }
    }
}
