package soundcontrol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ModListWidget extends ContainerObjectSelectionList<ModListWidget.ModEntry> {
    private final SoundControlScreen parent;

    public ModListWidget(Minecraft client, int width, int height, int y, int itemHeight, SoundControlScreen parent) {
        super(client, width, height, y, itemHeight);
        this.parent = parent;

        this.loadEntries();
    }

    public void loadEntries() {
        this.clearEntries();
        this.addEntry(new ModEntry("all", this));

        Set<String> namespaces = new HashSet<>();
        Collection<ResourceLocation> soundIds = Minecraft.getInstance().getSoundManager().getAvailableSounds();
        for (ResourceLocation id : soundIds) {
            String namespace = id.getNamespace();
            if (!namespace.equals("minecraft") && !namespace.startsWith("#global")) {
                namespaces.add(namespace);
            }
        }

        List<String> sortedNamespaces = new ArrayList<>(namespaces);
        Collections.sort(sortedNamespaces);

        for (String namespace : sortedNamespaces) {
            this.addEntry(new ModEntry(namespace, this));
        }
    }

    @Override
    public int getRowWidth() {
        return 100;
    }

    public void selectMod(String modId) {
        this.parent.setSelectedMod(modId);
    }

    @Override
    protected void renderListBackground(GuiGraphics guiGraphics) {

    }

    @Override
    protected void renderListSeparators(GuiGraphics guiGraphics) {
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), 0x80000000);
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
    }

    public class ModEntry extends ContainerObjectSelectionList.Entry<ModEntry> {
        private final String modId;
        private final ModListWidget parentList;
        private final Button button;

        public ModEntry(String modId, ModListWidget parentList) {
            this.modId = modId;
            this.parentList = parentList;

            String displayText = this.modId.equals("all") ? Component.translatable("text.soundcontrol.modlist.all").getString() : this.modId;

            this.button = Button.builder(Component.literal(displayText), b -> {
                this.parentList.selectMod(this.modId);
            }).bounds(0, 0, 100, 15).build();
        }

        public String getModId() {
            return this.modId;
        }

        @Override
        public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
            int buttonX = parentList.getX() + (parentList.getWidth() - 100) / 2;
            this.button.setX(buttonX);
            this.button.setY(this.getY());

            boolean isSelected = parentList.parent.getSelectedMod().equals(this.modId);
            String prefix = isSelected ? "▶ " : "";
            String displayText = this.modId.equals("all") ? Component.translatable("text.soundcontrol.modlist.all").getString() : this.modId;
            this.button.setMessage(Component.literal(prefix + displayText));

            this.button.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void visitWidgets(Consumer<AbstractWidget> consumer) {
            consumer.accept(this.button);
        }

        @Override
        public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return List.of(this.button);
        }

        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            return List.of(this.button);
        }
    }
}
