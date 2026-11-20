package soundcontrol.gui;

import soundcontrol.AnchorWorldContext;
import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import soundcontrol.anchor.SoundAnchor;
import soundcontrol.anchor.SoundAnchorEditScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.*;

public class AllSoundsPickerScreen extends Screen {
    private final Screen parent;
    private final String worldKey = AnchorWorldContext.currentKey();
    private final SoundAnchor targetAnchor;
    private final boolean recentOnly;
    private EditBox searchBox;
    SoundPickerList soundList;
    private final List<String> allSoundIds = new ArrayList<>();
    private final List<String> namespaces = new ArrayList<>();
    private int viewMode = 1, filterMode, category, namespaceIndex;
    private String query = "";

    public AllSoundsPickerScreen(Screen parent, SoundAnchor anchor, boolean recentOnly) {
        super(Component.translatable(recentOnly ? "text.soundcontrol.anchors.recent" : "text.soundcontrol.anchors.browse"));
        this.parent = parent;
        this.targetAnchor = anchor;
        this.recentOnly = recentOnly;
    }

    @Override protected void init() {
        this.allSoundIds.clear();
        this.namespaces.clear();
        this.namespaces.add("*");
        Set<String> ids = new TreeSet<>();
        if (this.recentOnly) {
            for (var sound : RecentSoundsPickerScreen.getRecentSounds()) ids.add(sound.soundId);
        } else {
            ids.addAll(Compat.clientSoundIds());
        }
        this.allSoundIds.addAll(ids);
        Set<String> mods = new TreeSet<>();
        for (String id : ids) mods.add(id.substring(0, id.indexOf(':')));
        this.namespaces.addAll(mods);
        this.namespaceIndex = Math.min(this.namespaceIndex, this.namespaces.size() - 1);

        int w = Math.min(360, this.width - 20), left = (this.width - w) / 2;
        this.searchBox = new EditBox(this.font, left, 23, w, 18, Component.translatable("text.soundcontrol.anchors.search"));
        this.searchBox.setMaxLength(256);
        this.searchBox.setValue(this.query);
        this.addRenderableWidget(this.searchBox);
        int bw = (w - 8) / 3;
        this.addRenderableWidget(Button.builder(modeLabel(), b -> {
            this.viewMode = (this.viewMode + 1) % 3; b.setMessage(modeLabel()); loadSounds();
        }).bounds(left, 45, bw, 20).build());
        this.addRenderableWidget(Button.builder(categoryLabel(), b -> {
            this.category = (this.category + 1) % 3; b.setMessage(categoryLabel()); loadSounds();
        }).bounds(left + bw + 4, 45, bw, 20).build());
        this.addRenderableWidget(Button.builder(filterLabel(), b -> {
            this.filterMode = (this.filterMode + 1) % 3; b.setMessage(filterLabel()); loadSounds();
        }).bounds(left + 2 * (bw + 4), 45, bw, 20).build());
        this.addRenderableWidget(Button.builder(modLabel(), b -> {
            this.namespaceIndex = (this.namespaceIndex + 1) % this.namespaces.size(); b.setMessage(modLabel()); loadSounds();
        }).bounds(left, 69, w, 20).build());
        this.soundList = new SoundPickerList(this.minecraft, this.width, Math.max(20, this.height - 128), 94, 24);
        this.addRenderableWidget(this.soundList);
        this.searchBox.setResponder(value -> { this.query = value; loadSounds(); });
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 - 60, this.height - 27, 120, 20).build());
        loadSounds();
    }

    private Component modeLabel() { return Component.translatable("text.soundcontrol.mode." + new String[]{"basic", "advanced", "mods"}[this.viewMode]); }
    private Component categoryLabel() { return Component.translatable("text.soundcontrol.category." + new String[]{"all", "mobs", "blocks"}[this.category]); }
    private Component filterLabel() { return Component.translatable("text.soundcontrol.filter." + new String[]{"all", "edited", "favorites"}[this.filterMode]); }
    private Component modLabel() {
        return this.namespaceIndex == 0 ? Component.translatable("text.soundcontrol.anchors.all_mods")
            : Component.literal(Compat.modName(this.namespaces.get(this.namespaceIndex)));
    }

    void loadSounds() {
        if (this.soundList == null) return;
        this.soundList.clear();
        Set<String> entries = new TreeSet<>();
        if (this.viewMode == 0) {
            if (!this.recentOnly) entries.addAll(SoundListWidget.GLOBAL_SOUNDS);
            for (String id : this.allSoundIds) entries.add(SoundConfig.getSoundGroup(id));
        } else {
            entries.addAll(this.allSoundIds);
        }
        String search = this.query.toLowerCase(Locale.ROOT);
        for (String id : entries) {
            if (!id.toLowerCase(Locale.ROOT).contains(search)) continue;
            if (this.viewMode == 2 && (id.startsWith("minecraft:") || id.startsWith("#global:"))) continue;
            if (this.namespaceIndex > 0 && !id.startsWith(this.namespaces.get(this.namespaceIndex) + ":")) continue;
            if (this.category == 1 && !(id.contains("entity.") || id.contains("_hurt") || id.contains("_ambient"))) continue;
            if (this.category == 2 && !(id.contains("block.") || List.of("#global:break", "#global:place", "#global:step", "#global:hit").contains(id))) continue;
            if (this.filterMode == 1 && !this.targetAnchor.getSoundOverrides().containsKey(id)) continue;
            if (this.filterMode == 2 && (SoundConfig.getSound(id) == null || !SoundConfig.getSound(id).favorite)) continue;
            this.soundList.add(new SoundPickerEntry(id, this));
        }
    }

    private void toggle(String soundId) {
        if (this.targetAnchor.getSoundOverrides().containsKey(soundId)) {
            this.targetAnchor.getSoundOverrides().remove(soundId);
        } else {
            SoundConfig.SoundSettings setting = new SoundConfig.SoundSettings();
            setting.muted = true;
            this.targetAnchor.getSoundOverrides().put(soundId, setting);
        }
        SoundConfig.saveSettings();
        if (this.filterMode == 1) loadSounds();
    }

    @Override public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, SoundControlScreen.BACKDROP);
        context.fill(0, 94, this.width, 94 + Math.max(20, this.height - 128), SoundControlScreen.LIST_PANEL);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
    }

    @Override public void tick() {
        super.tick();
        if (!java.util.Objects.equals(this.worldKey, AnchorWorldContext.currentKey())) Compat.setScreen(null);
    }

    @Override public void onClose() {
        if (this.parent instanceof SoundAnchorEditScreen editor) editor.loadSounds();
        Compat.setScreen(this.parent);
    }

    @Override public boolean isPauseScreen() { return false; }

    static class SoundPickerList extends ContainerObjectSelectionList<SoundPickerEntry> {
        SoundPickerList(Minecraft client, int width, int height, int y, int itemHeight) { super(client, width, height, y, y + height, itemHeight); this.setRenderBackground(false); this.setRenderTopAndBottom(false); }
        @Override public int getRowWidth() { return Math.min(360, this.width - 24); }
        @Override protected int getScrollbarPosition() { return this.getRowLeft() + getRowWidth() + 4; }
        void clear() { this.clearEntries(); }
        void add(SoundPickerEntry entry) { this.addEntry(entry); }
    }

    static class SoundPickerEntry extends ContainerObjectSelectionList.Entry<SoundPickerEntry> {
        private final String soundId;
        private final AllSoundsPickerScreen parent;
        private final Button addButton;

        SoundPickerEntry(String soundId, AllSoundsPickerScreen parent) {
            this.soundId = soundId;
            this.parent = parent;
            this.addButton = Button.builder(Component.literal("+"), b -> parent.toggle(soundId)).bounds(0, 0, 20, 20).build();
        }

        @Override public void render(GuiGraphics context, int index, int y, int x, int entryW, int entryH, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int rowWidth = this.parent.soundList.getRowWidth();
            var font = Minecraft.getInstance().font;
            String label = SoundListWidget.displayName(this.soundId);
            context.drawString(font, font.plainSubstrByWidth(label, rowWidth - 32), x + 2, y + 6,
                this.soundId.startsWith("#global:") ? 0xFFFFAA00 : 0xFFFFFFFF);
            this.addButton.setMessage(Component.literal(this.parent.targetAnchor.getSoundOverrides().containsKey(this.soundId) ? "✕" : "+"));
            this.addButton.setX(x + rowWidth - 24);
            this.addButton.setY(y);
            this.addButton.render(context, mouseX, mouseY, tickDelta);
        }

        @Override public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() { return List.of(this.addButton); }
        @Override public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() { return List.of(this.addButton); }
    }
}
