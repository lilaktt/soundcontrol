package soundcontrol.gui;

import soundcontrol.anchor.SoundAnchorEditScreen;
import soundcontrol.SoundCategory;
import soundcontrol.SoundConfig;
import soundcontrol.SoundControl;
import soundcontrol.anchor.SoundAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class AllSoundsPickerScreen extends Screen {
    private final Screen parent;
    private final SoundAnchor targetAnchor;
    private EditBox searchBox;
    private SoundPickerList soundList;
    private PickerModList modList;
    private List<String> allSoundIds;
    private SoundCategory currentCategory = SoundCategory.ALL;
    private int viewMode = 1; 
    private int filterMode = 0; 
    private String selectedMod = "";

    private static final String[] GLOBAL_ENTRIES = {
        "#global:break", "#global:place", "#global:step", "#global:hit",
        "#global:hostile_hurt", "#global:passive_hurt",
        "#global:hostile_ambient", "#global:passive_ambient"
    };

    public AllSoundsPickerScreen(Screen parent, SoundAnchor targetAnchor) {
        super(Component.translatable("text.soundcontrol.anchors.browse"));
        this.parent = parent;
        this.targetAnchor = targetAnchor;
    }

    private Component getFilterText() {
        if (this.filterMode == 1) return Component.translatable("text.soundcontrol.filter.edited");
        if (this.filterMode == 2) return Component.translatable("text.soundcontrol.filter.favorites");
        return Component.translatable("text.soundcontrol.filter.all");
    }

    @Override
    protected void init() {
        allSoundIds = new ArrayList<>();
        Set<String> uniqueGroups = new HashSet<>();
        for (var id : Minecraft.getInstance().getSoundManager().getAvailableSounds()) {
            allSoundIds.add(id.toString());
            if (id.getNamespace().equals("minecraft")) {
                uniqueGroups.add(SoundConfig.getSoundGroup(id.toString()));
            }
        }
        Collections.sort(allSoundIds);

        this.searchBox = new EditBox(this.font, this.width / 2 - 140, 22, 180, 20, Component.literal(""));
        this.searchBox.setResponder(this::onSearch);
        this.addWidget(this.searchBox);
        this.addRenderableWidget(this.searchBox);

        
        this.addRenderableWidget(Button.builder(getFilterText(), button -> {
            this.filterMode = (this.filterMode + 1) % 3;
            button.setMessage(getFilterText());
            loadSounds(this.searchBox.getValue());
        }).bounds(this.width / 2 + 50, 22, 100, 20).build());

        
        int buttonWidth = 60;
        int startX = this.width / 2 - (buttonWidth * 3 + 10) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.all"), b -> { this.currentCategory = SoundCategory.ALL; loadSounds(this.searchBox.getValue()); }).bounds(startX, 46, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.mobs"), b -> { this.currentCategory = SoundCategory.MOBS; loadSounds(this.searchBox.getValue()); }).bounds(startX + buttonWidth + 5, 46, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.blocks"), b -> { this.currentCategory = SoundCategory.BLOCKS; loadSounds(this.searchBox.getValue()); }).bounds(startX + (buttonWidth + 5) * 2, 46, buttonWidth, 20).build());

        this.modList = new PickerModList(this.minecraft, 120, this.height - 116, 72, 15, this);
        this.modList.setX(this.width - 120);
        this.addRenderableWidget(this.modList);

        this.soundList = new SoundPickerList(this.minecraft, this.width, this.height - 116, 72, 22);
        this.addRenderableWidget(this.soundList);

        if (this.viewMode == 2) {
            this.modList.active = true;
            this.modList.visible = true;
            this.soundList.setWidth(this.width - 120);
        } else {
            this.modList.active = false;
            this.modList.visible = false;
        }

        
        String initialModeKey = this.viewMode == 0 ? "basic" : (this.viewMode == 1 ? "advanced" : "mods");
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.mode." + initialModeKey), button -> {
            this.viewMode = (this.viewMode + 1) % 3;
            String modeKey = this.viewMode == 0 ? "basic" : (this.viewMode == 1 ? "advanced" : "mods");
            button.setMessage(Component.translatable("text.soundcontrol.mode." + modeKey));
            if (this.viewMode == 2) {
                this.modList.active = true;
                this.modList.visible = true;
                this.soundList.setWidth(this.width - 120);
            } else {
                this.modList.active = false;
                this.modList.visible = false;
                this.soundList.setWidth(this.width);
            }
            loadSounds(this.searchBox.getValue());
        }).bounds(this.width / 2 - 160, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());

        loadSounds("");
        this.setInitialFocus(this.searchBox);
    }

    public void setSelectedMod(String modId) {
        double scroll = this.soundList.scrollAmount();
        this.selectedMod = modId;
        loadSounds(this.searchBox.getValue());
        this.soundList.setScrollAmount(scroll);
    }

    public String getSelectedMod() {
        return this.selectedMod;
    }

    private void loadSounds(String query) {
        this.soundList.clear();
        String lowerQuery = query.toLowerCase();

        if (viewMode == 0) {
            
            for (String global : GLOBAL_ENTRIES) {
                String displayName = getGlobalDisplayName(global);
                if (!displayName.toLowerCase().contains(lowerQuery)) continue;
                if (!matchesCategory(global)) continue;
                if (!matchesFilter(global)) continue;
                this.soundList.add(new SoundPickerEntry(global, this));
            }

            Set<String> uniqueGroups = new HashSet<>();
            for (String id : allSoundIds) {
                if (id.startsWith("minecraft:")) {
                    uniqueGroups.add(SoundConfig.getSoundGroup(id));
                }
            }
            List<String> sortedGroups = new ArrayList<>(uniqueGroups);
            Collections.sort(sortedGroups);
            for (String group : sortedGroups) {
                if (!group.toLowerCase().contains(lowerQuery)) continue;
                if (!matchesCategory(group)) continue;
                if (!matchesFilter(group)) continue;
                this.soundList.add(new SoundPickerEntry(group, this));
            }
        } else if (viewMode == 2) {
            
            if (selectedMod.isEmpty()) selectedMod = "all";
            for (String id : allSoundIds) {
                if (selectedMod.equals("all")) {
                    if (id.startsWith("minecraft:")) continue;
                } else {
                    if (!id.startsWith(selectedMod + ":")) continue;
                }
                if (!id.toLowerCase().contains(lowerQuery)) continue;
                if (!matchesCategory(id)) continue;
                if (!matchesFilter(id)) continue;
                this.soundList.add(new SoundPickerEntry(id, this));
            }
        } else {
            
            for (String id : allSoundIds) {
                if (!id.toLowerCase().contains(lowerQuery)) continue;
                if (!matchesCategory(id)) continue;
                if (!matchesFilter(id)) continue;
                this.soundList.add(new SoundPickerEntry(id, this));
            }
        }
    }

    private boolean matchesCategory(String id) {
        if (viewMode == 2) return true; 
        if (currentCategory == SoundCategory.MOBS) {
            return id.contains("entity.") || id.contains("_hurt") || id.contains("_ambient") || id.startsWith("#global:hostile") || id.startsWith("#global:passive");
        } else if (currentCategory == SoundCategory.BLOCKS) {
            return id.contains("block.") || id.equals("#global:break") || id.equals("#global:place") || id.equals("#global:step") || id.equals("#global:hit");
        }
        return true;
    }

    private boolean matchesFilter(String id) {
        if (filterMode == 1) {
            
            return targetAnchor.getSoundOverrides().containsKey(id);
        }
        if (filterMode == 2) {
            
            SoundConfig.SoundSettings s = SoundConfig.getSound(id);
            return s != null && s.favorite;
        }
        return true;
    }

    static String getGlobalDisplayName(String id) {
        return switch (id) {
            case "#global:break" -> "ALL BREAK SOUNDS";
            case "#global:place" -> "ALL PLACE SOUNDS";
            case "#global:step" -> "ALL STEP SOUNDS";
            case "#global:hit" -> "ALL HIT SOUNDS";
            case "#global:hostile_hurt" -> "ALL HOSTILE HURT SOUNDS";
            case "#global:passive_hurt" -> "ALL PASSIVE HURT SOUNDS";
            case "#global:hostile_ambient" -> "ALL HOSTILE AMBIENT SOUNDS";
            case "#global:passive_ambient" -> "ALL PASSIVE AMBIENT SOUNDS";
            default -> id;
        };
    }

    private void onSearch(String query) { loadSounds(query); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { this.onClose(); return true; }
        if (this.searchBox.keyPressed(input) || this.searchBox.isFocused()) return true;
        return super.keyPressed(input);
    }

    @Override public void onClose() { this.minecraft.setScreen(this.parent); }
    @Override public boolean isPauseScreen() { return false; }

    private static class SoundPickerList extends ContainerObjectSelectionList<SoundPickerEntry> {
        private int listWidth = 0;

        public SoundPickerList(Minecraft client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, itemHeight);
            this.listWidth = width;
        }

        @Override
        public int getRowWidth() {
            return 380;
        }

        public void clear() {
            this.clearEntries();
        }

        public void add(SoundPickerEntry entry) {
            this.addEntry(entry);
        }

        public void setWidth(int newWidth) {
            this.listWidth = newWidth;
        }

        @Override
        protected int scrollBarX() {
            int currentWidth = (this.listWidth > 0) ? this.listWidth : this.width;
            return this.getX() + currentWidth - 6;
        }

        @Override
        public int getRight() {
            int currentWidth = (this.listWidth > 0) ? this.listWidth : this.width;
            return this.getX() + currentWidth;
        }

        @Override
        protected void extractListBackground(net.minecraft.client.gui.GuiGraphicsExtractor context) {
            int oldWidth = this.width;
            if (this.listWidth > 0) {
                this.width = this.listWidth;
            }
            super.extractListBackground(context);
            this.width = oldWidth;
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            if (mouseX >= this.getX() + this.listWidth) return false;
            return super.isMouseOver(mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean wasHandled) {
            if (!this.isMouseOver(event.x(), event.y())) return false;
            return super.mouseClicked(event, wasHandled);
        }
    }

    private static class PickerModList extends ContainerObjectSelectionList<PickerModEntry> {
        private final AllSoundsPickerScreen parentScreen;

        public PickerModList(Minecraft client, int width, int height, int y, int itemHeight, AllSoundsPickerScreen parentScreen) {
            super(client, width, height, y, itemHeight);
            this.parentScreen = parentScreen;

            this.addEntry(new PickerModEntry("all", this));
            Set<String> namespaces = new TreeSet<>();
            for (var id : client.getSoundManager().getAvailableSounds()) {
                String ns = id.getNamespace();
                if (!ns.equals("minecraft")) namespaces.add(ns);
            }
            for (String ns : namespaces) {
                this.addEntry(new PickerModEntry(ns, this));
            }
        }

        @Override public int getRowWidth() { return 100; }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            if (!this.visible || !this.active) return false;
            return super.isMouseOver(mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean wasHandled) {
            if (!this.isMouseOver(event.x(), event.y())) return false;
            PickerModEntry entry = this.getEntryAt(event.x(), event.y());
            if (entry != null) {
                if (entry.mouseClicked(event, wasHandled)) {
                    this.setFocused(entry);
                    this.setDragging(true);
                    return true;
                }
            }
            return super.mouseClicked(event, wasHandled);
        }

        public PickerModEntry getEntryAt(double mouseX, double mouseY) {
            for (PickerModEntry entry : this.children()) {
                Button btn = entry.button;
                if (mouseX >= btn.getX() && mouseX < btn.getX() + btn.getWidth()
                        && mouseY >= btn.getY() && mouseY < btn.getY() + btn.getHeight()) {
                    return entry;
                }
            }
            return null;
        }
    }

    private static class PickerModEntry extends ContainerObjectSelectionList.Entry<PickerModEntry> {
        private final String modId;
        private final PickerModList parentList;
        private final Button button;

        public PickerModEntry(String modId, PickerModList parentList) {
            this.modId = modId;
            this.parentList = parentList;
            String displayText = modId.equals("all") ? Component.translatable("text.soundcontrol.modlist.all").getString() : modId;
            this.button = Button.builder(Component.literal(displayText), b -> {
                parentList.parentScreen.setSelectedMod(modId);
            }).bounds(0, 0, 100, 15).build();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = this.parentList.getRowLeft();
            int y = this.getY();
            this.button.setX(x);
            this.button.setY(y);
            boolean isSelected = parentList.parentScreen.getSelectedMod().equals(this.modId);
            String prefix = isSelected ? "\u25B6 " : "";
            String displayText = modId.equals("all") ? Component.translatable("text.soundcontrol.modlist.all").getString() : modId;
            this.button.setMessage(Component.literal(prefix + displayText));
            this.button.extractRenderState(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean wasHandled) {
            return this.button.mouseClicked(event, wasHandled);
        }

        @Override public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() { return java.util.List.of(button); }
        @Override public java.util.List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() { return java.util.List.of(button); }
    }

    private static class SoundPickerEntry extends ContainerObjectSelectionList.Entry<SoundPickerEntry> {
        private final String soundId;
        private final AllSoundsPickerScreen parentScreen;
        private final Button addButton;

        public SoundPickerEntry(String soundId, AllSoundsPickerScreen parentScreen) {
            this.soundId = soundId;
            this.parentScreen = parentScreen;

            boolean alreadyAdded = parentScreen.targetAnchor.getSoundOverrides().containsKey(soundId);
            this.addButton = Button.builder(
                Component.literal(alreadyAdded ? "\u2715" : "+"),
                b -> {
                    if (parentScreen.parent instanceof SoundAnchorEditScreen editScreen) {
                        boolean isAdded = parentScreen.targetAnchor.getSoundOverrides().containsKey(soundId);
                        if (isAdded) {
                            editScreen.removeSoundOverride(soundId);
                            b.setMessage(Component.literal("+"));
                        } else {
                            editScreen.addSoundOverride(soundId);
                            b.setMessage(Component.literal("\u2715"));
                        }
                    }
                }
            ).bounds(0, 0, 20, 20).build();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = this.getX(); int y = this.getY();
            var font = Minecraft.getInstance().font;

            String display;
            int color = 0xFFFFFFFF;
            boolean isGlobal = soundId.startsWith("#global:");
            boolean isGroup = !isGlobal && soundId.contains(":") && SoundConfig.getSoundGroup(soundId).equals(soundId) && !soundId.equals(soundId.replace("entity.", "").replace("block.", ""));

            if (isGlobal) {
                display = getGlobalDisplayName(soundId);
                color = 0xFFFFAA00;
            } else {
                display = soundId.contains(":") ? soundId.substring(soundId.indexOf(':') + 1) : soundId;
            }

            String namespace = soundId.contains(":") ? soundId.substring(0, soundId.indexOf(':')) : "";
            int maxW = 310;
            String truncated = font.plainSubstrByWidth(display, maxW);
            if (truncated.length() < display.length()) {
                display = font.plainSubstrByWidth(display, maxW - font.width("...")) + "...";
            } else {
                display = truncated;
            }
            context.text(font, display, x + 2, y + 2, color);
            if (!isGlobal && !namespace.equals("minecraft") && !namespace.isEmpty()) {
                context.text(font, "[" + namespace + "]", x + 2, y + 12, 0xFF888888);
            }

            this.addButton.setX(x + 350); this.addButton.setY(y);
            this.addButton.extractRenderState(context, mouseX, mouseY, tickDelta);
        }

        @Override public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() { return List.of(addButton); }
        @Override public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() { return List.of(addButton); }
    }
}

