package soundcontrol.gui;

import soundcontrol.SoundCategory;
import soundcontrol.SoundConfig;
import soundcontrol.SoundControl;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class ProfileEditScreen extends Screen {
    private final Screen parent;
    private final SoundConfig.SoundProfile profile;

    private TextFieldWidget searchBox;
    private SoundListWidget soundList;
    private ModListWidget modList;
    private SoundCategory currentCategory = SoundCategory.ALL;
    private int viewMode = 0;
    private String selectedMod = "";
    private int filterMode = 0;

    public ProfileEditScreen(Screen parent, SoundConfig.SoundProfile profile) {
        super(Text.translatable("text.soundcontrol.profile.editing", profile.name));
        this.parent = parent;
        this.profile = profile;
    }

    @Override
    protected void init() {

        SoundConfig.setEditTarget(this.profile.sounds);

        this.searchBox = new TextFieldWidget(
            this.textRenderer, this.width / 2 - 140, 22, 180, 20, Text.literal("")
        );
        this.searchBox.setChangedListener(this::onSearch);
        this.addSelectableChild(this.searchBox);

        this.addDrawableChild(ButtonWidget.builder(getFilterText(), button -> {
            this.filterMode = (this.filterMode + 1) % 3;
            button.setMessage(getFilterText());
            refilter();
        }).dimensions(this.width / 2 + 50, 22, 100, 20).build());

        int bw = 60;
        int sx = this.width / 2 - (bw * 3 + 10) / 2;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.category.all"),
            b -> setCategory(SoundCategory.ALL)).dimensions(sx, 46, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.category.mobs"),
            b -> setCategory(SoundCategory.MOBS)).dimensions(sx + bw + 5, 46, bw, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.category.blocks"),
            b -> setCategory(SoundCategory.BLOCKS)).dimensions(sx + (bw + 5) * 2, 46, bw, 20).build());

        this.soundList = new SoundListWidget(this.client, this.width, this.height - 116, 72, 25);
        this.addSelectableChild(this.soundList);

        this.modList = new ModListWidget(this.client, 120, this.height - 116, 72, 15, null);
        this.modList.setX(this.width - 120);
        this.addSelectableChild(this.modList);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.mode.basic"), button -> {
            this.viewMode = (this.viewMode + 1) % 3;
            String key = this.viewMode == 0 ? "basic" : (this.viewMode == 1 ? "advanced" : "mods");
            button.setMessage(Text.translatable("text.soundcontrol.mode." + key));
            this.soundList.loadEntries(this.viewMode);
            refilter();
        }).dimensions(this.width / 2 - 160, this.height - 28, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
            .dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("text.soundcontrol.button.reset"), b -> {
                this.profile.sounds.entrySet().removeIf(e -> {
                    SoundConfig.SoundSettings s = e.getValue();
                    s.volume = 1.0f; s.muted = false;
                    return !s.favorite;
                });
                SoundConfig.saveProfile(this.profile);
                this.soundList.loadEntries(this.viewMode);
                refilter();
            }).dimensions(this.width / 2 + 60, this.height - 28, 80, 20).build());

        this.setInitialFocus(this.searchBox);
        this.soundList.loadEntries(this.viewMode);
        refilter();
    }

    private Text getFilterText() {
        if (this.filterMode == 1) return Text.translatable("text.soundcontrol.filter.edited");
        if (this.filterMode == 2) return Text.translatable("text.soundcontrol.filter.favorites");
        return Text.translatable("text.soundcontrol.filter.all");
    }

    private void setCategory(SoundCategory cat) { this.currentCategory = cat; refilter(); }
    private void onSearch(String q) { if (this.soundList != null) refilter(); }
    private void refilter() {
        this.soundList.filter(this.searchBox.getText(), this.currentCategory,
            this.selectedMod, this.viewMode, this.filterMode);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.soundList.render(context, mouseX, mouseY, delta);
        if (this.viewMode == 2) this.modList.render(context, mouseX, mouseY, delta);
        this.searchBox.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);

        context.drawTextWithShadow(this.textRenderer,
            Text.translatable("text.soundcontrol.profile.editing_label", this.profile.name),
            6, 8, 0xFF55FF55);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        if (this.searchBox.keyPressed(keyCode, scanCode, modifiers) || this.searchBox.isActive()) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        SoundConfig.saveProfile(this.profile);
        SoundConfig.clearEditTarget();
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override public boolean shouldPause() { return false; }
}

