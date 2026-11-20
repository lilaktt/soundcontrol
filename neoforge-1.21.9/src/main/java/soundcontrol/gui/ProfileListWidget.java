package soundcontrol.gui;

import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ProfileListWidget extends ContainerObjectSelectionList<ProfileListWidget.ProfileEntry> {
    public static final int PANEL_WIDTH = 130;
    private static final long REFRESH_INTERVAL_MS = 1500;
    final SoundControlScreen parent;
    private long lastRefreshMs = 0;
    private List<SoundConfig.SoundProfile> shown = new ArrayList<>();

    public ProfileListWidget(Minecraft mc, int height, int top, SoundControlScreen parent) {
        super(mc, PANEL_WIDTH, height, top, 26);
        this.parent = parent;
    }

    public void place(int x) {
        this.setX(x);
        reload();
    }

    public void reload() {
        SoundConfig.refreshProfiles();
        rebuildEntries();
    }

    private void rebuildEntries() {
        this.clearEntries();
        this.shown = new ArrayList<>(SoundConfig.getProfiles());
        for (SoundConfig.SoundProfile profile : this.shown) this.addEntry(new ProfileEntry(profile, this));
        this.lastRefreshMs = System.currentTimeMillis();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (now - this.lastRefreshMs < REFRESH_INTERVAL_MS) return;
        this.lastRefreshMs = now;
        SoundConfig.SoundProfile activeBefore = SoundConfig.getActiveProfile();
        SoundConfig.refreshProfiles();
        if (!this.shown.equals(SoundConfig.getProfiles())) {
            rebuildEntries();
            if (activeBefore != SoundConfig.getActiveProfile()) this.parent.refreshAfterProfileChange();
        }
    }

    @Override public int getRowWidth() { return PANEL_WIDTH - 12; }
    @Override protected int scrollBarX() { return this.getRowLeft() + this.getRowWidth() + 2; }

    @Override
    protected void renderListBackground(GuiGraphics context) {
    }

    @Override
    protected void renderListSeparators(GuiGraphics context) {
    }

    public static class ProfileEntry extends ContainerObjectSelectionList.Entry<ProfileEntry> {
        private final SoundConfig.SoundProfile profile;
        private final ProfileListWidget widget;
        private final Button activateButton;
        private final Button renameButton;
        private final Button deleteButton;
        private long deleteArmedAt;

        ProfileEntry(SoundConfig.SoundProfile profile, ProfileListWidget widget) {
            this.profile = profile;
            this.widget = widget;
            boolean isDefault = SoundConfig.DEFAULT_PROFILE.equals(profile.name);

            this.activateButton = Button.builder(Component.literal(profile.name), b -> {
                if (SoundConfig.getActiveProfile() != profile && SoundConfig.switchProfile(profile.name)) {
                    widget.parent.refreshAfterProfileChange();
                }
            }).bounds(0, 0, isDefault ? PANEL_WIDTH - 12 : PANEL_WIDTH - 52, 24).build();

            if (isDefault) {
                this.renameButton = null;
                this.deleteButton = null;
            } else {
                this.renameButton = Button.builder(Component.literal("✎"),
                        b -> Compat.setScreen(new ProfileNameScreen(widget.parent, profile)))
                    .bounds(0, 0, 18, 18)
                    .tooltip(Tooltip.create(Component.translatable("text.soundcontrol.profile.rename")))
                    .build();
                this.deleteButton = Button.builder(Component.literal("✕"), b -> {
                    long now = System.currentTimeMillis();
                    if (now - this.deleteArmedAt > 3000) {
                        this.deleteArmedAt = now;
                        return;
                    }
                    SoundConfig.deleteProfile(profile);
                    widget.parent.refreshAfterProfileChange();
                }).bounds(0, 0, 18, 18)
                  .tooltip(Tooltip.create(Component.translatable("text.soundcontrol.profile.delete")))
                  .build();
            }
        }

        @Override
        public void renderContent(GuiGraphics ctx, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = this.getContentX();
            int y = this.getContentY();
            var font = Minecraft.getInstance().font;
            boolean active = SoundConfig.getActiveProfile() == this.profile;

            if (active) ctx.fill(x - 2, y, x + PANEL_WIDTH - 10, y + 24, 0x5555FF55);
            else if (hovered) ctx.fill(x - 2, y, x + PANEL_WIDTH - 10, y + 24, 0x22FFFFFF);
            if (active) ctx.drawString(font, "●", x + 2, y + 8, 0xFF55FF55);

            int maxNameWidth = this.renameButton != null ? PANEL_WIDTH - 66 : PANEL_WIDTH - 26;
            ctx.drawString(font, font.plainSubstrByWidth(this.profile.name, maxNameWidth), x + 12, y + 8, active ? 0xFF55FF55 : 0xFFFFFFFF);

            this.activateButton.setX(x);
            this.activateButton.setY(y);

            if (this.renameButton != null) {
                this.renameButton.setX(x + PANEL_WIDTH - 52);
                this.renameButton.setY(y + 3);
                this.renameButton.render(ctx, mouseX, mouseY, delta);
            }
            if (this.deleteButton != null) {
                boolean armed = System.currentTimeMillis() - this.deleteArmedAt <= 3000;
                this.deleteButton.setMessage(Component.literal(armed ? "§c?" : "✕"));
                this.deleteButton.setTooltip(Tooltip.create(Component.translatable(
                        armed ? "text.soundcontrol.profile.delete_confirm" : "text.soundcontrol.profile.delete")));
                this.deleteButton.setX(x + PANEL_WIDTH - 32);
                this.deleteButton.setY(y + 3);
                this.deleteButton.render(ctx, mouseX, mouseY, delta);
            }
        }

        @Override
        public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            if (this.renameButton != null) return List.of(this.renameButton, this.deleteButton, this.activateButton);
            return List.of(this.activateButton);
        }

        @Override
        public List<? extends net.minecraft.client.gui.narration.NarratableEntry> narratables() {
            if (this.renameButton != null) return List.of(this.renameButton, this.deleteButton, this.activateButton);
            return List.of(this.activateButton);
        }
    }
}
