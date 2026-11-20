package soundcontrol.gui;

import soundcontrol.Compat;
import soundcontrol.SoundCategory;
import soundcontrol.SoundConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public class SoundListWidget extends ContainerObjectSelectionList<SoundListWidget.SoundEntry> {
    static final List<String> GLOBAL_SOUNDS = List.of(
            "#global:break", "#global:place", "#global:step", "#global:hit",
            "#global:hostile_hurt", "#global:passive_hurt",
            "#global:hostile_ambient", "#global:passive_ambient"
    );

    private final List<SoundEntry> allEntries = new ArrayList<>();
    private String lastQuery = "";
    private SoundCategory lastCategory = SoundCategory.ALL;
    private String lastSelectedMod = "all";
    private int lastViewMode = 0;
    private int lastFilterMode = 0;

    public SoundListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
        super(client, width, height, y, y + height, itemHeight); this.setRenderBackground(false); this.setRenderTopAndBottom(false);
    }

    public void place(int x) {
        this.setLeftPos(x);
    }

    @Override
    public int getRowWidth() {
        return Math.max(260, Math.min(380, this.width - 16));
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getRowLeft() + this.getRowWidth() + 4;
    }

    public void loadEntries(int viewMode) {
        this.allEntries.clear();
        TreeSet<String> ids = new TreeSet<>();
        if (viewMode == 0) {
            for (String id : Compat.clientSoundIds()) {
                if (id.startsWith("minecraft:")) ids.add(SoundConfig.getSoundGroup(id));
            }
            for (String global : GLOBAL_SOUNDS) this.allEntries.add(new SoundEntry(global, viewMode, this));
        } else {
            ids.addAll(Compat.clientSoundIds());
        }
        for (String id : ids) this.allEntries.add(new SoundEntry(id, viewMode, this));
    }

    public void filter(String query, SoundCategory category, String selectedMod, int viewMode, int filterMode) {
        this.lastQuery = query;
        this.lastCategory = category;
        this.lastSelectedMod = selectedMod;
        this.lastViewMode = viewMode;
        this.lastFilterMode = filterMode;
        this.clearEntries();
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        if (filterMode != 0) {
            TreeSet<String> ids = new TreeSet<>();
            for (var e : SoundConfig.getSounds().entrySet()) {
                if (e.getKey().startsWith("#mod:")) continue;
                boolean match = filterMode == 2 ? e.getValue().favorite : SoundConfig.isEdited(e.getValue());
                if (match && e.getKey().toLowerCase(Locale.ROOT).contains(lowerQuery)) ids.add(e.getKey());
            }
            for (String id : ids) this.addEntry(new SoundEntry(id, 1, this));
            this.setScrollAmount(0);
            return;
        }

        for (SoundEntry entry : this.allEntries) {
            String id = entry.soundId;
            boolean match;
            if (viewMode == 2) {
                if ("all".equals(selectedMod)) match = !id.startsWith("minecraft:") && !id.startsWith("#global:");
                else match = id.startsWith(selectedMod + ":");
            } else if (category == SoundCategory.MOBS) {
                match = id.startsWith("minecraft:entity.") || id.contains("_hurt") || id.contains("_ambient");
            } else if (category == SoundCategory.BLOCKS) {
                match = id.startsWith("minecraft:block.") || List.of("#global:break", "#global:place", "#global:step", "#global:hit").contains(id);
            } else {
                match = true;
            }
            if (match && id.toLowerCase(Locale.ROOT).contains(lowerQuery)) this.addEntry(entry);
        }
        this.setScrollAmount(0);
    }

    public void refilter() {
        double scroll = this.getScrollAmount();
        filter(lastQuery, lastCategory, lastSelectedMod, lastViewMode, lastFilterMode);
        this.setScrollAmount(scroll);
    }

    static String displayName(String soundId) {
        if (soundId.startsWith("#global:")) {
            return Component.translatable("text.soundcontrol.global." + soundId.substring("#global:".length())).getString();
        }
        return soundId;
    }

    public static class SoundEntry extends ContainerObjectSelectionList.Entry<SoundEntry> {
        final String soundId;
        private final SoundListWidget parentList;
        private final Button playButton;
        private final Button muteButton;
        private final SoundSlider volumeSlider;
        private final Button favoriteButton;
        private final Button resetButton;
        private final Button textHoverButton;
        private Object playingInstance;
        private long copiedAt = 0;

        public SoundEntry(String soundId, int viewMode, SoundListWidget parentList) {
            this.soundId = soundId;
            this.parentList = parentList;
            boolean global = soundId.startsWith("#global:");

            SoundConfig.SoundSettings initial = SoundConfig.getSound(soundId);
            boolean initialMuted = initial != null && initial.muted;
            float initialVolume = initial != null ? initial.volume : 1.0f;
            boolean initialFavorite = initial != null && initial.favorite;

            boolean basic = viewMode == 0;
            Button.Builder play = Button.builder(Component.literal("▶"), button -> {
                if (this.playingInstance != null && Compat.isPlaying(this.playingInstance)) {
                    Compat.stop(this.playingInstance);
                    this.playingInstance = null;
                } else {
                    this.playingInstance = Compat.playPreview(this.soundId);
                }
            }).bounds(0, 0, 20, 20);
            if (basic) play.tooltip(Tooltip.create(Component.translatable("text.soundcontrol.tooltip.advanced_only")));
            this.playButton = play.build();
            this.playButton.active = !basic && !global;

            this.muteButton = Button.builder(muteLabel(initialMuted), button -> {
                SoundConfig.SoundSettings s = SoundConfig.computeSound(this.soundId, k -> new SoundConfig.SoundSettings());
                s.muted = !s.muted;
                button.setMessage(muteLabel(s.muted));
                SoundConfig.removeIfDefault(this.soundId);
                SoundConfig.save();
                parentList.refilter();
            }).bounds(0, 0, 50, 20).build();

            this.volumeSlider = new SoundSlider(0, 0, 100, 20, volumeLabel(initialVolume), clampSlider(initialVolume / 2.0)) {
                @Override
                protected void updateMessage() {
                    this.setMessage(this.compact ? Component.literal(Math.round(this.value * 200) + "%") : volumeLabel(this.value * 2.0));
                }

                @Override
                protected void applyValue() {
                    SoundConfig.SoundSettings s = SoundConfig.computeSound(soundId, k -> new SoundConfig.SoundSettings());
                    s.volume = (float) (this.value * 2.0);
                    SoundConfig.removeIfDefault(soundId);
                    SoundConfig.save();
                }
            };

            this.favoriteButton = Button.builder(Component.literal(initialFavorite ? "★" : "☆"), button -> {
                SoundConfig.SoundSettings s = SoundConfig.computeSound(this.soundId, k -> new SoundConfig.SoundSettings());
                s.favorite = !s.favorite;
                button.setMessage(Component.literal(s.favorite ? "★" : "☆"));
                SoundConfig.removeIfDefault(this.soundId);
                SoundConfig.save();
                parentList.refilter();
            }).bounds(0, 0, 20, 20)
              .tooltip(Tooltip.create(Component.translatable("tooltip.soundcontrol.favorite")))
              .build();

            this.resetButton = Button.builder(Component.literal("⟲"), button -> {
                SoundConfig.SoundSettings current = SoundConfig.getSound(this.soundId);
                if (current != null) {
                    current.volume = 1.0f;
                    current.muted = false;
                    current.overrideParent = false;
                }
                SoundConfig.removeIfDefault(this.soundId);
                SoundConfig.save();
                this.muteButton.setMessage(muteLabel(false));
                this.volumeSlider.resetValue(0.5);
                parentList.refilter();
            }).bounds(0, 0, 20, 20)
              .tooltip(Tooltip.create(Component.translatable("text.soundcontrol.tooltip.reset")))
              .build();

            this.textHoverButton = Button.builder(Component.literal(""), b -> {
                if (!global) {
                    Compat.copy(this.soundId);
                    this.copiedAt = System.currentTimeMillis();
                }
            }).bounds(0, 0, 100, 20).build();
            this.textHoverButton.setAlpha(0.0f);
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
            boolean compact;

            SoundSlider(int x, int y, int width, int height, Component message, double value) {
                super(x, y, width, height, message, value);
            }

            void setCompact(boolean compact) {
                if (this.compact == compact) return;
                this.compact = compact;
                this.updateMessage();
            }

            void resetValue(double value) {
                this.value = value;
                this.updateMessage();
            }
        }

        @Override
        public void render(GuiGraphics context, int index, int y, int x, int entryW, int entryH, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int entryWidth = this.parentList.getRowWidth();
            var font = Minecraft.getInstance().font;

            String displayText = displayName(this.soundId);
            int sliderWidth = Math.max(50, Math.min(100, entryWidth - 245 - 90 + 100));
            int controls = 135 + sliderWidth;
            int maxTextWidth = Math.max(20, entryWidth - controls - 10);
            String truncated = font.plainSubstrByWidth(displayText, maxTextWidth);
            if (truncated.length() < displayText.length()) {
                truncated = font.plainSubstrByWidth(displayText, maxTextWidth - font.width("...")) + "...";
            }

            boolean showCopied = System.currentTimeMillis() - this.copiedAt < 2000;
            int color = showCopied ? 0xFF55FF55 : this.soundId.startsWith("#global:") ? 0xFFFFAA00 : 0xFFFFFFFF;
            context.drawString(font, truncated, x + 2, y + 6, color);
            if (showCopied) context.drawString(font, "✔", x + 2 + font.width(truncated) + 4, y + 6, 0xFF55FF55);

            this.textHoverButton.setX(x);
            this.textHoverButton.setY(y);
            this.textHoverButton.setWidth(maxTextWidth);
            if (!truncated.equals(displayText)) {
                this.textHoverButton.setTooltip(Tooltip.create(Component.literal(displayText)));
            } else if (!this.soundId.startsWith("#global:")) {
                this.textHoverButton.setTooltip(Tooltip.create(Component.translatable("text.soundcontrol.tooltip.copy")));
            } else {
                this.textHoverButton.setTooltip(null);
            }
            this.textHoverButton.render(context, mouseX, mouseY, tickDelta);

            boolean playing = this.playingInstance != null && Compat.isPlaying(this.playingInstance);
            if (!playing) this.playingInstance = null;
            this.playButton.setMessage(Component.literal(playing ? "■" : "▶"));

            this.playButton.setX(x + entryWidth - controls);
            this.playButton.setY(y);
            this.playButton.render(context, mouseX, mouseY, tickDelta);

            this.muteButton.setX(x + entryWidth - controls + 25);
            this.muteButton.setY(y);
            this.muteButton.render(context, mouseX, mouseY, tickDelta);

            this.volumeSlider.setWidth(sliderWidth);
            this.volumeSlider.setCompact(sliderWidth < 90);
            this.volumeSlider.setX(x + entryWidth - 55 - sliderWidth);
            this.volumeSlider.setY(y);
            this.volumeSlider.render(context, mouseX, mouseY, tickDelta);

            this.favoriteButton.setX(x + entryWidth - 50);
            this.favoriteButton.setY(y);
            this.favoriteButton.render(context, mouseX, mouseY, tickDelta);

            this.resetButton.setX(x + entryWidth - 25);
            this.resetButton.setY(y);
            this.resetButton.render(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton, this.resetButton, this.textHoverButton);
        }

        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton, this.resetButton, this.textHoverButton);
        }
    }
}
