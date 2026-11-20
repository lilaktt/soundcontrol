package soundcontrol.gui;

import soundcontrol.Compat;
import soundcontrol.SoundCategory;
import soundcontrol.SoundConfig;
import soundcontrol.anchor.SoundAnchorScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class SoundControlScreen extends Screen {
    public static final int PROFILE_W = ProfileListWidget.PANEL_WIDTH;
    public static final int MOD_W = ModListWidget.PANEL_WIDTH;
    static final int LIST_TOP = 72;
    public static final int BACKDROP = 0xC0101010;
    public static final int PANEL = 0x80202030;
    public static final int LIST_PANEL = 0x50000000;

    private final Screen parent;
    private EditBox searchBox;
    private SoundListWidget soundList;
    private ModListWidget modList;
    private ProfileListWidget profileList;
    private SoundCategory currentCategory = SoundCategory.ALL;
    private int viewMode = 0;
    private int filterMode = 0;
    private String selectedMod = "all";
    private String query = "";

    public SoundControlScreen(Screen parent) {
        super(Component.translatable("text.soundcontrol.title"));
        this.parent = parent;
    }

    public SoundControlScreen() { this(null); }

    public void setInitialSearch(String q) { this.query = q == null ? "" : q; this.viewMode = 1; }

    public SoundControlScreen withViewMode(int mode) { this.viewMode = Math.floorMod(mode, 3); return this; }

    private int listBottom() { return this.height - 44; }

    @Override
    protected void init() {
        int listHeight = Math.max(20, listBottom() - LIST_TOP);

        this.profileList = new ProfileListWidget(this.minecraft, listHeight, LIST_TOP, this);
        this.profileList.place(0);
        this.addRenderableWidget(this.profileList);

        this.addRenderableWidget(Button.builder(Component.literal("+"),
                b -> Compat.setScreen(new ProfileNameScreen(this, null)))
            .bounds(PROFILE_W - 22, 50, 20, 20)
            .tooltip(Tooltip.create(Component.translatable("text.soundcontrol.profile.add")))
            .build());

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.profile.open_folder"),
                b -> openConfigFolder())
            .bounds(2, this.height - 42, PROFILE_W - 4, 14).build());

        boolean mods = this.viewMode == 2;
        int soundListWidth = this.width - PROFILE_W - (mods ? MOD_W : 0);
        int centre = PROFILE_W + soundListWidth / 2;
        int searchWidth = Math.max(80, Math.min(186, soundListWidth - 112));
        int searchX = centre - (searchWidth + 104) / 2;
        this.searchBox = new EditBox(this.font, searchX, 22, searchWidth, 20, Component.translatable("text.soundcontrol.search"));
        this.searchBox.setMaxLength(256);
        this.searchBox.setValue(this.query);
        this.searchBox.setResponder(q -> { this.query = q; refilter(); });
        this.addRenderableWidget(this.searchBox);

        this.addRenderableWidget(Button.builder(filterText(), b -> {
            this.filterMode = (this.filterMode + 1) % 3;
            b.setMessage(filterText());
            refilter();
        }).bounds(searchX + searchWidth + 4, 22, 100, 20).build());

        if (!mods) {
            int bw = 60, sx = centre - (bw * 3 + 10) / 2;
            this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.all"),
                b -> setCategory(SoundCategory.ALL)).bounds(sx, 46, bw, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.mobs"),
                b -> setCategory(SoundCategory.MOBS)).bounds(sx + bw + 5, 46, bw, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.blocks"),
                b -> setCategory(SoundCategory.BLOCKS)).bounds(sx + (bw + 5) * 2, 46, bw, 20).build());
        }

        this.soundList = new SoundListWidget(this.minecraft, soundListWidth, listHeight, LIST_TOP, 25);
        this.soundList.place(PROFILE_W);
        this.addRenderableWidget(this.soundList);

        if (mods) {
            this.modList = new ModListWidget(this.minecraft, listHeight, LIST_TOP, this);
            this.modList.place(this.width - MOD_W);
            this.addRenderableWidget(this.modList);
            if (!this.modList.hasMod(this.selectedMod)) this.selectedMod = "all";
        } else {
            this.modList = null;
        }

        int footerSpace = this.width - PROFILE_W - 56;
        int fw = Math.max(40, Math.min(100, (footerSpace - 8) / 3));
        int fx = PROFILE_W + Math.max(0, (footerSpace - (fw * 3 + 8)) / 2);
        this.addRenderableWidget(Button.builder(modeText(), b -> {
            this.viewMode = (this.viewMode + 1) % 3;
            this.rebuildWidgets();
        }).bounds(fx, this.height - 28, fw, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
            .bounds(fx + fw + 4, this.height - 28, fw, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.button.reset"), b -> {
            SoundConfig.resetSettings();
            reloadSounds();
        }).bounds(fx + (fw + 4) * 2, this.height - 28, fw, 20).build());

        if (Compat.inWorld()) {
            this.addRenderableWidget(Button.builder(Component.literal("⚓"),
                    b -> Compat.setScreen(new SoundAnchorScreen(this)))
                .bounds(this.width - 50, this.height - 28, 20, 20)
                .tooltip(Tooltip.create(Component.translatable("text.soundcontrol.anchors.title"))).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("🕒"),
                b -> Compat.setScreen(new RecentSoundsPickerScreen(this)))
            .bounds(this.width - 26, this.height - 28, 20, 20)
            .tooltip(Tooltip.create(Component.translatable("text.soundcontrol.recent.title"))).build());

        this.setInitialFocus(this.searchBox);
        reloadSounds();
    }

    private Component filterText() {
        return Component.translatable("text.soundcontrol.filter." + new String[]{"all", "edited", "favorites"}[this.filterMode]);
    }

    private Component modeText() {
        return Component.translatable("text.soundcontrol.mode." + new String[]{"basic", "advanced", "mods"}[this.viewMode]);
    }

    private void setCategory(SoundCategory category) { this.currentCategory = category; refilter(); }

    private void reloadSounds() {
        this.soundList.loadEntries(this.viewMode);
        refilter();
    }

    public void refreshAfterProfileChange() {
        if (this.profileList != null) this.profileList.reload();
        if (this.modList != null) this.modList.reload();
        if (this.soundList != null) reloadSounds();
    }

    public void setSelectedMod(String id) { this.selectedMod = id; refilter(); }
    public String getSelectedMod() { return this.selectedMod; }

    void refilter() {
        if (this.soundList != null)
            this.soundList.filter(this.query, this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
    }

    private static void openConfigFolder() {
        try {
            String path = SoundConfig.CONFIGS_DIR.toAbsolutePath().toString();
            String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
            if (os.contains("win")) new ProcessBuilder("explorer.exe", path).start();
            else if (os.contains("mac")) new ProcessBuilder("open", path).start();
            else new ProcessBuilder("xdg-open", path).start();
        } catch (Exception ignored) {}
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, BACKDROP);
        ctx.fill(0, LIST_TOP, PROFILE_W, listBottom(), PANEL);
        ctx.fill(PROFILE_W, LIST_TOP, this.width - (this.modList != null ? MOD_W : 0), listBottom(), LIST_PANEL);
        if (this.modList != null) ctx.fill(this.width - MOD_W, LIST_TOP, this.width, listBottom(), PANEL);
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        ctx.drawString(this.font, Component.translatable("text.soundcontrol.profiles.title"), 6, 56, 0xFFCCCCDD);
        if (this.modList != null) {
            ctx.drawString(this.font, Component.translatable("text.soundcontrol.modlist.title"), this.width - MOD_W + 6, 56, 0xFFCCCCDD);
        }

        long remaining = SoundConfig.getSwitchCooldownRemaining();
        if (remaining > 0) {
            ctx.drawCenteredString(this.font, Component.literal(String.format(java.util.Locale.ROOT, "%.1fs", remaining / 1000.0)),
                PROFILE_W / 2, this.height - 54, 0xFFFF8800);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.profileList != null) this.profileList.tick();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode;
        if (key == 256 && this.shouldCloseOnEsc()) { this.onClose(); return true; }
        if (this.searchBox.isFocused() && key != 258) return this.searchBox.keyPressed(keyCode, scanCode, modifiers) || true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        SoundConfig.flush();
    }

    @Override
    public void onClose() {
        Compat.setScreen(this.parent);
    }
}
