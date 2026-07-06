package soundcontrol.gui;

import soundcontrol.SoundCategory;
import soundcontrol.SoundConfig;
import soundcontrol.SoundControl;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class SoundControlScreen extends Screen {
    private TextFieldWidget searchBox;
    private SoundListWidget soundList;
    private ModListWidget modList;
    private ProfileListWidget profileList;
    private SoundCategory currentCategory = SoundCategory.ALL;
    private int viewMode = 0;
    private String selectedMod = "";
    private int filterMode = 0;
    private String initialSearch = "";
    private int initialViewMode = -1;

    public static final int PROFILE_W = ProfileListWidget.PANEL_WIDTH;

    public SoundControlScreen() { super(Text.translatable("text.soundcontrol.title")); }

    public void setInitialSearch(String s)  { this.initialSearch = s; }
    public void setInitialViewMode(int m)   { this.initialViewMode = m; }

    private Text filterText() {
        if (this.filterMode == 1) return Text.translatable("text.soundcontrol.filter.edited");
        if (this.filterMode == 2) return Text.translatable("text.soundcontrol.filter.favorites");
        return Text.translatable("text.soundcontrol.filter.all");
    }

    @Override
    protected void init() {

        this.profileList = new ProfileListWidget(this.client, this.height - 116, 72, this);
        this.addSelectableChild(this.profileList);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("+"),
                b -> this.client.setScreen(new ProfileNameScreen(this, this.profileList)))
            .dimensions(PROFILE_W - 22, 52, 20, 18)
            .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                Text.translatable("text.soundcontrol.profile.add")))
            .build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("text.soundcontrol.profile.open_folder"),
                b -> openConfigFolder())
            .dimensions(2, this.height - 42, PROFILE_W - 4, 14).build());

        this.searchBox = new TextFieldWidget(this.textRenderer,
            this.width / 2 - 100, 22, 180, 20, Text.literal(""));
        this.searchBox.setChangedListener(q -> refilter());
        this.addSelectableChild(this.searchBox);

        this.addDrawableChild(ButtonWidget.builder(filterText(), b -> {
            this.filterMode = (this.filterMode + 1) % 3;
            b.setMessage(filterText());
            refilter();
        }).dimensions(this.width / 2 + 90, 22, 100, 20).build());

        int bw = 60, sx = this.width / 2 - (bw * 3 + 10) / 2;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.category.all"),
            b -> { this.currentCategory = SoundCategory.ALL;    refilter(); }).dimensions(sx, 46, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.category.mobs"),
            b -> { this.currentCategory = SoundCategory.MOBS;   refilter(); }).dimensions(sx + bw + 5, 46, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.category.blocks"),
            b -> { this.currentCategory = SoundCategory.BLOCKS; refilter(); }).dimensions(sx + (bw + 5) * 2, 46, bw, 20).build());

        this.soundList = new SoundListWidget(this.client, this.width, this.height - 116, 72, 25);
        this.addSelectableChild(this.soundList);

        this.modList = new ModListWidget(this.client, 120, this.height - 116, 72, 15, this);
        this.modList.setX(this.width - 120);
        this.addSelectableChild(this.modList);
        this.modList.active = false;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.mode.basic"), b -> {
            this.viewMode = (this.viewMode + 1) % 3;
            String k = this.viewMode == 0 ? "basic" : (this.viewMode == 1 ? "advanced" : "mods");
            b.setMessage(Text.translatable("text.soundcontrol.mode." + k));
            this.modList.active = (this.viewMode == 2);
            if (this.viewMode == 2) {
                this.soundList.setListWidth(this.width - 120);
            } else {
                this.soundList.setListWidth(0);
            }
            this.soundList.loadEntries(this.viewMode);
            refilter();
        }).dimensions(this.width / 2 - 160, this.height - 28, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
            .dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.button.reset"), b -> {
            SoundConfig.resetSettings();
            this.soundList.loadEntries(this.viewMode);
            refilter();
        }).dimensions(this.width / 2 + 60, this.height - 28, 80, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("\uD83D\uDD52"),
                b -> this.client.setScreen(new RecentSoundsPickerScreen(this)))
            .dimensions(this.width - 26, this.height - 28, 20, 20)
            .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                Text.translatable("text.soundcontrol.recent.title")))
            .build());

        this.setInitialFocus(this.searchBox);
        if (this.initialViewMode >= 0) { this.viewMode = this.initialViewMode; this.initialViewMode = -1; }
        this.soundList.loadEntries(this.viewMode);
        if (!this.initialSearch.isEmpty()) { this.searchBox.setText(this.initialSearch); this.initialSearch = ""; }
        if (!this.modList.children().isEmpty())
            this.selectedMod = ((ModListWidget.ModEntry) this.modList.children().get(0)).getModId();
        refilter();
    }

    public void refreshAfterProfileChange() {
        this.soundList.loadEntries(this.viewMode); refilter();
    }

    public void setSelectedMod(String id) { this.selectedMod = id; refilter(); }
    public String getSelectedMod() { return this.selectedMod; }

    private void refilter() {
        if (this.soundList != null)
            this.soundList.filter(this.searchBox.getText(), this.currentCategory,
                this.selectedMod, this.viewMode, this.filterMode);
    }

    private static void openConfigFolder() {
        try {
            net.minecraft.util.Util.getOperatingSystem().open(SoundConfig.CONFIGS_DIR.toURI());
        } catch (Exception e) {

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
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {

        ctx.fill(0, 72, PROFILE_W, this.height - 44, 0x80202030);

        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawTextWithShadow(this.textRenderer,
            Text.translatable("text.soundcontrol.profiles.title"),
            6, 56, 0xFFCCCCDD);

        this.profileList.render(ctx, mouseX, mouseY, delta);
        this.profileList.tick();

        this.soundList.render(ctx, mouseX, mouseY, delta);
        if (this.viewMode == 2) this.modList.render(ctx, mouseX, mouseY, delta);
        this.searchBox.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);

        long remaining = SoundConfig.getSwitchCooldownRemaining();
        if (remaining > 0) {
            String cdText = String.format("%.1fs", remaining / 1000.0);
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(cdText), PROFILE_W / 2, this.height - 50, 0xFFFF8800);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) { close(); return true; }
        if (this.searchBox.keyPressed(input) || this.searchBox.isActive()) return true;
        return super.keyPressed(input);
    }

    @Override public boolean shouldPause() { return false; }
}

