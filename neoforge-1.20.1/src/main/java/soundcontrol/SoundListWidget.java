package soundcontrol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
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

public class SoundListWidget extends ObjectSelectionList<SoundListWidget.SoundEntry> {
    private final List<SoundEntry> allEntries = new ArrayList<>();
    private String lastQuery = "";
    private SoundCategory lastCategory = SoundCategory.ALL;
    private String lastSelectedMod = "";
    private int lastViewMode = 0;
    private int lastFilterMode = 0;

    public SoundListWidget(Minecraft client, int width, int height, int y0, int y1, int itemHeight) {
        super(client, width, height, y0, y1, itemHeight);
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
                this.allEntries.add(new SoundEntry(id, viewMode, this.getRowWidth(), this));
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

    @Override
    protected int getScrollbarPosition() {
        return this.width / 2 + this.getRowWidth() / 2 + 8;
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

            boolean matchCategory = (filterMode == 1);

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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
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

    public static class SoundEntry extends ObjectSelectionList.Entry<SoundEntry> {
        final String soundId;
        private final Button playButton;
        private final Button muteButton;
        private final SoundSlider volumeSlider;
        private final Button favoriteButton;
        private final Button resetButton;
        private String tooltipText = null;
        private SimpleSoundInstance playingInstance;
        private final int entryWidth;
        private final SoundListWidget parentList;
        private boolean sliderDragging = false;

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
            ResourceLocation parsedId = ResourceLocation.tryParse(this.soundId);
            boolean isPlayable = !isBasicMode && parsedId != null && !this.soundId.startsWith("#global:");

            this.playButton = Button.builder(Component.literal("▶"), button -> {
                if (this.soundId.startsWith("#global:")) return;
                ResourceLocation pid = ResourceLocation.tryParse(this.soundId);
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
        public void render(GuiGraphics pGuiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean pIsMouseOver, float pPartialTick) {
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
            pGuiGraphics.drawString(font, truncated, left + 5, top + 6, color);
            if (!truncated.equals(displayText) && mouseX >= left + 5 && mouseX <= left + 5 + maxTextWidth && mouseY >= top && mouseY <= top + height) {
                pGuiGraphics.renderTooltip(font, Component.literal(displayText), mouseX, mouseY);
            }

            this.playButton.setX(left + this.entryWidth - 235);
            this.playButton.setY(top);
            this.playButton.render(pGuiGraphics, mouseX, mouseY, pPartialTick);

            this.muteButton.setX(left + this.entryWidth - 210);
            this.muteButton.setY(top);
            this.muteButton.render(pGuiGraphics, mouseX, mouseY, pPartialTick);

            this.volumeSlider.setX(left + this.entryWidth - 155);
            this.volumeSlider.setY(top);
            this.volumeSlider.render(pGuiGraphics, mouseX, mouseY, pPartialTick);

            this.favoriteButton.setX(left + this.entryWidth - 50);
            this.favoriteButton.setY(top);
            this.favoriteButton.render(pGuiGraphics, mouseX, mouseY, pPartialTick);

            this.resetButton.setX(left + this.entryWidth - 25);
            this.resetButton.setY(top);
            this.resetButton.render(pGuiGraphics, mouseX, mouseY, pPartialTick);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
            // Copy sound ID to clipboard when clicking on the name text
            if (mouseButton == 0) {
                int left = this.playButton.getX() - this.entryWidth + 235;
                int top = this.playButton.getY();
                int maxTextWidth = this.entryWidth - 245;
                if (mouseX >= left + 5 && mouseX <= left + 5 + maxTextWidth && mouseY >= top && mouseY <= top + 20) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(this.soundId);
                    return true;
                }
            }
            if (this.playButton.mouseClicked(mouseX, mouseY, mouseButton)) return true;
            if (this.muteButton.mouseClicked(mouseX, mouseY, mouseButton)) return true;
            if (this.volumeSlider.mouseClicked(mouseX, mouseY, mouseButton)) {
                this.sliderDragging = true;
                return true;
            }
            if (this.favoriteButton.mouseClicked(mouseX, mouseY, mouseButton)) return true;
            if (this.resetButton.mouseClicked(mouseX, mouseY, mouseButton)) return true;
            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
            this.sliderDragging = false;
            this.volumeSlider.mouseReleased(mouseX, mouseY, mouseButton);
            return super.mouseReleased(mouseX, mouseY, mouseButton);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (this.sliderDragging) {
                return this.volumeSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
            return false;
        }

        @Override
        public Component getNarration() {
            return Component.empty();
        }
    }
}
