package soundcontrol;

import soundcontrol.SoundConfig;
import soundcontrol.AnchorWorldContext;
import soundcontrol.SoundAnchor;
import soundcontrol.SoundAnchorEditScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import java.util.*;

/** One picker for registry sounds and recent sounds, with identical controls on all ports. */
public class AllSoundsPickerScreen extends Screen {
    private final Screen parent;
    private final String worldKey = AnchorWorldContext.currentKey();
    private final SoundAnchor targetAnchor;
    private final boolean recentOnly;
    private EditBox searchBox;
    private SoundPickerList soundList;
    private final List<String> allSoundIds = new ArrayList<>();
    private final List<String> namespaces = new ArrayList<>();
    private int viewMode = 1, filterMode, category, namespaceIndex;
    private String query = "";
    private static final String[] GLOBAL_ENTRIES = {
        "#global:break", "#global:place", "#global:step", "#global:hit",
        "#global:hostile_hurt", "#global:passive_hurt", "#global:hostile_ambient", "#global:passive_ambient"
    };

    public AllSoundsPickerScreen(Screen parent, SoundAnchor anchor) { this(parent, anchor, false); }
    public AllSoundsPickerScreen(Screen parent, SoundAnchor anchor, boolean recentOnly) {
        super(Component.translatable(recentOnly ? "text.soundcontrol.anchors.recent" : "text.soundcontrol.anchors.browse"));
        this.parent = parent; this.targetAnchor = anchor; this.recentOnly = recentOnly;
    }

