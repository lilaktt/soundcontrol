package soundcontrol;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModListWidget extends ElementListWidget<ModListWidget.ModEntry> {
    private final SoundControlScreen parent;

    public ModListWidget(MinecraftClient client, int width, int height, int y, int itemHeight, SoundControlScreen parent) {
        super(client, width, height, y, itemHeight);
        this.parent = parent;

        this.addEntry(new ModEntry("all", this));

        Set<String> namespaces = new HashSet<>();
        Collection<Identifier> soundIds = client.getSoundManager().getKeys();
        for (Identifier id : soundIds) {
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

    @Override
    protected int getScrollbarX() {
        return this.getX() + this.width - 6;
    }

    public void selectMod(String modId) {
        this.parent.setSelectedMod(modId);
    }

    public class ModEntry extends ElementListWidget.Entry<ModEntry> {
        private final String modId;
        private final ModListWidget parentList;
        private final ButtonWidget button;

        public ModEntry(String modId, ModListWidget parentList) {
            this.modId = modId;
            this.parentList = parentList;

            String displayText = this.modId.equals("all") ? Text.translatable("text.soundcontrol.modlist.all").getString() : this.modId;

            this.button = ButtonWidget.builder(Text.literal(displayText), b -> {
                this.parentList.selectMod(this.modId);
            }).dimensions(0, 0, 100, 15).build();
        }

        public String getModId() {
            return this.modId;
        }

        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = this.parentList.getRowLeft();
            int y = this.getY();

            this.button.setX(x);
            this.button.setY(y);

            boolean isSelected = parentList.parent.getSelectedMod().equals(this.modId);
            String prefix = isSelected ? "▶ " : "";
            String displayText = this.modId.equals("all") ? Text.translatable("text.soundcontrol.modlist.all").getString() : this.modId;
            this.button.setMessage(Text.literal(prefix + displayText));

            this.button.render(context, mouseX, mouseY, tickDelta);
        }

        public List<? extends net.minecraft.client.gui.Element> children() {
            return List.of(this.button);
        }

        public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
            return List.of(this.button);
        }
    }
}