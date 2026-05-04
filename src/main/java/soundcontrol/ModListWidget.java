package soundcontrol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModListWidget extends ContainerObjectSelectionList<ModListWidget.ModEntry> {
    private final SoundControlScreen parent;

    public ModListWidget(Minecraft client, int width, int height, int y, int itemHeight, SoundControlScreen parent) {
        super(client, width, height, y, itemHeight);
        this.parent = parent;

        this.addEntry(new ModEntry("all", this));

        Set<String> namespaces = new HashSet<>();
        var soundIds = client.getSoundManager().getAvailableSounds();
        for (var id : soundIds) {
            String namespace = id.getNamespace();
            if (!namespace.equals("minecraft")) {
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
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = this.parentList.getRowLeft();
            int y = this.getY();

            this.button.setX(x);
            this.button.setY(y);

            boolean isSelected = parentList.parent.getSelectedMod().equals(this.modId);
            String prefix = isSelected ? "▶ " : "";
            String displayText = this.modId.equals("all") ? Component.translatable("text.soundcontrol.modlist.all").getString() : this.modId;
            this.button.setMessage(Component.literal(prefix + displayText));

            this.button.extractRenderState(context, mouseX, mouseY, tickDelta);
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
