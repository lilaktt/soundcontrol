package soundcontrol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class SoundListWidget extends ContainerObjectSelectionList<SoundListWidget.SoundEntry> {
    private final List<SoundEntry> allEntries = new ArrayList<>();

    public SoundListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
        super(client, width, height, y, itemHeight);
    }

    public void loadEntries(int viewMode) {
        this.allEntries.clear();
        Collection<ResourceLocation> soundIds = Minecraft.getInstance().getSoundManager().getAvailableSounds();

        if (viewMode == 1 || viewMode == 2) {
            List<String> rawIds = new ArrayList<>();
            for (ResourceLocation id : soundIds) {
                rawIds.add(id.toString());
            }
            Collections.sort(rawIds);
            for (String id : rawIds) {
                this.allEntries.add(new SoundEntry(id, viewMode, this.getRowWidth()));
            }
        } else {
            Set<String> uniqueGroups = new HashSet<>();
            for (ResourceLocation id : soundIds) {
                if (id.getNamespace().equals("minecraft")) {
                    uniqueGroups.add(SoundConfig.getSoundGroup(id.toString()));
                }
            }

            List<String> sortedGroups = new ArrayList<>(uniqueGroups);
            Collections.sort(sortedGroups);

            this.allEntries.add(new SoundEntry("#global:break", viewMode, this.getRowWidth()));
            this.allEntries.add(new SoundEntry("#global:place", viewMode, this.getRowWidth()));
            this.allEntries.add(new SoundEntry("#global:step", viewMode, this.getRowWidth()));
            this.allEntries.add(new SoundEntry("#global:hit", viewMode, this.getRowWidth()));
            this.allEntries.add(new SoundEntry("#global:hostile_hurt", viewMode, this.getRowWidth()));
            this.allEntries.add(new SoundEntry("#global:passive_hurt", viewMode, this.getRowWidth()));
            this.allEntries.add(new SoundEntry("#global:hostile_ambient", viewMode, this.getRowWidth()));
            this.allEntries.add(new SoundEntry("#global:passive_ambient", viewMode, this.getRowWidth()));

            for (String group : sortedGroups) {
                this.allEntries.add(new SoundEntry(group, viewMode, this.getRowWidth()));
            }
        }
    }

    @Override
    public int getRowWidth() {
        return 380;
    }

    public void filter(String query, SoundCategory category, String selectedMod, int viewMode, int filterMode) {
        this.clearEntries();
        String lowerQuery = query.toLowerCase();
        for (SoundEntry entry : this.allEntries) {

            if (filterMode == 1) {
                if (!SoundConfig.SOUNDS.containsKey(entry.soundId)) {
                    continue;
                }
                SoundConfig.SoundSettings s = SoundConfig.SOUNDS.get(entry.soundId);
                if (!s.muted && !s.favorite && s.volume == 1.0f) {
                    continue;
                }
            }

            if (filterMode == 2) {
                if (!SoundConfig.SOUNDS.containsKey(entry.soundId) || !SoundConfig.SOUNDS.get(entry.soundId).favorite) {
                    continue;
                }
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
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (Minecraft.getInstance().screen instanceof SoundControlScreen screen) {
            if (screen.getViewMode() == 2 && mouseX > screen.width - 125) {
                return false;
            }
            if (mouseY > screen.height - 40) {
                return false;
            }
        }
        return super.isMouseOver(mouseX, mouseY);
    }

    public static class SoundEntry extends ContainerObjectSelectionList.Entry<SoundEntry> {
        final String soundId;
        private final Button playButton;
        private final Button muteButton;
        private final AbstractSliderButton volumeSlider;
        private final Button favoriteButton;
        private SimpleSoundInstance playingInstance;
        private final int entryWidth;

        public SoundEntry(String soundId, int viewMode, int entryWidth) {
            this.soundId = soundId;
            this.entryWidth = entryWidth;

            boolean initialMuted = false;
            float initialVolume = 1.0f;
            boolean initialFavorite = false;

            if (SoundConfig.SOUNDS.containsKey(soundId)) {
                initialMuted = SoundConfig.SOUNDS.get(soundId).muted;
                initialVolume = SoundConfig.SOUNDS.get(soundId).volume;
                initialFavorite = SoundConfig.SOUNDS.get(soundId).favorite;
            }

            boolean isBasicMode = (viewMode == 0);
            ResourceLocation parsedId = ResourceLocation.tryParse(this.soundId);
            boolean isPlayable = !isBasicMode && parsedId != null;

            this.playButton = Button.builder(Component.literal("▶"), button -> {
                Minecraft client = Minecraft.getInstance();
                if (this.playingInstance != null && client.getSoundManager().isActive(this.playingInstance)) {
                    client.getSoundManager().stop(this.playingInstance);
                    this.playingInstance = null;
                } else if (isPlayable) {
                    this.playingInstance = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(parsedId), 1.0F, 1.0F);
                    client.getSoundManager().play(this.playingInstance);
                }
            }).bounds(0, 0, 20, 20).build();

            if (isBasicMode) {
                this.playButton.setTooltip(Tooltip.create(Component.translatable("text.soundcontrol.tooltip.advanced_only")));
            }
            this.playButton.active = isPlayable;

            this.muteButton = Button.builder(Component.translatable(initialMuted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute"), button -> {
                SoundConfig.SoundSettings s = SoundConfig.SOUNDS.computeIfAbsent(this.soundId, k -> new SoundConfig.SoundSettings());
                s.muted = !s.muted;
                button.setMessage(Component.translatable(s.muted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute"));
                SoundConfig.save();
            }).bounds(0, 0, 50, 20).build();

            this.volumeSlider = new AbstractSliderButton(0, 0, 100, 20, Component.translatable("text.soundcontrol.slider.volume", (int)(initialVolume * 100)), initialVolume / 2.0f) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Component.translatable("text.soundcontrol.slider.volume", (int)(this.value * 200)));
                }

                @Override
                protected void applyValue() {
                    SoundConfig.SoundSettings s = SoundConfig.SOUNDS.computeIfAbsent(soundId, k -> new SoundConfig.SoundSettings());
                    s.volume = (float) (this.value * 2.0f);
                    SoundConfig.save();
                }
            };

            this.favoriteButton = Button.builder(Component.literal(initialFavorite ? "★" : "☆"), button -> {
                SoundConfig.SoundSettings s = SoundConfig.SOUNDS.computeIfAbsent(this.soundId, k -> new SoundConfig.SoundSettings());
                s.favorite = !s.favorite;
                button.setMessage(Component.literal(s.favorite ? "★" : "☆"));
                SoundConfig.save();
            }).bounds(0, 0, 20, 20).build();
        }

        @Override
        public void renderContent(GuiGraphics pGuiGraphics, int mouseX, int mouseY, boolean pIsMouseOver, float pPartialTick) {
            boolean isPlaying = this.playingInstance != null && Minecraft.getInstance().getSoundManager().isActive(this.playingInstance);
            this.playButton.setMessage(Component.literal(isPlaying ? "■" : "▶"));

            Component displayIdText = Component.literal(this.soundId);

            if (this.soundId.equals("#global:break")) displayIdText = Component.translatable("text.soundcontrol.global.break");
            if (this.soundId.equals("#global:place")) displayIdText = Component.translatable("text.soundcontrol.global.place");
            if (this.soundId.equals("#global:step")) displayIdText = Component.translatable("text.soundcontrol.global.step");
            if (this.soundId.equals("#global:hit")) displayIdText = Component.translatable("text.soundcontrol.global.hit");
            if (this.soundId.equals("#global:hostile_hurt")) displayIdText = Component.translatable("text.soundcontrol.global.hostile_hurt");
            if (this.soundId.equals("#global:passive_hurt")) displayIdText = Component.translatable("text.soundcontrol.global.passive_hurt");
            if (this.soundId.equals("#global:hostile_ambient")) displayIdText = Component.translatable("text.soundcontrol.global.hostile_ambient");
            if (this.soundId.equals("#global:passive_ambient")) displayIdText = Component.translatable("text.soundcontrol.global.passive_ambient");

            String textStr = displayIdText.getString();
            int maxWidth = this.entryWidth - 215;
            String trimmedText = Minecraft.getInstance().font.plainSubstrByWidth(textStr, maxWidth);
            int color = this.soundId.startsWith("#global:") ? 0xFFFFAA00 : 0xFFFFFFFF;

            pGuiGraphics.drawString(Minecraft.getInstance().font, trimmedText, this.getX() + 5, this.getY() + 6, color);

            this.playButton.setX(this.getX() + this.entryWidth - 210);
            this.playButton.setY(this.getY());
            this.playButton.render(pGuiGraphics, mouseX, mouseY, pPartialTick);

            this.muteButton.setX(this.getX() + this.entryWidth - 185);
            this.muteButton.setY(this.getY());
            this.muteButton.render(pGuiGraphics, mouseX, mouseY, pPartialTick);

            this.volumeSlider.setX(this.getX() + this.entryWidth - 130);
            this.volumeSlider.setY(this.getY());
            this.volumeSlider.render(pGuiGraphics, mouseX, mouseY, pPartialTick);

            this.favoriteButton.setX(this.getX() + this.entryWidth - 25);
            this.favoriteButton.setY(this.getY());
            this.favoriteButton.render(pGuiGraphics, mouseX, mouseY, pPartialTick);
        }

        @Override
        public void visitWidgets(Consumer<AbstractWidget> consumer) {
            consumer.accept(this.playButton);
            consumer.accept(this.muteButton);
            consumer.accept(this.volumeSlider);
            consumer.accept(this.favoriteButton);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton);
        }

        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton);
        }
    }
}