    @Override protected void init() {
        allSoundIds.clear(); namespaces.clear(); namespaces.add("*");
        Set<String> ids = new TreeSet<>();
        if (recentOnly) {
            for (var sound : RecentSoundsPickerScreen.getRecentSounds()) ids.add(sound.soundId);
        } else {
            for (var id : Minecraft.getInstance().getSoundManager().getAvailableSounds()) ids.add(id.toString());
        }
        allSoundIds.addAll(ids);
        Set<String> mods = new TreeSet<>();
        for (String id : ids) mods.add(id.substring(0, id.indexOf(':')));
        namespaces.addAll(mods);
        namespaceIndex = Math.min(namespaceIndex, namespaces.size() - 1);
        int w = Math.min(360, this.width - 20), left = (this.width - w) / 2;
        this.searchBox = new EditBox(this.font, left, 23, w, 18, Component.translatable("text.soundcontrol.anchors.search"));
        this.searchBox.setMaxLength(256);
        this.searchBox.setValue(query);
        this.addRenderableWidget(this.searchBox);
        int bw = (w - 8) / 3;
        this.addRenderableWidget(Button.builder(modeLabel(), b -> {
            viewMode = (viewMode + 1) % 3; b.setMessage(modeLabel()); loadSounds();
        }).bounds(left, 45, bw, 20).build());
        this.addRenderableWidget(Button.builder(categoryLabel(), b -> {
            category = (category + 1) % 3; b.setMessage(categoryLabel()); loadSounds();
        }).bounds(left + bw + 4, 45, bw, 20).build());
        this.addRenderableWidget(Button.builder(filterLabel(), b -> {
            filterMode = (filterMode + 1) % 3; b.setMessage(filterLabel()); loadSounds();
        }).bounds(left + 2 * (bw + 4), 45, bw, 20).build());
        this.addRenderableWidget(Button.builder(modLabel(), b -> {
            namespaceIndex = (namespaceIndex + 1) % namespaces.size(); b.setMessage(modLabel()); loadSounds();
        }).bounds(left, 69, w, 20).build());
        this.soundList = new SoundPickerList(this.minecraft, this.width, Math.max(20, this.height - 128), 94, 24);
        this.addRenderableWidget(this.soundList);
        this.searchBox.setResponder(value -> { query = value; loadSounds(); });
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 - 60, this.height - 27, 120, 20).build());
        loadSounds();
    }

    private Component modeLabel() { return Component.translatable("text.soundcontrol.mode." + new String[]{"basic", "advanced", "mods"}[viewMode]); }
    private Component categoryLabel() { return Component.translatable("text.soundcontrol.category." + new String[]{"all", "mobs", "blocks"}[category]); }
    private Component filterLabel() { return Component.translatable("text.soundcontrol.filter." + new String[]{"all", "edited", "favorites"}[filterMode]); }
    private Component modLabel() { return namespaceIndex == 0 ? Component.translatable("text.soundcontrol.anchors.all_mods") : Component.literal(namespaces.get(namespaceIndex)); }

    private void loadSounds() {
        if (soundList == null) return;
        soundList.clear();
        Set<String> entries = new TreeSet<>();
        if (viewMode == 0) {
            if (!recentOnly) entries.addAll(List.of(GLOBAL_ENTRIES));
            for (String id : allSoundIds) entries.add(SoundConfig.getSoundGroup(id));
        } else entries.addAll(allSoundIds);
        String search = query.toLowerCase(Locale.ROOT);
        for (String id : entries) {
            if (!id.toLowerCase(Locale.ROOT).contains(search)) continue;
            if (viewMode == 2 && id.startsWith("minecraft:")) continue;
            if (namespaceIndex > 0 && !id.startsWith(namespaces.get(namespaceIndex) + ":")) continue;
            if (category == 1 && !(id.contains("entity.") || id.contains("_hurt") || id.contains("_ambient") || id.startsWith("#global:hostile") || id.startsWith("#global:passive"))) continue;
            if (category == 2 && !(id.contains("block.") || List.of("#global:break", "#global:place", "#global:step", "#global:hit").contains(id))) continue;
            if (filterMode == 1 && !targetAnchor.getSoundOverrides().containsKey(id)) continue;
            if (filterMode == 2 && (SoundConfig.getSound(id) == null || !SoundConfig.getSound(id).favorite)) continue;
            soundList.add(new SoundPickerEntry(id, this));
        }
    }

    @Override public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
    }
    @Override public void tick() {
        super.tick();
        // Do not leave an editor holding another world's anchor after disconnect/reconnect.
        if (!java.util.Objects.equals(worldKey, AnchorWorldContext.currentKey())) {
            this.minecraft.setScreen(null);
        }
    }

    @Override public void onClose() { this.minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }

    private static class SoundPickerList extends ContainerObjectSelectionList<SoundPickerEntry> {
        SoundPickerList(Minecraft client, int width, int height, int y, int itemHeight) { super(client, width, height, y, itemHeight); }
        @Override public int getRowWidth() { return Math.min(360, this.width - 24); }
        @Override protected int getScrollbarPosition() { return this.width / 2 + getRowWidth() / 2 + 4; }
        void clear() { clearEntries(); }
        void add(SoundPickerEntry entry) { addEntry(entry); }
    }
    private static class SoundPickerEntry extends ContainerObjectSelectionList.Entry<SoundPickerEntry> {
        private final String soundId;
        private final AllSoundsPickerScreen parent;
        private final Button addButton;
        SoundPickerEntry(String soundId, AllSoundsPickerScreen parent) {
            this.soundId = soundId; this.parent = parent;
            this.addButton = Button.builder(Component.literal("+"), b -> {
                if (parent.targetAnchor.getSoundOverrides().containsKey(soundId)) {
                    parent.targetAnchor.getSoundOverrides().remove(soundId);
                } else {
                    SoundConfig.SoundSettings setting = new SoundConfig.SoundSettings(); setting.muted = true;
                    parent.targetAnchor.getSoundOverrides().put(soundId, setting);
                }
                SoundConfig.saveSettings();
                if (parent.filterMode == 1) parent.loadSounds();
            }).bounds(0, 0, 20, 20).build();
        }
        @Override public void render(GuiGraphics context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int rowWidth = parent.soundList.getRowWidth();
            var font = Minecraft.getInstance().font;
            context.drawString(font, font.plainSubstrByWidth(soundId, rowWidth - 32), x + 2, y + 5, 0xFFFFFFFF);
            addButton.setMessage(Component.literal(parent.targetAnchor.getSoundOverrides().containsKey(soundId) ? "\u2715" : "+"));
            addButton.setX(x + rowWidth - 24); addButton.setY(y);
            addButton.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() { return List.of(addButton); }
        @Override public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() { return List.of(addButton); }
    }
}
