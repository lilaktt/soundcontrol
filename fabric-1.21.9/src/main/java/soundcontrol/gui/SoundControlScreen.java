package soundcontrol.gui;

import soundcontrol.Compat;
import soundcontrol.SoundCategory;
import soundcontrol.SoundConfig;
import soundcontrol.anchor.SoundAnchorScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

public class SoundControlScreen extends Screen {
    public static final int PROFILE_W = ProfileListWidget.PANEL_WIDTH;
    public static final int MOD_W = ModListWidget.PANEL_WIDTH;
    static final int LIST_TOP = 72;
    public static final int BACKDROP = 0xC0101010;
    public static final int PANEL = 0x80202030;
    public static final int LIST_PANEL = 0x50000000;

    private final Screen parent;
    private TextFieldWidget searchBox;
    private SoundListWidget soundList;
    private ModListWidget modList;
    private ProfileListWidget profileList;
    private SoundCategory currentCategory = SoundCategory.ALL;
    private int viewMode = 0;
    private int filterMode = 0;
    private String selectedMod = "all";
    private String query = "";

    public SoundControlScreen(Screen parent) {
        super(Text.translatable("text.soundcontrol.title"));
        this.parent = parent;
    }

    public SoundControlScreen() { this(null); }

    public void setInitialSearch(String q) { this.query = q == null ? "" : q; this.viewMode = 1; }

    public SoundControlScreen withViewMode(int mode) { this.viewMode = Math.floorMod(mode, 3); return this; }

    private int listBottom() { return this.height - 44; }

    @Override
    protected void init() {
        int listHeight = Math.max(20, listBottom() - LIST_TOP);

        this.profileList = new ProfileListWidget(this.client, listHeight, LIST_TOP, this);
        this.profileList.place(0);
        this.addDrawableChild(this.profileList);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                b -> Compat.setScreen(new ProfileNameScreen(this, null)))
            .dimensions(PROFILE_W - 22, 50, 20, 20)
            .tooltip(Tooltip.of(Text.translatable("text.soundcontrol.profile.add")))
            .build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.profile.open_folder"),
                b -> openConfigFolder())
            .dimensions(2, this.height - 42, PROFILE_W - 4, 14).build());

        boolean mods = this.viewMode == 2;
        int soundListWidth = this.width - PROFILE_W - (mods ? MOD_W : 0);
        int centre = PROFILE_W + soundListWidth / 2;
        int searchWidth = Math.max(80, Math.min(186, soundListWidth - 112));
        int searchX = centre - (searchWidth + 104) / 2;
        this.searchBox = new TextFieldWidget(this.textRenderer, searchX, 22, searchWidth, 20, Text.translatable("text.soundcontrol.search"));
        this.searchBox.setMaxLength(256);
        this.searchBox.setText(this.query);
        this.searchBox.setChangedListener(q -> { this.query = q; refilter(); });
        this.addDrawableChild(this.searchBox);

        this.addDrawableChild(ButtonWidget.builder(filterText(), b -> {
            this.filterMode = (this.filterMode + 1) % 3;
            b.setMessage(filterText());
            refilter();
        }).dimensions(searchX + searchWidth + 4, 22, 100, 20).build());

        if (!mods) {
            int bw = 60, sx = centre - (bw * 3 + 10) / 2;
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.category.all"),
                b -> setCategory(SoundCategory.ALL)).dimensions(sx, 46, bw, 20).build());
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.category.mobs"),
                b -> setCategory(SoundCategory.MOBS)).dimensions(sx + bw + 5, 46, bw, 20).build());
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.category.blocks"),
                b -> setCategory(SoundCategory.BLOCKS)).dimensions(sx + (bw + 5) * 2, 46, bw, 20).build());
        }

        this.soundList = new SoundListWidget(this.client, soundListWidth, listHeight, LIST_TOP, 25);
        this.soundList.place(PROFILE_W);
        this.addDrawableChild(this.soundList);

        if (mods) {
            this.modList = new ModListWidget(this.client, listHeight, LIST_TOP, this);
            this.modList.place(this.width - MOD_W);
            this.addDrawableChild(this.modList);
            if (!this.modList.hasMod(this.selectedMod)) this.selectedMod = "all";
        } else {
            this.modList = null;
        }

        int footerSpace = this.width - PROFILE_W - 56;
        int fw = Math.max(40, Math.min(100, (footerSpace - 8) / 3));
        int fx = PROFILE_W + Math.max(0, (footerSpace - (fw * 3 + 8)) / 2);
        this.addDrawableChild(ButtonWidget.builder(modeText(), b -> {
            this.viewMode = (this.viewMode + 1) % 3;
            this.clearAndInit();
        }).dimensions(fx, this.height - 28, fw, 20).build());

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> this.close())
            .dimensions(fx + fw + 4, this.height - 28, fw, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.button.reset"), b -> {
            SoundConfig.resetSettings();
            reloadSounds();
        }).dimensions(fx + (fw + 4) * 2, this.height - 28, fw, 20).build());

        if (Compat.inWorld()) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("⚓"),
                    b -> Compat.setScreen(new SoundAnchorScreen(this)))
                .dimensions(this.width - 50, this.height - 28, 20, 20)
                .tooltip(Tooltip.of(Text.translatable("text.soundcontrol.anchors.title"))).build());
        }

        this.addDrawableChild(ButtonWidget.builder(Text.literal("🕒"),
                b -> Compat.setScreen(new RecentSoundsPickerScreen(this)))
            .dimensions(this.width - 26, this.height - 28, 20, 20)
            .tooltip(Tooltip.of(Text.translatable("text.soundcontrol.recent.title"))).build());

        this.setInitialFocus(this.searchBox);
        reloadSounds();
    }

    private Text filterText() {
        return Text.translatable("text.soundcontrol.filter." + new String[]{"all", "edited", "favorites"}[this.filterMode]);
    }

    private Text modeText() {
        return Text.translatable("text.soundcontrol.mode." + new String[]{"basic", "advanced", "mods"}[this.viewMode]);
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
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, BACKDROP);
        ctx.fill(0, LIST_TOP, PROFILE_W, listBottom(), PANEL);
        ctx.fill(PROFILE_W, LIST_TOP, this.width - (this.modList != null ? MOD_W : 0), listBottom(), LIST_PANEL);
        if (this.modList != null) ctx.fill(this.width - MOD_W, LIST_TOP, this.width, listBottom(), PANEL);
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
        ctx.drawText(this.textRenderer, Text.translatable("text.soundcontrol.profiles.title"), 6, 56, 0xFFCCCCDD, true);
        if (this.modList != null) {
            ctx.drawText(this.textRenderer, Text.translatable("text.soundcontrol.modlist.title"), this.width - MOD_W + 6, 56, 0xFFCCCCDD, true);
        }

        long remaining = SoundConfig.getSwitchCooldownRemaining();
        if (remaining > 0) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(String.format(java.util.Locale.ROOT, "%.1fs", remaining / 1000.0)),
                PROFILE_W / 2, this.height - 54, 0xFFFF8800);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.profileList != null) this.profileList.tick();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == 256 && this.shouldCloseOnEsc()) { this.close(); return true; }
        if (this.searchBox.isFocused() && key != 258) return this.searchBox.keyPressed(input) || true;
        return super.keyPressed(input);
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        SoundConfig.flush();
    }

    @Override
    public void close() {
        Compat.setScreen(this.parent);
    }
}
