package soundcontrol.gui;

import soundcontrol.SoundCategory;
import soundcontrol.SoundConfig;
import soundcontrol.SoundControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SoundListWidget extends ContainerObjectSelectionList<SoundListWidget.SoundEntry> {
    private final List<SoundEntry> allEntries = new ArrayList<>();
    private String lastQuery = "";
    private SoundCategory lastCategory = SoundCategory.ALL;
    private String lastSelectedMod = "";
    private int lastViewMode = 0;
    private int lastFilterMode = 0;

    private static final List<String> GLOBAL_SOUNDS = List.of(
            "#global:break", "#global:place", "#global:step", "#global:hit",
            "#global:hostile_hurt", "#global:passive_hurt",
            "#global:hostile_ambient", "#global:passive_ambient"
    );

    public SoundListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
        super(client, width, height, y, itemHeight);
    }

    private int listWidth = 0;

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        int currentWidth = (this.listWidth > 0) ? this.listWidth : this.width;
        if (mouseX >= this.getX() + currentWidth) return false;
        return super.isMouseOver(mouseX, mouseY);
    }

    public void setWidth(int newWidth) {
        this.listWidth = newWidth;
    }

    @Override
    protected int scrollBarX() {
        int currentWidth = (this.listWidth > 0) ? this.listWidth : this.width;
        return this.getX() + currentWidth - 6;
    }

    @Override
    public int getRight() {
        int currentWidth = (this.listWidth > 0) ? this.listWidth : this.width;
        return this.getX() + currentWidth;
    }

    @Override
    protected void extractListBackground(net.minecraft.client.gui.GuiGraphicsExtractor context) {
        int oldWidth = this.width;
        if (this.listWidth > 0) {
            this.width = this.listWidth;
        }
        super.extractListBackground(context);
        this.width = oldWidth;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean wasHandled) {
        if (!this.isMouseOver(event.x(), event.y())) return false;
        return super.mouseClicked(event, wasHandled);
    }

    public void loadEntries(int viewMode) {
        this.allEntries.clear();
        var soundIds = Minecraft.getInstance().getSoundManager().getAvailableSounds();

        if (viewMode == 1 || viewMode == 2) {
            List<String> rawIds = new ArrayList<>();
            for (var id : soundIds) {
                rawIds.add(id.toString());
            }
            Collections.sort(rawIds);
            for (String id : rawIds) {
                this.allEntries.add(new SoundEntry(id, viewMode, this.getRowWidth(), this));
            }
        } else {
            Set<String> uniqueGroups = new HashSet<>();
            for (var id : soundIds) {
                if (id.getNamespace().equals("minecraft")) {
                    uniqueGroups.add(SoundConfig.getSoundGroup(id.toString()));
                }
            }

            List<String> sortedGroups = new ArrayList<>(uniqueGroups);
            Collections.sort(sortedGroups);

            for (String globalSound : GLOBAL_SOUNDS) {
                this.allEntries.add(new SoundEntry(globalSound, viewMode, this.getRowWidth(), this));
            }

            for (String group : sortedGroups) {
                this.allEntries.add(new SoundEntry(group, viewMode, this.getRowWidth(), this));
            }
        }
    }

    @Override
    public int getRowWidth() {
        return 380;
    }

    public void filter(String query, SoundCategory category, String selectedMod, int viewMode, int filterMode) {
        this.lastQuery = query;
        this.lastCategory = category;
        this.lastSelectedMod = selectedMod;
        this.lastViewMode = viewMode;
        this.lastFilterMode = filterMode;
        this.clearEntries();
        String lowerQuery = query.toLowerCase();

        if (filterMode == 2) {
            List<String> favoriteIds = new ArrayList<>();
            for (var e : SoundConfig.getSounds().entrySet()) {
                if (e.getValue().favorite) {
                    favoriteIds.add(e.getKey());
                }
            }
            Collections.sort(favoriteIds);
            for (String id : favoriteIds) {
                if (id.toLowerCase().contains(lowerQuery)) {
                    this.addEntry(new SoundEntry(id, 1, this.getRowWidth(), this));
                }
            }
            this.setSize(this.width, this.height);
            return;
        }

        for (SoundEntry entry : this.allEntries) {

            if (filterMode == 1) {
                if (!SoundConfig.containsSound(entry.soundId)) continue;
                SoundConfig.SoundSettings s = SoundConfig.getSound(entry.soundId);
                if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f) continue;
            }

            boolean matchCategory = false;

            if (viewMode == 2) {
                if (selectedMod != null && !selectedMod.isEmpty()) {
                    if (selectedMod.equals("all")) {
                        matchCategory = !entry.soundId.startsWith("minecraft:") && !entry.soundId.startsWith("#global:");
                    } else {
                        matchCategory = entry.soundId.startsWith(selectedMod + ":");
                    }
                } else {
                    matchCategory = false;
                }
            } else {
                if (category == SoundCategory.ALL) {
                    matchCategory = true;
                } else if (category == SoundCategory.MOBS) {
                    matchCategory = entry.soundId.startsWith("minecraft:entity.") || entry.soundId.contains("_hurt") || entry.soundId.contains("_ambient");
                } else if (category == SoundCategory.BLOCKS) {
                    matchCategory = entry.soundId.startsWith("minecraft:block.");
                }
            }

            if (matchCategory && entry.soundId.toLowerCase().contains(lowerQuery)) {
                this.addEntry(entry);
            }
        }
        
        this.setSize(this.width, this.height);
    }

    public void refilter() {
        filter(lastQuery, lastCategory, lastSelectedMod, lastViewMode, lastFilterMode);
    }

    public static class SoundEntry extends ContainerObjectSelectionList.Entry<SoundEntry> {
        final String soundId;
        private final Button playButton;
        private final Button muteButton;
        private final SoundSlider volumeSlider;
        private final Button favoriteButton;
        private final Button resetButton;
        private final Button textHoverButton;
        private final int entryWidth;
        private final int viewMode;
        private SimpleSoundInstance playingInstance;
        private final SoundListWidget parentList;
        private boolean sliderDragging = false;
        private long copiedAt = 0;

        public SoundEntry(String soundId, int viewMode, int entryWidth, SoundListWidget parentList) {
            this.soundId = soundId;
            this.entryWidth = entryWidth;
            this.viewMode = viewMode;
            this.parentList = parentList;

            boolean initialMuted = false;
            float initialVolume = 1.0f;
            boolean initialFavorite = false;

            if (SoundConfig.containsSound(soundId)) {
                initialMuted = SoundConfig.getSound(soundId).muted;
                initialVolume = SoundConfig.getSound(soundId).volume;
                initialFavorite = SoundConfig.getSound(soundId).favorite;
            }

            boolean isBasicMode = (viewMode == 0);
            boolean isPlayable = !isBasicMode && !this.soundId.startsWith("#global:");

            Button.Builder playBuilder = Button.builder(Component.literal("▶"), button -> {
                if (this.soundId.startsWith("#global:")) return;
                Identifier parsedId = Identifier.tryParse(this.soundId);
                if (parsedId != null) {
                    Minecraft client = Minecraft.getInstance();
                    if (this.playingInstance != null && client.getSoundManager().isActive(this.playingInstance)) {
                        client.getSoundManager().stop(this.playingInstance);
                        this.playingInstance = null;
                        button.setMessage(Component.literal("▶"));
                    } else {
                        SoundEvent event = SoundEvent.createVariableRangeEvent(parsedId);
                        this.playingInstance = SimpleSoundInstance.forUI(event, 1.0F);
                        client.getSoundManager().play(this.playingInstance);
                        button.setMessage(Component.literal("■"));
                    }
                }
            }).bounds(0, 0, 20, 20);

            if (isBasicMode) {
                playBuilder.tooltip(Tooltip.create(Component.translatable("text.soundcontrol.tooltip.advanced_only")));
            }

            this.playButton = playBuilder.build();
            this.playButton.active = isPlayable;

            this.muteButton = Button.builder(Component.translatable(initialMuted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute"), button -> {
                SoundConfig.SoundSettings s = SoundConfig.computeSound(this.soundId, k -> new SoundConfig.SoundSettings());
                s.muted = !s.muted;
                button.setMessage(Component.translatable(s.muted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute"));
                if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.removeSound(this.soundId);
                SoundConfig.save();
                parentList.refilter();
            }).bounds(0, 0, 50, 20).build();

            this.volumeSlider = new SoundSlider(0, 0, 100, 20, Component.translatable("text.soundcontrol.slider.volume", (int)(initialVolume * 100)), initialVolume / 2.0f) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Component.translatable("text.soundcontrol.slider.volume", (int)(this.value * 200)));
                }

                @Override
                protected void applyValue() {
                    SoundConfig.SoundSettings s = SoundConfig.computeSound(soundId, k -> new SoundConfig.SoundSettings());
                    s.volume = (float) (this.value * 2.0f);
                    if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.removeSound(soundId);
                    SoundConfig.save();
                }
            };

            this.favoriteButton = Button.builder(Component.literal(initialFavorite ? "★" : "☆"), button -> {
                SoundConfig.SoundSettings s = SoundConfig.computeSound(this.soundId, k -> new SoundConfig.SoundSettings());
                s.favorite = !s.favorite;
                button.setMessage(Component.literal(s.favorite ? "★" : "☆"));
                if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.removeSound(this.soundId);
                SoundConfig.save();
                parentList.refilter();
            }).bounds(0, 0, 20, 20).build();

            this.resetButton = Button.builder(Component.literal("⟲"), button -> {
                SoundConfig.SoundSettings current = SoundConfig.getSound(this.soundId);
                boolean wasFavorite = current != null && current.favorite;
                if (wasFavorite) {
                    current.volume = 1.0f;
                    current.muted = false;
                } else {
                    SoundConfig.removeSound(this.soundId);
                }
                SoundConfig.save();
                this.muteButton.setMessage(Component.translatable("text.soundcontrol.button.mute"));
                this.volumeSlider.resetValue(0.5);
                parentList.refilter();
            }).bounds(0, 0, 20, 20).build();
            this.resetButton.setTooltip(Tooltip.create(Component.translatable("text.soundcontrol.tooltip.reset")));

            this.textHoverButton = Button.builder(Component.literal(""), b -> {
                if (!this.soundId.startsWith("#global:")) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(this.soundId);
                    this.copiedAt = System.currentTimeMillis();
                }
            }).bounds(0, 0, 100, 20).build();
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
            int x = this.getX();
            int y = this.getY();

            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;

            String displayText = this.soundId;
            if (this.soundId.equals("#global:break")) displayText = Component.translatable("text.soundcontrol.global.break").getString();
            if (this.soundId.equals("#global:place")) displayText = Component.translatable("text.soundcontrol.global.place").getString();
            if (this.soundId.equals("#global:step")) displayText = Component.translatable("text.soundcontrol.global.step").getString();
            if (this.soundId.equals("#global:hit")) displayText = Component.translatable("text.soundcontrol.global.hit").getString();
            if (this.soundId.equals("#global:hostile_hurt")) displayText = Component.translatable("text.soundcontrol.global.hostile_hurt").getString();
            if (this.soundId.equals("#global:passive_hurt")) displayText = Component.translatable("text.soundcontrol.global.passive_hurt").getString();
            if (this.soundId.equals("#global:hostile_ambient")) displayText = Component.translatable("text.soundcontrol.global.hostile_ambient").getString();
            if (this.soundId.equals("#global:passive_ambient")) displayText = Component.translatable("text.soundcontrol.global.passive_ambient").getString();

            int maxTextWidth = this.entryWidth - 245;
            String truncated = font.plainSubstrByWidth(displayText, maxTextWidth);
            if (truncated.length() < displayText.length()) {
                truncated = font.plainSubstrByWidth(displayText, maxTextWidth - font.width("...")) + "...";
            }

            int color = 0xFFFFFFFF;
            if (this.soundId.startsWith("#global:")) {
                color = 0xFFFFAA00;
            }
            boolean showCopied = (System.currentTimeMillis() - this.copiedAt) < 2000;
            if (showCopied) color = 0xFF55FF55;
            context.text(font, truncated, x + 2, y + 5, color);
            if (showCopied) {
                int checkX = x + 2 + font.width(truncated) + 4;
                context.text(font, "\u2714", checkX, y + 5, 0xFF55FF55);
            }

            this.textHoverButton.setX(x);
            this.textHoverButton.setY(y);
            this.textHoverButton.setWidth(maxTextWidth);
            this.textHoverButton.setAlpha(0.0f);
            if (!truncated.equals(displayText)) {
                this.textHoverButton.setTooltip(Tooltip.create(Component.literal(displayText)));
            } else {
                this.textHoverButton.setTooltip(Tooltip.create(Component.translatable("text.soundcontrol.tooltip.copy")));
            }
            this.textHoverButton.extractRenderState(context, mouseX, mouseY, tickDelta);

            if (this.playingInstance != null && !Minecraft.getInstance().getSoundManager().isActive(this.playingInstance)) {
                this.playingInstance = null;
                this.playButton.setMessage(Component.literal("▶"));
            }

            this.playButton.setX(x + this.entryWidth - 235);
            this.playButton.setY(y);
            this.playButton.extractRenderState(context, mouseX, mouseY, tickDelta);

            this.muteButton.setX(x + this.entryWidth - 210);
            this.muteButton.setY(y);
            this.muteButton.extractRenderState(context, mouseX, mouseY, tickDelta);

            this.volumeSlider.setX(x + this.entryWidth - 155);
            this.volumeSlider.setY(y);
            this.volumeSlider.extractRenderState(context, mouseX, mouseY, tickDelta);

            this.favoriteButton.setX(x + this.entryWidth - 50);
            this.favoriteButton.setY(y);
            this.favoriteButton.extractRenderState(context, mouseX, mouseY, tickDelta);

            this.resetButton.setX(x + this.entryWidth - 25);
            this.resetButton.setY(y);
            this.resetButton.extractRenderState(context, mouseX, mouseY, tickDelta);
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

