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
import net.minecraft.resources.Identifier;
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
    private String lastQuery = "";
    private SoundCategory lastCategory = SoundCategory.ALL;
    private String lastSelectedMod = "";
    private int lastViewMode = 0;
    private int lastFilterMode = 0;

    public SoundListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
        super(client, width, height, y, itemHeight);
    }

    public void loadEntries(int viewMode) {
        this.allEntries.clear();
        Collection<Identifier> soundIds = Minecraft.getInstance().getSoundManager().getAvailableSounds();

        if (viewMode == 1 || viewMode == 2) {
            List<String> rawIds = new ArrayList<>();
            for (Identifier id : soundIds) {
                rawIds.add(id.toString());
            }
            Collections.sort(rawIds);
            for (String id : rawIds) {
                this.allEntries.add(new SoundEntry(id, viewMode, this.getRowWidth(), this));
            }
        } else {
            Set<String> uniqueGroups = new HashSet<>();
            for (Identifier id : soundIds) {
                if (id.getNamespace().equals("minecraft")) {
                    uniqueGroups.add(SoundConfig.getSoundGroup(id.toString()));
                }
            }

            List<String> sortedGroups = new ArrayList<>(uniqueGroups);
            Collections.sort(sortedGroups);

            this.allEntries.add(new SoundEntry("#global:break", viewMode, this.getRowWidth(), this));
            this.allEntries.add(new SoundEntry("#global:place", viewMode, this.getRowWidth(), this));
            this.allEntries.add(new SoundEntry("#global:step", viewMode, this.getRowWidth(), this));
            this.allEntries.add(new SoundEntry("#global:hit", viewMode, this.getRowWidth(), this));
            this.allEntries.add(new SoundEntry("#global:hostile_hurt", viewMode, this.getRowWidth(), this));
            this.allEntries.add(new SoundEntry("#global:passive_hurt", viewMode, this.getRowWidth(), this));
            this.allEntries.add(new SoundEntry("#global:hostile_ambient", viewMode, this.getRowWidth(), this));
            this.allEntries.add(new SoundEntry("#global:passive_ambient", viewMode, this.getRowWidth(), this));

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
            for (var e : SoundConfig.SOUNDS.entrySet()) {
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
            return;
        }

        for (SoundEntry entry : this.allEntries) {

            if (filterMode == 1) {
                if (!SoundConfig.SOUNDS.containsKey(entry.soundId)) continue;
                SoundConfig.SoundSettings s = SoundConfig.SOUNDS.get(entry.soundId);
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
    }

    public void refilter() {
        filter(lastQuery, lastCategory, lastSelectedMod, lastViewMode, lastFilterMode);
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
        private final SoundSlider volumeSlider;
        private final Button favoriteButton;
        private final Button resetButton;
        private final Button textHoverButton;
        private SimpleSoundInstance playingInstance;
        private final int entryWidth;
        private final SoundListWidget parentList;

        public SoundEntry(String soundId, int viewMode, int entryWidth, SoundListWidget parentList) {
            this.soundId = soundId;
            this.entryWidth = entryWidth;
            this.parentList = parentList;

            boolean initialMuted = false;
            float initialVolume = 1.0f;
            boolean initialFavorite = false;

            if (SoundConfig.SOUNDS.containsKey(soundId)) {
                initialMuted = SoundConfig.SOUNDS.get(soundId).muted;
                initialVolume = SoundConfig.SOUNDS.get(soundId).volume;
                initialFavorite = SoundConfig.SOUNDS.get(soundId).favorite;
            }

            boolean isBasicMode = (viewMode == 0);
            Identifier parsedId = Identifier.tryParse(this.soundId);
            boolean isPlayable = !isBasicMode && parsedId != null && !this.soundId.startsWith("#global:");

            this.playButton = Button.builder(Component.literal("▶"), button -> {
                if (this.soundId.startsWith("#global:")) return;
                Identifier pid = Identifier.tryParse(this.soundId);
                if (pid != null) {
                    Minecraft client = Minecraft.getInstance();
                    if (this.playingInstance != null && client.getSoundManager().isActive(this.playingInstance)) {
                        client.getSoundManager().stop(this.playingInstance);
                        this.playingInstance = null;
                    } else {
                        this.playingInstance = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(pid), 1.0F, 1.0F);
                        client.getSoundManager().play(this.playingInstance);
                    }
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
                if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.SOUNDS.remove(this.soundId);
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
                    SoundConfig.SoundSettings s = SoundConfig.SOUNDS.computeIfAbsent(soundId, k -> new SoundConfig.SoundSettings());
                    s.volume = (float) (this.value * 2.0f);
                    if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.SOUNDS.remove(soundId);
                    SoundConfig.save();
                }
            };

            this.favoriteButton = Button.builder(Component.literal(initialFavorite ? "★" : "☆"), button -> {
                SoundConfig.SoundSettings s = SoundConfig.SOUNDS.computeIfAbsent(this.soundId, k -> new SoundConfig.SoundSettings());
                s.favorite = !s.favorite;
                button.setMessage(Component.literal(s.favorite ? "★" : "☆"));
                if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.SOUNDS.remove(this.soundId);
                SoundConfig.save();
                parentList.refilter();
            }).bounds(0, 0, 20, 20)
            .tooltip(Tooltip.create(Component.translatable("tooltip.soundcontrol.favorite")))
            .build();

            this.resetButton = Button.builder(Component.literal("⟲"), button -> {
                SoundConfig.SoundSettings current = SoundConfig.SOUNDS.get(this.soundId);
                boolean wasFavorite = current != null && current.favorite;
                if (wasFavorite) {
                    current.volume = 1.0f;
                    current.muted = false;
                } else {
                    SoundConfig.SOUNDS.remove(this.soundId);
                }
                SoundConfig.save();
                this.muteButton.setMessage(Component.translatable("text.soundcontrol.button.mute"));
                this.volumeSlider.resetValue(0.5);
                parentList.refilter();
            }).bounds(0, 0, 20, 20)
            .tooltip(Tooltip.create(Component.translatable("text.soundcontrol.tooltip.reset")))
            .build();

            this.textHoverButton = Button.builder(Component.literal(""), b -> {}).bounds(0, 0, 100, 20).build();
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
        public void renderContent(GuiGraphics pGuiGraphics, int mouseX, int mouseY, boolean pIsMouseOver, float pPartialTick) {
            boolean isPlaying = this.playingInstance != null && Minecraft.getInstance().getSoundManager().isActive(this.playingInstance);
            this.playButton.setMessage(Component.literal(isPlaying ? "■" : "▶"));

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
            String truncated = displayText;
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            if (font.width(truncated) > maxTextWidth) {
                while (font.width(truncated + "...") > maxTextWidth && truncated.length() > 1) {
                    truncated = truncated.substring(0, truncated.length() - 1);
                }
                truncated = truncated + "...";
            }

            int color = this.soundId.startsWith("#global:") ? 0xFFFFAA00 : 0xFFFFFFFF;
            pGuiGraphics.drawString(font, truncated, this.getX() + 2, this.getY() + 6, color);
            this.textHoverButton.setX(this.getX());
            this.textHoverButton.setY(this.getY());
            this.textHoverButton.setWidth(maxTextWidth);
            this.textHoverButton.setAlpha(0.0f);
            if (!truncated.equals(displayText)) {
                this.textHoverButton.setTooltip(Tooltip.create(Component.literal(displayText)));
            } else {
                this.textHoverButton.setTooltip(null);
            }
            this.textHoverButton.render(pGuiGraphics, mouseX, mouseY, pPartialTick);

            this.playButton.setX(this.getX() + this.entryWidth - 235);
            this.playButton.setY(this.getY());
            this.playButton.render(pGuiGraphics, mouseX, mouseY, pPartialTick);

            this.muteButton.setX(this.getX() + this.entryWidth - 210);
            this.muteButton.setY(this.getY());
            this.muteButton.render(pGuiGraphics, mouseX, mouseY, pPartialTick);

            this.volumeSlider.setX(this.getX() + this.entryWidth - 155);
            this.volumeSlider.setY(this.getY());
            this.volumeSlider.render(pGuiGraphics, mouseX, mouseY, pPartialTick);

            this.favoriteButton.setX(this.getX() + this.entryWidth - 50);
            this.favoriteButton.setY(this.getY());
            this.favoriteButton.render(pGuiGraphics, mouseX, mouseY, pPartialTick);

            this.resetButton.setX(this.getX() + this.entryWidth - 25);
            this.resetButton.setY(this.getY());
            this.resetButton.render(pGuiGraphics, mouseX, mouseY, pPartialTick);
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
