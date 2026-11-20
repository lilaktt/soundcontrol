package soundcontrol.gui;

import soundcontrol.AnchorWorldContext;
import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import soundcontrol.anchor.SoundAnchor;
import soundcontrol.anchor.SoundAnchorEditScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.*;

public class AllSoundsPickerScreen extends Screen {
    private final Screen parent;
    private final String worldKey = AnchorWorldContext.currentKey();
    private final SoundAnchor targetAnchor;
    private final boolean recentOnly;
    private TextFieldWidget searchBox;
    SoundPickerList soundList;
    private final List<String> allSoundIds = new ArrayList<>();
    private final List<String> namespaces = new ArrayList<>();
    private int viewMode = 1, filterMode, category, namespaceIndex;
    private String query = "";

    public AllSoundsPickerScreen(Screen parent, SoundAnchor anchor, boolean recentOnly) {
        super(Text.translatable(recentOnly ? "text.soundcontrol.anchors.recent" : "text.soundcontrol.anchors.browse"));
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
        this.searchBox = new TextFieldWidget(this.textRenderer, left, 23, w, 18, Text.translatable("text.soundcontrol.anchors.search"));
        this.searchBox.setMaxLength(256);
        this.searchBox.setText(this.query);
        this.addDrawableChild(this.searchBox);
        int bw = (w - 8) / 3;
        this.addDrawableChild(ButtonWidget.builder(modeLabel(), b -> {
            this.viewMode = (this.viewMode + 1) % 3; b.setMessage(modeLabel()); loadSounds();
        }).dimensions(left, 45, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(categoryLabel(), b -> {
            this.category = (this.category + 1) % 3; b.setMessage(categoryLabel()); loadSounds();
        }).dimensions(left + bw + 4, 45, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(filterLabel(), b -> {
            this.filterMode = (this.filterMode + 1) % 3; b.setMessage(filterLabel()); loadSounds();
        }).dimensions(left + 2 * (bw + 4), 45, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(modLabel(), b -> {
            this.namespaceIndex = (this.namespaceIndex + 1) % this.namespaces.size(); b.setMessage(modLabel()); loadSounds();
        }).dimensions(left, 69, w, 20).build());
        this.soundList = new SoundPickerList(this.client, this.width, Math.max(20, this.height - 128), 94, 24);
        this.addDrawableChild(this.soundList);
        this.searchBox.setChangedListener(value -> { this.query = value; loadSounds(); });
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> this.close())
                .dimensions(this.width / 2 - 60, this.height - 27, 120, 20).build());
        loadSounds();
    }

    private Text modeLabel() { return Text.translatable("text.soundcontrol.mode." + new String[]{"basic", "advanced", "mods"}[this.viewMode]); }
    private Text categoryLabel() { return Text.translatable("text.soundcontrol.category." + new String[]{"all", "mobs", "blocks"}[this.category]); }
    private Text filterLabel() { return Text.translatable("text.soundcontrol.filter." + new String[]{"all", "edited", "favorites"}[this.filterMode]); }
    private Text modLabel() {
        return this.namespaceIndex == 0 ? Text.translatable("text.soundcontrol.anchors.all_mods")
            : Text.literal(Compat.modName(this.namespaces.get(this.namespaceIndex)));
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

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, SoundControlScreen.BACKDROP);
        context.fill(0, 94, this.width, 94 + Math.max(20, this.height - 128), SoundControlScreen.LIST_PANEL);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
    }

    @Override public void tick() {
        super.tick();
        if (!java.util.Objects.equals(this.worldKey, AnchorWorldContext.currentKey())) Compat.setScreen(null);
    }

    @Override public void close() {
        if (this.parent instanceof SoundAnchorEditScreen editor) editor.loadSounds();
        Compat.setScreen(this.parent);
    }

    @Override public boolean shouldPause() { return false; }

    static class SoundPickerList extends ElementListWidget<SoundPickerEntry> {
        SoundPickerList(MinecraftClient client, int width, int height, int y, int itemHeight) { super(client, width, height, y, itemHeight); }
        @Override public int getRowWidth() { return Math.min(360, this.width - 24); }
        @Override protected int getScrollbarX() { return this.getRowLeft() + getRowWidth() + 4; }
        @Override protected void drawMenuListBackground(DrawContext context) {}
        @Override protected void drawHeaderAndFooterSeparators(DrawContext context) {}
        void clear() { this.clearEntries(); }
        void add(SoundPickerEntry entry) { this.addEntry(entry); }
    }

    static class SoundPickerEntry extends ElementListWidget.Entry<SoundPickerEntry> {
        private final String soundId;
        private final AllSoundsPickerScreen parent;
        private final ButtonWidget addButton;

        SoundPickerEntry(String soundId, AllSoundsPickerScreen parent) {
            this.soundId = soundId;
            this.parent = parent;
            this.addButton = ButtonWidget.builder(Text.literal("+"), b -> parent.toggle(soundId)).dimensions(0, 0, 20, 20).build();
        }

        @Override public void render(DrawContext context, int index, int y, int x, int entryW, int entryH, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int rowWidth = this.parent.soundList.getRowWidth();
            var font = MinecraftClient.getInstance().textRenderer;
            String label = SoundListWidget.displayName(this.soundId);
            context.drawText(font, font.trimToWidth(label, rowWidth - 32), x + 2, y + 6,
                this.soundId.startsWith("#global:") ? 0xFFFFAA00 : 0xFFFFFFFF, true);
            this.addButton.setMessage(Text.literal(this.parent.targetAnchor.getSoundOverrides().containsKey(this.soundId) ? "✕" : "+"));
            this.addButton.setX(x + rowWidth - 24);
            this.addButton.setY(y);
            this.addButton.render(context, mouseX, mouseY, tickDelta);
        }

        @Override public List<? extends net.minecraft.client.gui.Element> children() { return List.of(this.addButton); }
        @Override public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() { return List.of(this.addButton); }
    }
}
