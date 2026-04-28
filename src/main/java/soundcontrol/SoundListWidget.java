package soundcontrol;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.Registries;
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
    private String lastQuery = "";
    private SoundCategory lastCategory = SoundCategory.ALL;
    private String lastSelectedMod = "";
    private int lastViewMode = 0;
    private int lastFilterMode = 0;

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
                this.allEntries.add(new SoundEntry(id, viewMode, this));
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

            this.allEntries.add(new SoundEntry("#global:break", viewMode, this));
            this.allEntries.add(new SoundEntry("#global:place", viewMode, this));
            this.allEntries.add(new SoundEntry("#global:step", viewMode, this));
            this.allEntries.add(new SoundEntry("#global:hit", viewMode, this));
            this.allEntries.add(new SoundEntry("#global:hostile_hurt", viewMode, this));
            this.allEntries.add(new SoundEntry("#global:passive_hurt", viewMode, this));
            this.allEntries.add(new SoundEntry("#global:hostile_ambient", viewMode, this));
            this.allEntries.add(new SoundEntry("#global:passive_ambient", viewMode, this));

            for (String group : sortedGroups) {
                this.allEntries.add(new SoundEntry(group, viewMode, this));
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
        this.lastQuery = query;
        this.lastCategory = category;
        this.lastSelectedMod = selectedMod;
        this.lastViewMode = viewMode;
        this.lastFilterMode = filterMode;
        this.clearEntries();
        String lowerQuery = query.toLowerCase();
        for (SoundEntry entry : this.allEntries) {

            if (filterMode == 1) {
                if (!SoundConfig.SOUNDS.containsKey(entry.soundId)) continue;
                SoundConfig.SoundSettings s = SoundConfig.SOUNDS.get(entry.soundId);
                if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f) continue;
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
                    matchCategory = entry.soundId.startsWith("minecraft:entity.") || entry.soundId.contains("_hurt");
                } else if (category == SoundCategory.BLOCKS) {
                    matchCategory = entry.soundId.startsWith("minecraft:block.");
                }
            }

            if (matchCategory && entry.soundId.toLowerCase().contains(lowerQuery)) {
                this.addEntry(entry);
            }
        }
        this.setScrollAmount(0);
    }

    public void refilter() {
        double scroll = this.getScrollAmount();
        filter(lastQuery, lastCategory, lastSelectedMod, lastViewMode, lastFilterMode);
        this.setScrollAmount(scroll);
    }

    public static class SoundEntry extends ElementListWidget.Entry<SoundEntry> {
        final String soundId;
        private final ButtonWidget playButton;
        private final ButtonWidget muteButton;
        private final SliderWidget volumeSlider;
        private final ButtonWidget favoriteButton;
        private final ButtonWidget resetButton;
        private final ButtonWidget textHoverButton;
        private PositionedSoundInstance playingInstance;
        private final SoundListWidget parentList;

        public SoundEntry(String soundId, int viewMode, SoundListWidget parentList) {
            this.soundId = soundId;
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
            boolean isPlayable = !isBasicMode && parsedId != null;

            ButtonWidget.Builder playBuilder = ButtonWidget.builder(Text.literal("▶"), button -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (this.playingInstance != null && client.getSoundManager().isPlaying(this.playingInstance)) {
                    client.getSoundManager().stop(this.playingInstance);
                    this.playingInstance = null;
                } else if (isPlayable) {
                    SoundEvent event = Registries.SOUND_EVENT.containsId(parsedId) ? Registries.SOUND_EVENT.get(parsedId) : SoundEvent.of(parsedId);
                    if (event != null) {
                        this.playingInstance = PositionedSoundInstance.master(event, 1.0F, 1.0F);
                        client.getSoundManager().play(this.playingInstance);
                    }
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
                if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.SOUNDS.remove(this.soundId);
                SoundConfig.save();
                parentList.refilter();
            }).dimensions(0, 0, 50, 20).build();


            this.favoriteButton = ButtonWidget.builder(Text.literal(initialFavorite ? "★" : "☆"), button -> {
                SoundConfig.SoundSettings s = SoundConfig.SOUNDS.computeIfAbsent(this.soundId, k -> new SoundConfig.SoundSettings());
                s.favorite = !s.favorite;
                button.setMessage(Text.literal(s.favorite ? "★" : "☆"));
                if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.SOUNDS.remove(this.soundId);
                SoundConfig.save();
                parentList.refilter();
            }).dimensions(0, 0, 20, 20).tooltip(Tooltip.of(Text.translatable("tooltip.soundcontrol.favorite"))).build();

            class VolumeSlider extends SliderWidget {
                public VolumeSlider(int x, int y, int width, int height, Text message, double value) {
                    super(x, y, width, height, message, value);
                }

                @Override
                protected void updateMessage() {
                    this.setMessage(Text.translatable("text.soundcontrol.slider.volume", (int)(this.value * 200)));
                }

                @Override
                protected void applyValue() {
                    SoundConfig.SoundSettings s = SoundConfig.SOUNDS.computeIfAbsent(soundId, k -> new SoundConfig.SoundSettings());
                    s.volume = (float) (this.value * 2.0f);
                    if (!s.muted && Math.abs(s.volume - 1.0f) < 0.01f && !s.favorite) SoundConfig.SOUNDS.remove(soundId);
                    SoundConfig.save();
                }

                public void resetValue() {
                    this.value = 0.5;
                    this.updateMessage();
                }
            }

            this.volumeSlider = new VolumeSlider(0, 0, 100, 20, Text.translatable("text.soundcontrol.slider.volume", (int)(initialVolume * 100)), initialVolume / 2.0f);

            this.resetButton = ButtonWidget.builder(Text.literal("⟲"), button -> {
                SoundConfig.SoundSettings current = SoundConfig.SOUNDS.get(this.soundId);
                boolean wasFavorite = current != null && current.favorite;
                if (wasFavorite) {
                    current.volume = 1.0f;
                    current.muted = false;
                } else {
                    SoundConfig.SOUNDS.remove(this.soundId);
                }
                SoundConfig.save();
                this.muteButton.setMessage(Text.translatable("text.soundcontrol.button.mute"));
                ((VolumeSlider) this.volumeSlider).resetValue();
                parentList.refilter();
            }).dimensions(0, 0, 20, 20).tooltip(Tooltip.of(Text.translatable("text.soundcontrol.tooltip.reset"))).build();

            this.textHoverButton = ButtonWidget.builder(Text.empty(), button -> {}).dimensions(0, 0, 100, 20).build();
            this.textHoverButton.setAlpha(0.0f);
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
            String trimmedText = MinecraftClient.getInstance().textRenderer.trimToWidth(textStr, entryWidth - 240);
            int color = this.soundId.startsWith("#global:") ? 0xFFAA00 : 0xFFFFFF;
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, trimmedText, x + 5, y + 6, color);

            if (!textStr.equals(trimmedText)) {
                this.textHoverButton.setTooltip(Tooltip.of(displayIdText));
            } else {
                this.textHoverButton.setTooltip(null);
            }
            this.textHoverButton.setX(x + 5);
            this.textHoverButton.setY(y);
            this.textHoverButton.setWidth(entryWidth - 240);
            this.textHoverButton.render(context, mouseX, mouseY, tickDelta);

            this.playButton.setX(x + entryWidth - 235);
            this.playButton.setY(y);
            this.playButton.render(context, mouseX, mouseY, tickDelta);

            this.muteButton.setX(x + entryWidth - 210);
            this.muteButton.setY(y);
            this.muteButton.render(context, mouseX, mouseY, tickDelta);

            this.volumeSlider.setX(x + entryWidth - 155);
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
        public List<? extends net.minecraft.client.gui.Element> children() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton, this.resetButton, this.textHoverButton);
        }

        @Override
        public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
            return List.of(this.playButton, this.muteButton, this.volumeSlider, this.favoriteButton, this.resetButton, this.textHoverButton);
        }
    }
}