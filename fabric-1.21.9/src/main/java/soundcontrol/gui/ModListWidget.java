package soundcontrol.gui;

import soundcontrol.ModSoundCatalog;
import soundcontrol.SoundConfig;
import soundcontrol.SoundControl;
import soundcontrol.render.SoundWorldRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModListWidget extends ElementListWidget<ModListWidget.ModEntry> {
    private final SoundControlScreen parent;

    public ModListWidget(MinecraftClient client, int width, int height, int y, int itemHeight, SoundControlScreen parent) {
        super(client, width, height, y, itemHeight);
        this.parent = parent;

        this.addEntry(new ModEntry("all", this));

        Set<String> mappedNamespaces = new HashSet<>();
        for (ModSoundCatalog.ModInfo mod : ModSoundCatalog.getMods()) {
            this.addEntry(new ModEntry(mod.id(), this));
            mappedNamespaces.addAll(mod.namespaces());
        }
        client.getSoundManager().getKeys().stream()
                .map(Identifier::getNamespace)
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
        private final ButtonWidget muteButton;

        public ModEntry(String modId, ModListWidget parentList) {
            this.modId = modId;
            this.parentList = parentList;

            String displayText = this.modId.equals("all") ? Text.translatable("text.soundcontrol.modlist.all").getString() : ModSoundCatalog.getDisplayName(this.modId);

            int nameWidth = this.modId.equals("all") ? 100 : 80;
            this.button = ButtonWidget.builder(Text.literal(displayText), b -> {
                this.parentList.selectMod(this.modId);
            }).dimensions(0, 0, nameWidth, 15).build();

            this.muteButton = this.modId.equals("all") ? null : ButtonWidget.builder(Text.literal("M"), b -> {
                boolean muted = SoundConfig.toggleModMuted(this.modId);
                if (muted) SoundWorldRenderer.stopModSounds(MinecraftClient.getInstance(), this.modId);
                this.parentList.parent.refreshAfterProfileChange();
            }).dimensions(0, 0, 18, 15)
              .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("text.soundcontrol.modlist.mute")))
              .build();
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
            boolean muted = !this.modId.equals("all") && SoundConfig.isModMuted(this.modId);
            String prefix = isSelected ? "▶ " : "";
            String displayText = this.modId.equals("all") ? Text.translatable("text.soundcontrol.modlist.all").getString() : ModSoundCatalog.getDisplayName(this.modId);
            this.button.setMessage(Text.literal(prefix + displayText).styled(style -> muted ? style.withColor(0xFF5555) : style));

            this.button.render(context, mouseX, mouseY, tickDelta);
            if (this.muteButton != null) {
                this.muteButton.setX(x + 82);
                this.muteButton.setY(y);
                this.muteButton.setMessage(Text.literal(muted ? "U" : "M"));
                this.muteButton.render(context, mouseX, mouseY, tickDelta);
            }
        }

        public List<? extends net.minecraft.client.gui.Element> children() {
            return this.muteButton == null ? List.of(this.button) : List.of(this.button, this.muteButton);
        }

        public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
            return this.muteButton == null ? List.of(this.button) : List.of(this.button, this.muteButton);
        }
    }
}

