package soundcontrol.gui;

import soundcontrol.ModSoundCatalog;
import soundcontrol.SoundConfig;
import soundcontrol.SoundControl;
import soundcontrol.render.SoundWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModListWidget extends ContainerObjectSelectionList<ModListWidget.ModEntry> {
    private final SoundControlScreen parent;

    public ModListWidget(Minecraft client, int width, int height, int y, int itemHeight, SoundControlScreen parent) {
        super(client, width, height, y, itemHeight);
        this.parent = parent;

        this.addEntry(new ModEntry("all", this));

        Set<String> mappedNamespaces = new HashSet<>();
        for (ModSoundCatalog.ModInfo mod : ModSoundCatalog.getMods()) {
            this.addEntry(new ModEntry(mod.id(), this));
            mappedNamespaces.addAll(mod.namespaces());
        }
        client.getSoundManager().getAvailableSounds().stream()
                .map(id -> id.getNamespace())
                .filter(namespace -> !namespace.equals("minecraft") && !mappedNamespaces.contains(namespace))
                .distinct()
                .sorted()
                .forEach(namespace -> this.addEntry(new ModEntry(namespace, this)));
    }

    @Override
    public int getRowWidth() {
        return 100;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!this.visible || !this.active) return false;
        return super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean wasHandled) {
        if (!this.isMouseOver(event.x(), event.y())) return false;
        ModEntry entry = this.getEntryAt(event.x(), event.y());
        if (entry != null) {
            if (entry.mouseClicked(event, wasHandled)) {
                this.setFocused(entry);
                this.setDragging(true);
                return true;
            }
        }
        return super.mouseClicked(event, wasHandled);
    }

    public void selectMod(String modId) {
        this.parent.setSelectedMod(modId);
    }

    public ModEntry getEntryAt(double mouseX, double mouseY) {
        for (ModEntry entry : this.children()) {
            Button btn = entry.button;
            if (mouseX >= btn.getX() && mouseX < btn.getX() + btn.getWidth()
                    && mouseY >= btn.getY() && mouseY < btn.getY() + btn.getHeight()) {
                return entry;
            }
            Button mute = entry.muteButton;
            if (mute != null && mouseX >= mute.getX() && mouseX < mute.getX() + mute.getWidth()
                    && mouseY >= mute.getY() && mouseY < mute.getY() + mute.getHeight()) {
                return entry;
            }
        }
        return null;
    }

    public class ModEntry extends ContainerObjectSelectionList.Entry<ModEntry> {
        private final String modId;
        private final ModListWidget parentList;
        private final Button button;
        private final Button muteButton;

        public ModEntry(String modId, ModListWidget parentList) {
            this.modId = modId;
            this.parentList = parentList;

            String displayText = this.modId.equals("all") ? Component.translatable("text.soundcontrol.modlist.all").getString() : ModSoundCatalog.getDisplayName(this.modId);

            int nameWidth = this.modId.equals("all") ? 100 : 80;
            this.button = Button.builder(Component.literal(displayText), b -> {
                this.parentList.selectMod(this.modId);
            }).bounds(0, 0, nameWidth, 15).build();

            this.muteButton = this.modId.equals("all") ? null : Button.builder(Component.literal("M"), b -> {
                boolean muted = SoundConfig.toggleModMuted(this.modId);
                if (muted) SoundWorldRenderer.stopModSounds(Minecraft.getInstance(), this.modId);
                this.parentList.parent.refreshAfterProfileChange();
            }).bounds(0, 0, 18, 15).build();
            if (this.muteButton != null) {
                this.muteButton.setTooltip(Tooltip.create(Component.translatable("text.soundcontrol.modlist.mute")));
            }
        }

        public String getModId() {
            return this.modId;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = this.parentList.getRowLeft();
            int y = this.getY();

            this.button.setX(x);
            this.button.setY(y);

            boolean isSelected = parentList.parent.getSelectedMod().equals(this.modId);
            boolean muted = !this.modId.equals("all") && SoundConfig.isModMuted(this.modId);
            String prefix = isSelected ? "▶ " : "";
            String displayText = this.modId.equals("all") ? Component.translatable("text.soundcontrol.modlist.all").getString() : ModSoundCatalog.getDisplayName(this.modId);
            this.button.setMessage(Component.literal(prefix + displayText).withColor(muted ? 0xFF5555 : 0xFFFFFF));

            this.button.extractRenderState(context, mouseX, mouseY, tickDelta);
            if (this.muteButton != null) {
                this.muteButton.setX(x + 82);
                this.muteButton.setY(y);
                this.muteButton.setMessage(Component.literal(muted ? "U" : "M"));
                this.muteButton.extractRenderState(context, mouseX, mouseY, tickDelta);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean wasHandled) {
            if (this.muteButton != null && this.muteButton.mouseClicked(event, wasHandled)) return true;
            return this.button.mouseClicked(event, wasHandled);
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

