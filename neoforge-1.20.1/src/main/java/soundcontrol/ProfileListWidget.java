package soundcontrol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ProfileListWidget extends ObjectSelectionList<ProfileListWidget.ProfileEntry> {
    final SoundControlScreen parent;
    public static final int PANEL_WIDTH = 130;

    private long lastRefreshMs = 0;
    private static final long REFRESH_INTERVAL_MS = 1500;

    public ProfileListWidget(Minecraft mc, int height, int top, SoundControlScreen parent) {
        super(mc, PANEL_WIDTH, height, top, top + height, 26);
        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
        this.parent = parent;
        this.setLeftPos(0);
        reload();
    }

    @Override
    protected void renderBackground(GuiGraphics guiGraphics) {
        // The profile panel background is rendered by SoundControlScreen.
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

    @Override public int getRowWidth()    { return PANEL_WIDTH - 12; }
    @Override protected int getScrollbarPosition()  { return PANEL_WIDTH - 6; }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (mouseX < 0 || mouseX >= PANEL_WIDTH) return false;
        return super.isMouseOver(mouseX, mouseY);
    }

    public static class ProfileEntry extends ObjectSelectionList.Entry<ProfileEntry> {
        private final SoundConfig.SoundProfile profile;
        private final ProfileListWidget widget;
        private final Button activateButton;
        private final Button renameButton;
        private final Button deleteButton;

        public ProfileEntry(SoundConfig.SoundProfile profile, ProfileListWidget widget) {
            this.profile = profile;
            this.widget = widget;

            int activateW = profile.name.equals("default") ? PANEL_WIDTH - 12 : PANEL_WIDTH - 48;
            this.activateButton = Button.builder(Component.literal(""), b -> {
                if (SoundConfig.getActiveProfile() != profile) {
                    boolean ok = SoundConfig.switchProfile(profile.name);
                    if (ok) {
                        widget.reload();
                        widget.parent.refreshAfterProfileChange();
                    }
                }
            }).bounds(0, 0, activateW, 22).build();

            if (profile.name.equals("default")) {
                this.renameButton = null;
                this.deleteButton = null;
            } else {
                this.renameButton = Button.builder(Component.literal("\u270E"), b ->
                    Minecraft.getInstance().setScreen(new ProfileRenameScreen(widget.parent, profile, widget))
                ).bounds(0, 0, 18, 18)
                 .tooltip(Tooltip.create(Component.translatable("text.soundcontrol.profile.rename")))
                 .build();

                this.deleteButton = Button.builder(Component.literal("\u2715"), b -> {
                    SoundConfig.deleteProfile(profile);
                    widget.reload();
                    widget.parent.refreshAfterProfileChange();
                }).bounds(0, 0, 18, 18)
                 .tooltip(Tooltip.create(Component.translatable("text.soundcontrol.profile.delete")))
                 .build();
            }
        }

        @Override
        public void render(GuiGraphics ctx, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean hovered, float delta) {
            Minecraft mc = Minecraft.getInstance();
            boolean active = SoundConfig.getActiveProfile() == profile;

            if (active)       ctx.fill(0, top, PANEL_WIDTH - 2, top + 24, 0x5555FF55);
            else if (hovered) ctx.fill(0, top, PANEL_WIDTH - 2, top + 24, 0x22FFFFFF);

            if (active) ctx.drawString(mc.font, "\u25CF", 4, top + 8, 0xFF55FF55, true);

            int maxNameW = (renameButton != null) ? PANEL_WIDTH - 56 : PANEL_WIDTH - 20;
            String name = mc.font.plainSubstrByWidth(profile.name, maxNameW);
            ctx.drawString(mc.font, name, 14, top + 8,
                active ? 0xFF55FF55 : 0xFFFFFFFF, true);

            activateButton.setX(0); activateButton.setY(top);

            if (renameButton != null) {
                renameButton.setX(PANEL_WIDTH - 42); renameButton.setY(top + 3);
                renameButton.render(ctx, mouseX, mouseY, delta);
            }
            if (deleteButton != null) {
                deleteButton.setX(PANEL_WIDTH - 22); deleteButton.setY(top + 3);
                deleteButton.render(ctx, mouseX, mouseY, delta);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (renameButton != null && renameButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (deleteButton != null && deleteButton.mouseClicked(mouseX, mouseY, button)) return true;
            if (activateButton.mouseClicked(mouseX, mouseY, button)) return true;
            return false;
        }

        @Override
        public Component getNarration() { return Component.literal(profile.name); }
    }
}
