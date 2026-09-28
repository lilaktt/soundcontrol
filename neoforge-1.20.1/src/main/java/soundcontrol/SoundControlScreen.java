package soundcontrol;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class SoundControlScreen extends Screen {
    private EditBox searchBox;
    private SoundListWidget soundList;
    private ModListWidget modList;
    private ProfileListWidget profileList;
    private SoundCategory currentCategory = SoundCategory.ALL;
    private int viewMode = 0;
    private String selectedMod = "";
    private int filterMode = 0;

    public static final int PROFILE_W = ProfileListWidget.PANEL_WIDTH;

    public SoundControlScreen() {
        super(Component.translatable("text.soundcontrol.title"));
    }

    private Component getFilterText() {
        if (this.filterMode == 1) return Component.translatable("text.soundcontrol.filter.edited");
        if (this.filterMode == 2) return Component.translatable("text.soundcontrol.filter.favorites");
        return Component.translatable("text.soundcontrol.filter.all");
    }

    @Override
    protected void init() {
        if (this.minecraft.level != null) {
            this.addRenderableWidget(Button.builder(Component.literal("\u2693"),
                    b -> this.minecraft.setScreen(new soundcontrol.SoundAnchorScreen(this)))
                .bounds(this.width - 50, this.height - 28, 20, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("text.soundcontrol.anchors.title"))).build());
        }


        // Profile panel
        this.profileList = new ProfileListWidget(this.minecraft, this.height - 116, 72, this);
        this.addWidget(this.profileList);

        this.addRenderableWidget(Button.builder(Component.literal("+"),
                b -> this.minecraft.setScreen(new ProfileNameScreen(this, this.profileList)))
            .bounds(PROFILE_W - 22, 52, 20, 18)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("text.soundcontrol.profile.add")))
            .build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("text.soundcontrol.profile.open_folder"),
                b -> openConfigFolder())
            .bounds(2, this.height - 42, PROFILE_W - 4, 14).build());

        // Search & filter
        this.searchBox = new EditBox(this.font, this.width / 2 - 100, 22, 180, 20, Component.literal(""));
        this.searchBox.setResponder(q -> refilter());
        this.addRenderableWidget(this.searchBox);

        this.addRenderableWidget(Button.builder(getFilterText(), button -> {
            this.filterMode = (this.filterMode + 1) % 3;
            button.setMessage(getFilterText());
            refilter();
        }).bounds(this.width / 2 + 90, 22, 100, 20).build());

        // Category buttons
        int bw = 60, sx = this.width / 2 - (bw * 3 + 10) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.all"),
            b -> { this.currentCategory = SoundCategory.ALL; refilter(); }).bounds(sx, 46, bw, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.mobs"),
            b -> { this.currentCategory = SoundCategory.MOBS; refilter(); }).bounds(sx + bw + 5, 46, bw, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.blocks"),
            b -> { this.currentCategory = SoundCategory.BLOCKS; refilter(); }).bounds(sx + (bw + 5) * 2, 46, bw, 20).build());

        // Sound list
        this.soundList = new SoundListWidget(this.minecraft, this.width, this.height - 116, 72, 25);
        this.addRenderableWidget(this.soundList);

        // Mod list
        this.modList = new ModListWidget(this.minecraft, 120, this.height - 116, 72, 15, this);
        this.modList.setLeftPos(this.width - 120);
        this.modList.setModActive(false);
        this.modList.setModVisible(false);

        if (!this.modList.children().isEmpty()) {
            this.selectedMod = ((ModListWidget.ModEntry) this.modList.children().get(0)).getModId();
        }

        // Mode toggle
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.mode.basic"), button -> {
            this.viewMode = (this.viewMode + 1) % 3;
            String modeKey = this.viewMode == 0 ? "basic" : (this.viewMode == 1 ? "advanced" : "mods");
            button.setMessage(Component.translatable("text.soundcontrol.mode." + modeKey));
            boolean mods = this.viewMode == 2;
            this.modList.setModActive(mods);
            this.modList.setModVisible(mods);
            this.soundList.loadEntries(this.viewMode);
            refilter();
        }).bounds(this.width / 2 - 160, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.button.reset"), button -> {
            SoundConfig.resetSettings();
            this.soundList.loadEntries(this.viewMode);
            refilter();
        }).bounds(this.width / 2 + 60, this.height - 28, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("\uD83D\uDD52"), button -> {
            this.minecraft.setScreen(new RecentSoundsPickerScreen(this));
        }).bounds(this.width - 26, this.height - 28, 20, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("text.soundcontrol.recent.title"))).build());

        this.setInitialFocus(this.searchBox);
        this.soundList.loadEntries(this.viewMode);
        refilter();
    }

    public void refreshAfterProfileChange() {
        this.soundList.loadEntries(this.viewMode);
        refilter();
    }

    public void setSelectedMod(String modId) {
        this.selectedMod = modId;
        refilter();
    }

    public String getSelectedMod() {
        return this.selectedMod;
    }

    private void refilter() {
        if (this.soundList != null)
            this.soundList.filter(this.searchBox.getValue(), this.currentCategory,
                this.selectedMod, this.viewMode, this.filterMode);
    }

    public int getViewMode() {
        return this.viewMode;
    }

    private static void openConfigFolder() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", SoundConfig.CONFIGS_DIR.getAbsolutePath()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", SoundConfig.CONFIGS_DIR.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("xdg-open", SoundConfig.CONFIGS_DIR.getAbsolutePath()).start();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // Profile panel background
        ctx.fill(0, 72, PROFILE_W, this.height - 44, 0x80202030);

        ctx.fill(0, 0, this.width, this.height, 0xC0101010);
        super.render(ctx, mouseX, mouseY, delta);

        // Profiles title
        ctx.drawString(this.font,
            Component.translatable("text.soundcontrol.profiles.title"),
            6, 56, 0xFFCCCCDD, true);

        // Render profile list
        this.profileList.render(ctx, mouseX, mouseY, delta);
        this.profileList.tick();

        // Mod list (only in mods mode)
        if (this.viewMode == 2 && this.modList != null && this.modList.isModVisible())
            this.modList.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);

        // Cooldown display
        long remaining = SoundConfig.getSwitchCooldownRemaining();
        if (remaining > 0) {
            String cdText = String.format("%.1fs", remaining / 1000.0);
            ctx.drawCenteredString(this.font, Component.literal(cdText),
                PROFILE_W / 2, this.height - 50, 0xFFFF8800);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) {
            this.onClose();
            return true;
        }
        if (this.searchBox.keyPressed(keyCode, scanCode, modifiers) || this.searchBox.isFocused()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
