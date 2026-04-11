package soundcontrol;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SoundListWidget extends ElementListWidget<SoundListWidget.SoundEntry> {
    private final List<SoundEntry> allEntries = new ArrayList<>();

    public SoundListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
        super(client, width, height, y, itemHeight);
    }

    public void loadEntries(int viewMode) {
        this.allEntries.clear();
        Collection<Identifier> soundIds = MinecraftClient.getInstance().getSoundManager().getKeys();

        if (viewMode == 1 || viewMode == 2) {
            List<String> rawIds = new ArrayList<>();
            for (Identifier id : soundIds) {
                rawIds.add(id.toString());
            }
            Collections.sort(rawIds);
            for (String id : rawIds) {
                this.allEntries.add(new SoundEntry(id, viewMode));
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

            this.allEntries.add(new SoundEntry("#global:break", viewMode));
            this.allEntries.add(new SoundEntry("#global:place", viewMode));
            this.allEntries.add(new SoundEntry("#global:step", viewMode));
            this.allEntries.add(new SoundEntry("#global:hit", viewMode));
            this.allEntries.add(new SoundEntry("#global:hostile_hurt", viewMode));
            this.allEntries.add(new SoundEntry("#global:passive_hurt", viewMode));
            this.allEntries.add(new SoundEntry("#global:hostile_ambient", viewMode));
            this.allEntries.add(new SoundEntry("#global:passive_ambient", viewMode));

            for (String group : sortedGroups) {
                this.allEntries.add(new SoundEntry(group, viewMode));
            }
        }
    }

    @Override
    public int getRowWidth() {
        return 380;
    }

    @Override
    protected int getScrollbarX() {
        return this.width / 2 + 195;
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

    public static class SoundEntry extends ElementListWidget.Entry<SoundEntry> {
        final String soundId;
        private final ButtonWidget playButton;
        private final ButtonWidget muteButton;
        private final SliderWidget volumeSlider;
        private final ButtonWidget favoriteButton;
        private PositionedSoundInstance playingInstance;

        public SoundEntry(String soundId, int viewMode) {
            this.soundId = soundId;

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
            boolean isPlayable = !isBasicMode && parsedId != null;

            ButtonWidget.Builder playBuilder = ButtonWidget.builder(Text.literal("▶"), button -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (this.playingInstance != null && client.getSoundManager().isPlaying(this.playingInstance)) {
                    client.getSoundManager().stop(this.playingInstance);
                    this.playingInstance = null;
                } else if (isPlayable) {
                    SoundEvent event = SoundEvent.of(parsedId);
                    this.playingInstance = PositionedSoundInstance.master(event, 1.0F, 1.0F);
                    client.getSoundManager().play(this.playingInstance);
                }
            }).dimensions(0, 0, 20, 20);

            if (isBasicMode) {
                playBuilder.tooltip(Tooltip.of(Text.translatable("text.soundcontrol.tooltip.advanced_only")));
            }

            this.playButton = playBuilder.build();
            this.playButton.active = isPlayable;

            this.muteButton = ButtonWidget.builder(Text.translatable(initialMuted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute"), button -> {
                SoundConfig.SoundSettings s = SoundConfig.SOUNDS.computeIfAbsent(this.soundId, k -> new SoundConfig.SoundSettings());
                s.muted = !s.muted;
                button.setMessage(Text.translatable(s.muted ? "text.soundcontrol.button.unmute" : "text.soundcontrol.button.mute"));
                SoundConfig.save();
            }).dimensions(0, 0, 50, 20).build();

            this.volumeSlider = new SliderWidget(0, 0, 100, 20, Text.translatable("text.soundcontrol.slider.volume", (int)(initialVolume * 100)), initialVolume / 2.0f) {
                @Override
                protected void updateMessage() {
                    this.setMessage(Text.translatable("text.soundcontrol.slider.volume", (int)(this.value * 200)));
                }

                @Override
                protected void applyValue() {
                    SoundConfig.SoundSettings s = SoundConfig.SOUNDS.computeIfAbsent(soundId, k -> new SoundConfig.SoundSettings());
                    s.volume = (float) (this.value * 2.0f);
                    SoundConfig.save();
                }
            };

            this.favoriteButton = ButtonWidget.builder(Text.literal(initialFavorite ? "★" : "☆"), button -> {
                SoundConfig.SoundSettings s = SoundConfig.SOUNDS.computeIfAbsent(this.soundId, k -> new SoundConfig.SoundSettings());
                s.favorite = !s.favorite;
                button.setMessage(Text.literal(s.favorite ? "★" : "☆"));
                SoundConfig.save();
            }).dimensions(0, 0, 20, 20).build();
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            boolean isPlaying = this.playingInstance != null && MinecraftClient.getInstance().getSoundManager().isPlaying(this.playingInstance);
            this.playButton.setMessage(Text.literal(isPlaying ? "■" : "▶"));

            Text displayIdText = Text.literal(this.soundId);

            if (this.soundId.equals("#global:break")) displayIdText = Text.translatable("text.soundcontrol.global.break");
            if (this.soundId.equals("#global:place")) displayIdText = Text.translatable("text.soundcontrol.global.place");
            if (this.soundId.equals("#global:step")) displayIdText = Text.translatable("text.soundcontrol.global.step");
            if (this.soundId.equals("#global:hit")) displayIdText = Text.translatable("text.soundcontrol.global.hit");
            if (this.soundId.equals("#global:hostile_hurt")) displayIdText = Text.translatable("text.soundcontrol.global.hostile_hurt");
            if (this.soundId.equals("#global:passive_hurt")) displayIdText = Text.translatable("text.soundcontrol.global.passive_hurt");
            if (this.soundId.equals("#global:hostile_ambient")) displayIdText = Text.translatable("text.soundcontrol.global.hostile_ambient");
            if (this.soundId.equals("#global:passive_ambient")) displayIdText = Text.translatable("text.soundcontrol.global.passive_ambient");

            String textStr = displayIdText.getString();
            String trimmedText = MinecraftClient.getInstance().textRenderer.trimToWidth(textStr, entryWidth - 215);
            int color = this.soundId.startsWith("#global:") ? 0xFFAA00 : 0xFFFFFF;
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, trimmedText, x + 5, y + 6, color);

            this.playButton.setX(x + entryWidth - 210);
            this.playButton.setY(y);
            this.playButton.render(context, mouseX, mouseY, tickDelta);

            this.muteButton.setX(x + entryWidth - 185);
            this.muteButton.setY(y);
            this.muteButton.render(context, mouseX, mouseY, tickDelta);

            this.volumeSlider.setX(x + entryWidth - 130);
            this.volumeSlider.setY(y);
            this.volumeSlider.render(context, mouseX, mouseY, tickDelta);

            this.favoriteButton.setX(x + entryWidth - 25);
            this.favoriteButton.setY(y);
            this.favoriteButton.render(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public List<? extends net.minecraft.client.gui.Element> children() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton);
        }

        @Override
        public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton);
        }
    }
}