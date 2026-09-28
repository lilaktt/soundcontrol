package soundcontrol.gui;

import soundcontrol.SoundConfig;
import soundcontrol.AnchorWorldContext;
import soundcontrol.anchor.SoundAnchor;
import soundcontrol.anchor.SoundAnchorEditScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import java.util.*;

/** One picker for registry sounds and recent sounds, with identical controls on all ports. */
public class AllSoundsPickerScreen extends Screen {
    private final Screen parent;
    private final String worldKey = AnchorWorldContext.currentKey();
    private final SoundAnchor targetAnchor;
    private final boolean recentOnly;
    private TextFieldWidget searchBox;
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
        super(Text.translatable(recentOnly ? "text.soundcontrol.anchors.recent" : "text.soundcontrol.anchors.browse"));
        this.parent = parent; this.targetAnchor = anchor; this.recentOnly = recentOnly;
    }

    @Override protected void init() {
        allSoundIds.clear(); namespaces.clear(); namespaces.add("*");
        Set<String> ids = new TreeSet<>();
        if (recentOnly) {
            for (var sound : RecentSoundsPickerScreen.getRecentSounds()) ids.add(sound.soundId);
        } else {
            for (var id : MinecraftClient.getInstance().getSoundManager().getKeys()) ids.add(id.toString());
        }
        allSoundIds.addAll(ids);
        Set<String> mods = new TreeSet<>();
        for (String id : ids) mods.add(id.substring(0, id.indexOf(':')));
        namespaces.addAll(mods);
        namespaceIndex = Math.min(namespaceIndex, namespaces.size() - 1);
        int w = Math.min(360, this.width - 20), left = (this.width - w) / 2;
        this.searchBox = new TextFieldWidget(this.textRenderer, left, 23, w, 18, Text.translatable("text.soundcontrol.anchors.search"));
        this.searchBox.setMaxLength(256);
        this.searchBox.setText(query);
        this.addDrawableChild(this.searchBox);
        int bw = (w - 8) / 3;
        this.addDrawableChild(ButtonWidget.builder(modeLabel(), b -> {
            viewMode = (viewMode + 1) % 3; b.setMessage(modeLabel()); loadSounds();
        }).dimensions(left, 45, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(categoryLabel(), b -> {
            category = (category + 1) % 3; b.setMessage(categoryLabel()); loadSounds();
        }).dimensions(left + bw + 4, 45, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(filterLabel(), b -> {
            filterMode = (filterMode + 1) % 3; b.setMessage(filterLabel()); loadSounds();
        }).dimensions(left + 2 * (bw + 4), 45, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(modLabel(), b -> {
            namespaceIndex = (namespaceIndex + 1) % namespaces.size(); b.setMessage(modLabel()); loadSounds();
        }).dimensions(left, 69, w, 20).build());
        this.soundList = new SoundPickerList(this.client, this.width, Math.max(20, this.height - 128), 94, 24);
        this.addDrawableChild(this.soundList);
        this.searchBox.setChangedListener(value -> { query = value; loadSounds(); });
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> this.close())
                .dimensions(this.width / 2 - 60, this.height - 27, 120, 20).build());
        loadSounds();
    }

    private Text modeLabel() { return Text.translatable("text.soundcontrol.mode." + new String[]{"basic", "advanced", "mods"}[viewMode]); }
    private Text categoryLabel() { return Text.translatable("text.soundcontrol.category." + new String[]{"all", "mobs", "blocks"}[category]); }
    private Text filterLabel() { return Text.translatable("text.soundcontrol.filter." + new String[]{"all", "edited", "favorites"}[filterMode]); }
    private Text modLabel() { return namespaceIndex == 0 ? Text.translatable("text.soundcontrol.anchors.all_mods") : Text.literal(namespaces.get(namespaceIndex)); }

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

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
    }
    @Override public void tick() {
        super.tick();
        // Do not leave an editor holding another world's anchor after disconnect/reconnect.
        if (!java.util.Objects.equals(worldKey, AnchorWorldContext.currentKey())) {
            this.client.setScreen(null);
        }
    }

    @Override public void close() { this.client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    private static class SoundPickerList extends ElementListWidget<SoundPickerEntry> {
        SoundPickerList(MinecraftClient client, int width, int height, int y, int itemHeight) { super(client, width, height, y, itemHeight); }
        @Override public int getRowWidth() { return Math.min(360, this.width - 24); }
        @Override protected int getScrollbarX() { return this.width / 2 + getRowWidth() / 2 + 4; }
        void clear() { clearEntries(); }
        void add(SoundPickerEntry entry) { addEntry(entry); }
    }
    private static class SoundPickerEntry extends ElementListWidget.Entry<SoundPickerEntry> {
        private final String soundId;
        private final AllSoundsPickerScreen parent;
        private final ButtonWidget addButton;
        SoundPickerEntry(String soundId, AllSoundsPickerScreen parent) {
            this.soundId = soundId; this.parent = parent;
            this.addButton = ButtonWidget.builder(Text.literal("+"), b -> {
                if (parent.targetAnchor.getSoundOverrides().containsKey(soundId)) {
                    parent.targetAnchor.getSoundOverrides().remove(soundId);
                } else {
                    SoundConfig.SoundSettings setting = new SoundConfig.SoundSettings(); setting.muted = true;
                    parent.targetAnchor.getSoundOverrides().put(soundId, setting);
                }
                SoundConfig.saveSettings();
                if (parent.filterMode == 1) parent.loadSounds();
            }).dimensions(0, 0, 20, 20).build();
        }
        @Override public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int rowWidth = parent.soundList.getRowWidth();
            var font = MinecraftClient.getInstance().textRenderer;
            context.drawText(font, font.trimToWidth(soundId, rowWidth - 32), x + 2, y + 5, 0xFFFFFFFF, true);
            addButton.setMessage(Text.literal(parent.targetAnchor.getSoundOverrides().containsKey(soundId) ? "\u2715" : "+"));
            addButton.setX(x + rowWidth - 24); addButton.setY(y);
            addButton.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends net.minecraft.client.gui.Element> children() { return List.of(addButton); }
        @Override public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() { return List.of(addButton); }
    }
}
