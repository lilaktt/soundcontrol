package soundcontrol.gui;

import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ProfileListWidget extends ElementListWidget<ProfileListWidget.ProfileEntry> {
    public static final int PANEL_WIDTH = 130;
    private static final long REFRESH_INTERVAL_MS = 1500;
    final SoundControlScreen parent;
    private long lastRefreshMs = 0;
    private List<SoundConfig.SoundProfile> shown = new ArrayList<>();

    public ProfileListWidget(MinecraftClient mc, int height, int top, SoundControlScreen parent) {
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
    @Override protected int getScrollbarX() { return this.getRowLeft() + this.getRowWidth() + 2; }

    @Override
    protected void drawMenuListBackground(DrawContext context) {
    }

    @Override
    protected void drawHeaderAndFooterSeparators(DrawContext context) {
    }

    public static class ProfileEntry extends ElementListWidget.Entry<ProfileEntry> {
        private final SoundConfig.SoundProfile profile;
        private final ProfileListWidget widget;
        private final ButtonWidget activateButton;
        private final ButtonWidget renameButton;
        private final ButtonWidget deleteButton;
        private long deleteArmedAt;

        ProfileEntry(SoundConfig.SoundProfile profile, ProfileListWidget widget) {
            this.profile = profile;
            this.widget = widget;
            boolean isDefault = SoundConfig.DEFAULT_PROFILE.equals(profile.name);

            this.activateButton = ButtonWidget.builder(Text.literal(profile.name), b -> {
                if (SoundConfig.getActiveProfile() != profile && SoundConfig.switchProfile(profile.name)) {
                    widget.parent.refreshAfterProfileChange();
                }
            }).dimensions(0, 0, isDefault ? PANEL_WIDTH - 12 : PANEL_WIDTH - 52, 24).build();

            if (isDefault) {
                this.renameButton = null;
                this.deleteButton = null;
            } else {
                this.renameButton = ButtonWidget.builder(Text.literal("✎"),
                        b -> Compat.setScreen(new ProfileNameScreen(widget.parent, profile)))
                    .dimensions(0, 0, 18, 18)
                    .tooltip(Tooltip.of(Text.translatable("text.soundcontrol.profile.rename")))
                    .build();
                this.deleteButton = ButtonWidget.builder(Text.literal("✕"), b -> {
                    long now = System.currentTimeMillis();
                    if (now - this.deleteArmedAt > 3000) {
                        this.deleteArmedAt = now;
                        return;
                    }
                    SoundConfig.deleteProfile(profile);
                    widget.parent.refreshAfterProfileChange();
                }).dimensions(0, 0, 18, 18)
                  .tooltip(Tooltip.of(Text.translatable("text.soundcontrol.profile.delete")))
                  .build();
            }
        }

        @Override
        public void render(DrawContext ctx, int index, int y, int x, int entryW, int entryH, int mouseX, int mouseY, boolean hovered, float delta) {
            var font = MinecraftClient.getInstance().textRenderer;
            boolean active = SoundConfig.getActiveProfile() == this.profile;

            if (active) ctx.fill(x - 2, y, x + PANEL_WIDTH - 10, y + 24, 0x5555FF55);
            else if (hovered) ctx.fill(x - 2, y, x + PANEL_WIDTH - 10, y + 24, 0x22FFFFFF);
            if (active) ctx.drawText(font, "●", x + 2, y + 8, 0xFF55FF55, true);

            int maxNameWidth = this.renameButton != null ? PANEL_WIDTH - 66 : PANEL_WIDTH - 26;
            ctx.drawText(font, font.trimToWidth(this.profile.name, maxNameWidth), x + 12, y + 8, active ? 0xFF55FF55 : 0xFFFFFFFF, true);

            this.activateButton.setX(x);
            this.activateButton.setY(y);

            if (this.renameButton != null) {
                this.renameButton.setX(x + PANEL_WIDTH - 52);
                this.renameButton.setY(y + 3);
                this.renameButton.render(ctx, mouseX, mouseY, delta);
            }
            if (this.deleteButton != null) {
                boolean armed = System.currentTimeMillis() - this.deleteArmedAt <= 3000;
                this.deleteButton.setMessage(Text.literal(armed ? "§c?" : "✕"));
                this.deleteButton.setTooltip(Tooltip.of(Text.translatable(
                        armed ? "text.soundcontrol.profile.delete_confirm" : "text.soundcontrol.profile.delete")));
                this.deleteButton.setX(x + PANEL_WIDTH - 32);
                this.deleteButton.setY(y + 3);
                this.deleteButton.render(ctx, mouseX, mouseY, delta);
            }
        }

        @Override
        public List<? extends net.minecraft.client.gui.Element> children() {
            if (this.renameButton != null) return List.of(this.renameButton, this.deleteButton, this.activateButton);
            return List.of(this.activateButton);
        }

        @Override
        public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
            if (this.renameButton != null) return List.of(this.renameButton, this.deleteButton, this.activateButton);
            return List.of(this.activateButton);
        }
    }
}
