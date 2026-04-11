package soundcontrol;

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

    public SoundListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
        super(client, width, height, y, itemHeight);
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
                this.allEntries.add(new SoundEntry(id, viewMode, this.getRowWidth()));
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

            if (filterMode == 1 && !SoundConfig.SOUNDS.containsKey(entry.soundId)) {
                continue;
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

    public static class SoundEntry extends ContainerObjectSelectionList.Entry<SoundEntry> {
        final String soundId;
        private final Button playButton;
        private final Button muteButton;
        private final AbstractSliderButton volumeSlider;
        private final Button favoriteButton;
        private final int entryWidth;
        private final int viewMode;
        private SimpleSoundInstance playingInstance;

        public SoundEntry(String soundId, int viewMode, int entryWidth) {
            this.soundId = soundId;
            this.entryWidth = entryWidth;
            this.viewMode = viewMode;

            boolean initialMuted = false;
            float initialVolume = 1.0f;
            boolean initialFavorite = false;

            if (SoundConfig.SOUNDS.containsKey(soundId)) {
                initialMuted = SoundConfig.SOUNDS.get(soundId).muted;
                initialVolume = SoundConfig.SOUNDS.get(soundId).volume;
                initialFavorite = SoundConfig.SOUNDS.get(soundId).favorite;
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
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = this.getX();
            int y = this.getY();

            // Draw sound name text
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

            // Truncate if too long
            int maxTextWidth = this.entryWidth - 220;
            String truncated = displayText;
            if (font.width(truncated) > maxTextWidth) {
                while (font.width(truncated + "...") > maxTextWidth && truncated.length() > 1) {
                    truncated = truncated.substring(0, truncated.length() - 1);
                }
                truncated = truncated + "...";
            }

            int color = 0xFFFFFFFF;
            if (this.soundId.startsWith("#global:")) {
                color = 0xFFFFAA00; // Gold (&6)
            }
            context.text(font, truncated, x + 2, y + 5, color);

            // Update play button icon if sound finished
            if (this.playingInstance != null && !Minecraft.getInstance().getSoundManager().isActive(this.playingInstance)) {
                this.playingInstance = null;
                this.playButton.setMessage(Component.literal("▶"));
            }

            // Draw buttons
            this.playButton.setX(x + this.entryWidth - 210);
            this.playButton.setY(y);
            this.playButton.extractRenderState(context, mouseX, mouseY, tickDelta);

            this.muteButton.setX(x + this.entryWidth - 185);
            this.muteButton.setY(y);
            this.muteButton.extractRenderState(context, mouseX, mouseY, tickDelta);

            this.volumeSlider.setX(x + this.entryWidth - 130);
            this.volumeSlider.setY(y);
            this.volumeSlider.extractRenderState(context, mouseX, mouseY, tickDelta);

            this.favoriteButton.setX(x + this.entryWidth - 25);
            this.favoriteButton.setY(y);
            this.favoriteButton.extractRenderState(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton);
        }

        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton);
        }
    }
}