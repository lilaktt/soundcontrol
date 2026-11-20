package soundcontrol.gui;

import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import soundcontrol.render.SoundWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.TreeMap;

public class ModListWidget extends ContainerObjectSelectionList<ModListWidget.ModEntry> {
    public static final int PANEL_WIDTH = 120;
    private final SoundControlScreen parent;

    public ModListWidget(Minecraft client, int height, int y, SoundControlScreen parent) {
        super(client, PANEL_WIDTH, height, y, 17);
        this.parent = parent;
    }

    public void place(int x) {
        this.setX(x);
        reload();
    }

    public void reload() {
        this.clearEntries();
        this.addEntry(new ModEntry("all", this));
        TreeMap<String, String> mods = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String id : Compat.clientSoundIds()) {
            String namespace = id.substring(0, id.indexOf(':'));
            if (!namespace.equals("minecraft")) mods.putIfAbsent(Compat.modName(namespace) + "\u0000" + namespace, namespace);
        }
        for (String namespace : mods.values()) this.addEntry(new ModEntry(namespace, this));
    }

    public boolean hasMod(String namespace) {
        for (ModEntry entry : this.children()) if (entry.modId.equals(namespace)) return true;
        return false;
    }

    @Override
    public int getRowWidth() {
        return PANEL_WIDTH - 12;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getRowLeft() + this.getRowWidth() + 2;
    }

    @Override
    protected void renderListBackground(GuiGraphics context) {
    }

    @Override
    protected void renderListSeparators(GuiGraphics context) {
    }

    public static class ModEntry extends ContainerObjectSelectionList.Entry<ModEntry> {
        private final String modId;
        private final ModListWidget parentList;
        private final Button button;
        private final Button muteButton;

        ModEntry(String modId, ModListWidget parentList) {
            this.modId = modId;
            this.parentList = parentList;
            boolean all = modId.equals("all");
            this.button = Button.builder(Component.literal(label()), b -> parentList.parent.setSelectedMod(this.modId))
                .bounds(0, 0, all ? PANEL_WIDTH - 12 : PANEL_WIDTH - 32, 15).build();
            this.muteButton = all ? null : Button.builder(Component.literal("M"), b -> {
                boolean muted = SoundConfig.toggleModMuted(this.modId);
                if (muted) SoundWorldRenderer.stopNamespace(this.modId);
                parentList.parent.refilter();
            }).bounds(0, 0, 18, 15)
              .tooltip(Tooltip.create(Component.translatable("text.soundcontrol.modlist.mute")))
              .build();
        }

        public String getModId() {
            return this.modId;
        }

        private String label() {
            return this.modId.equals("all") ? Component.translatable("text.soundcontrol.modlist.all").getString() : Compat.modName(this.modId);
        }

        @Override
        public void render(GuiGraphics context, int index, int y, int x, int entryW, int entryH, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            var font = Minecraft.getInstance().font;
            boolean selected = parentList.parent.getSelectedMod().equals(this.modId);
            boolean muted = this.muteButton != null && SoundConfig.isModMuted(this.modId);
            String text = (selected ? "▶ " : "") + label();
            text = font.plainSubstrByWidth(text, this.button.getWidth() - 8);
            this.button.setMessage(Component.literal((muted ? "§c" : "") + text));
            this.button.setX(x);
            this.button.setY(y);
            this.button.render(context, mouseX, mouseY, tickDelta);
            if (this.muteButton != null) {
                this.muteButton.setMessage(Component.literal(muted ? "§cU" : "M"));
                this.muteButton.setX(x + PANEL_WIDTH - 30);
                this.muteButton.setY(y);
                this.muteButton.render(context, mouseX, mouseY, tickDelta);
            }
        }

        @Override
        public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return this.muteButton == null ? List.of(this.button) : List.of(this.button, this.muteButton);
        }

        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return this.muteButton == null ? List.of(this.button) : List.of(this.button, this.muteButton);
        }
    }
}
