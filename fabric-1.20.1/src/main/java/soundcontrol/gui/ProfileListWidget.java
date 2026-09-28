package soundcontrol.gui;

import soundcontrol.SoundConfig;
import soundcontrol.SoundControl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;

import java.util.List;

public class ProfileListWidget extends ElementListWidget<ProfileListWidget.ProfileEntry> {
    final SoundControlScreen parent;
    public static final int PANEL_WIDTH = 130;

    private long lastRefreshMs = 0;
    private static final long REFRESH_INTERVAL_MS = 1500;

    public ProfileListWidget(MinecraftClient client, int height, int top, SoundControlScreen parent) {
        super(client, PANEL_WIDTH, height, top, top + height, 26);
        this.setRenderBackground(false);
        this.setRenderHorizontalShadows(false);
        this.setRenderHeader(false, 0);
        this.parent = parent;
        this.left = 0; this.right = PANEL_WIDTH;
        reload();
    }

    @Override
    protected void renderBackground(DrawContext context) {
        // Profile panel has its own background drawn in SoundControlScreen.render()
    }

    public void reload() {
        SoundConfig.refreshProfiles();
        this.clearEntries();
        for (SoundConfig.SoundProfile profile : SoundConfig.getProfiles()) {
            this.addEntry(new ProfileEntry(profile, this));
        }
        lastRefreshMs = System.currentTimeMillis();
    }

    
    public void tick() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs >= REFRESH_INTERVAL_MS) {
            int before = SoundConfig.getProfiles().size();
            SoundConfig.refreshProfiles();
            int after = SoundConfig.getProfiles().size();
            if (before != after) {
                this.clearEntries();
                for (SoundConfig.SoundProfile profile : SoundConfig.getProfiles()) {
                    this.addEntry(new ProfileEntry(profile, this));
                }
                parent.refreshAfterProfileChange();
            }
            lastRefreshMs = now;
        }
    }

    @Override public int getRowWidth()      { return PANEL_WIDTH - 12; }
    @Override protected int getScrollbarPositionX() { return PANEL_WIDTH - 6; }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (mouseX < 0 || mouseX >= PANEL_WIDTH) return false;
        return super.isMouseOver(mouseX, mouseY);
    }

    @Override
    protected void renderList(DrawContext context, int mouseX, int mouseY, float delta) {
        context.enableScissor(0, this.top, PANEL_WIDTH, this.bottom);
        super.renderList(context, mouseX, mouseY, delta);
        context.disableScissor();
    }

    public static class ProfileEntry extends ElementListWidget.Entry<ProfileEntry> {
        private final SoundConfig.SoundProfile profile;
        private final ProfileListWidget widget;
        private final boolean editable;
        private final ButtonWidget activateButton;
        private final ButtonWidget renameButton;
        private final ButtonWidget deleteButton;

        public ProfileEntry(SoundConfig.SoundProfile profile, ProfileListWidget widget) {
            this.profile = profile;
            this.widget = widget;

            this.editable = !profile.name.equals("default");
            int activationWidth = this.editable ? PANEL_WIDTH - 48 : PANEL_WIDTH - 12;
            this.activateButton = ButtonWidget.builder(Text.empty(), button -> {
                if (SoundConfig.getActiveProfile() != profile && SoundConfig.switchProfile(profile.name)) {
                    widget.reload();
                    widget.parent.refreshAfterProfileChange();
                }
            }).dimensions(0, 0, activationWidth, 22).build();

            if (this.editable) {
                this.renameButton = ButtonWidget.builder(Text.literal("\u270E"), b ->
                    MinecraftClient.getInstance().setScreen(
                        new ProfileRenameScreen(widget.parent, profile, widget)))
                    .dimensions(0, 0, 18, 18)
                    .tooltip(Tooltip.of(Text.translatable("text.soundcontrol.profile.rename")))
                    .build();
                this.deleteButton = ButtonWidget.builder(Text.literal("\u2715"), b -> {
                    SoundConfig.deleteProfile(profile);
                    widget.reload();
                    widget.parent.refreshAfterProfileChange();
                }).dimensions(0, 0, 18, 18)
                  .tooltip(Tooltip.of(Text.translatable("text.soundcontrol.profile.delete")))
                  .build();
            } else {
                this.renameButton = null;
                this.deleteButton = null;
            }
        }

        @Override
        public void render(DrawContext ctx, int index, int y, int x, int entryWidth,
                           int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean active = SoundConfig.getActiveProfile() == profile;

            if (active)       ctx.fill(0, y, PANEL_WIDTH - 2, y + 24, 0x5555FF55);
            else if (hovered) ctx.fill(0, y, PANEL_WIDTH - 2, y + 24, 0x22FFFFFF);

            if (active) ctx.drawTextWithShadow(mc.textRenderer, "\u25CF", 4, y + 8, 0xFF55FF55);

            int maxNameW = editable ? PANEL_WIDTH - 56 : PANEL_WIDTH - 20;
            String name = mc.textRenderer.trimToWidth(profile.name, maxNameW);
            ctx.drawTextWithShadow(mc.textRenderer, name, 14, y + 8,
                active ? 0xFF55FF55 : 0xFFFFFFFF);

            this.activateButton.setX(0);
            this.activateButton.setY(y);

            // Match 26.1: activation is an input-only child and is never rendered.
            if (editable) {
                this.renameButton.setX(PANEL_WIDTH - 42);
                this.renameButton.setY(y + 3);
                this.renameButton.render(ctx, mouseX, mouseY, delta);
                this.deleteButton.setX(PANEL_WIDTH - 22);
                this.deleteButton.setY(y + 3);
                this.deleteButton.render(ctx, mouseX, mouseY, delta);
            }
        }

        @Override public List<? extends net.minecraft.client.gui.Element> children() {
            return editable ? List.of(renameButton, deleteButton, activateButton) : List.of(activateButton);
        }
        @Override public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
            return editable ? List.of(renameButton, deleteButton, activateButton) : List.of(activateButton);
        }
    }
}

