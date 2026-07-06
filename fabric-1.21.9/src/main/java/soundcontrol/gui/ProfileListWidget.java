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
        super(client, PANEL_WIDTH, height, top, 26);
        this.parent = parent;
        this.setX(0);
        reload();
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
    @Override protected int getScrollbarX() { return PANEL_WIDTH - 6; }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (mouseX < 0 || mouseX >= PANEL_WIDTH) return false;
        return super.isMouseOver(mouseX, mouseY);
    }

    @Override
    protected void drawMenuListBackground(DrawContext context) {

    }

    @Override
    protected void drawHeaderAndFooterSeparators(DrawContext context) {

    }

    @Override
    protected void renderList(DrawContext context, int mouseX, int mouseY, float delta) {
        context.enableScissor(0, this.getY(), PANEL_WIDTH, this.getBottom());
        super.renderList(context, mouseX, mouseY, delta);
        context.disableScissor();
    }

    public static class ProfileEntry extends ElementListWidget.Entry<ProfileEntry> {
        private final SoundConfig.SoundProfile profile;
        private final ProfileListWidget widget;
        private final ButtonWidget renameButton;
        private final ButtonWidget deleteButton;
        private final ButtonWidget activateButton;

        public ProfileEntry(SoundConfig.SoundProfile profile, ProfileListWidget widget) {
            this.profile = profile;
            this.widget = widget;

            int activateW = profile.name.equals("default") ? PANEL_WIDTH - 12 : PANEL_WIDTH - 48;
            this.activateButton = ButtonWidget.builder(Text.literal(""), b -> {
                if (SoundConfig.getActiveProfile() != profile) {
                    boolean ok = SoundConfig.switchProfile(profile.name);
                    if (ok) {
                        widget.reload();
                        widget.parent.refreshAfterProfileChange();
                    }
                }
            }).dimensions(0, 0, activateW, 22).build();
            this.activateButton.setAlpha(0f);

            if (profile.name.equals("default")) {
                this.renameButton = null;
                this.deleteButton = null;
            } else {
                this.renameButton = ButtonWidget.builder(Text.literal("\u270E"), b ->
                    MinecraftClient.getInstance().setScreen(
                        new ProfileRenameScreen(widget.parent, profile, widget))
                ).dimensions(0, 0, 18, 18)
                 .tooltip(Tooltip.of(Text.translatable("text.soundcontrol.profile.rename")))
                 .build();

                this.deleteButton = ButtonWidget.builder(Text.literal("\u2715"), b -> {
                    SoundConfig.deleteProfile(profile);
                    widget.reload();
                    widget.parent.refreshAfterProfileChange();
                }).dimensions(0, 0, 18, 18)
                 .tooltip(Tooltip.of(Text.translatable("text.soundcontrol.profile.delete")))
                 .build();
            }
        }

        public void render(DrawContext ctx, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = this.getX();
            int y = this.getY();
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean active = SoundConfig.getActiveProfile() == profile;

            if (active)       ctx.fill(0, y, PANEL_WIDTH - 2, y + 24, 0x5555FF55);
            else if (hovered) ctx.fill(0, y, PANEL_WIDTH - 2, y + 24, 0x22FFFFFF);

            if (active) ctx.drawTextWithShadow(mc.textRenderer, "\u25CF", 4, y + 8, 0xFF55FF55);

            int maxNameW = (renameButton != null) ? PANEL_WIDTH - 56 : PANEL_WIDTH - 20;
            String name = mc.textRenderer.trimToWidth(profile.name, maxNameW);
            ctx.drawTextWithShadow(mc.textRenderer, name, 14, y + 8,
                active ? 0xFF55FF55 : 0xFFFFFFFF);

            activateButton.setX(0); activateButton.setY(y);
            activateButton.render(ctx, mouseX, mouseY, delta);

            if (renameButton != null) {
                renameButton.setX(PANEL_WIDTH - 42); renameButton.setY(y + 3);
                renameButton.render(ctx, mouseX, mouseY, delta);
            }
            if (deleteButton != null) {
                deleteButton.setX(PANEL_WIDTH - 22); deleteButton.setY(y + 3);
                deleteButton.render(ctx, mouseX, mouseY, delta);
            }
        }

        @Override public List<? extends net.minecraft.client.gui.Element> children() {
            if (renameButton != null && deleteButton != null) return List.of(renameButton, deleteButton, activateButton);
            return List.of(activateButton);
        }
        @Override public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
            if (renameButton != null && deleteButton != null) return List.of(renameButton, deleteButton, activateButton);
            return List.of(activateButton);
        }
    }
}

