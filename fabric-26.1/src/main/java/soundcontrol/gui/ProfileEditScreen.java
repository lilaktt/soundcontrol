package soundcontrol.gui;

import soundcontrol.SoundCategory;
import soundcontrol.SoundConfig;
import soundcontrol.SoundControl;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class ProfileEditScreen extends Screen {
    private final Screen parent;
    private final SoundConfig.SoundProfile profile;

    private EditBox searchBox;
    private SoundListWidget soundList;
    private ModListWidget modList;
    private SoundCategory currentCategory = SoundCategory.ALL;
    private int viewMode = 0;
    private String selectedMod = "";
    private int filterMode = 0;

    public ProfileEditScreen(Screen parent, SoundConfig.SoundProfile profile) {
        super(Component.translatable("text.soundcontrol.profile.editing", profile.name));
        this.parent = parent;
        this.profile = profile;
    }

    @Override
    protected void init() {
        SoundConfig.setEditTarget(this.profile.sounds);

        this.searchBox = new EditBox(this.font, this.width / 2 - 140, 22, 180, 20, Component.literal(""));
        this.searchBox.setResponder(q -> refilter());
        this.addWidget(this.searchBox);
        this.addRenderableWidget(this.searchBox);

        this.addRenderableWidget(Button.builder(getFilterText(), button -> {
            this.filterMode = (this.filterMode + 1) % 3;
            button.setMessage(getFilterText());
            refilter();
        }).bounds(this.width / 2 + 50, 22, 100, 20).build());

        int bw = 60, sx = this.width / 2 - (bw * 3 + 10) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.all"),
            b -> setCategory(SoundCategory.ALL)).bounds(sx, 46, bw, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.mobs"),
            b -> setCategory(SoundCategory.MOBS)).bounds(sx + bw + 5, 46, bw, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.blocks"),
            b -> setCategory(SoundCategory.BLOCKS)).bounds(sx + (bw + 5) * 2, 46, bw, 20).build());

        this.soundList = new SoundListWidget(this.minecraft, this.width, this.height - 116, 72, 25);
        this.addRenderableWidget(this.soundList);

        this.modList = new ModListWidget(this.minecraft, 120, this.height - 116, 72, 15, null);
        this.modList.setX(this.width - 120);
        this.modList.active = false;
        this.modList.visible = false;

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.mode.basic"), button -> {
            this.viewMode = (this.viewMode + 1) % 3;
            String key = this.viewMode == 0 ? "basic" : (this.viewMode == 1 ? "advanced" : "mods");
            button.setMessage(Component.translatable("text.soundcontrol.mode." + key));
            if (this.viewMode != 2) {
                this.modList.active = false; this.modList.visible = false;
                this.soundList.setWidth(this.width);
            } else {
                this.modList.active = true; this.modList.visible = true;
                this.soundList.setWidth(this.width - 120);
            }
            this.soundList.loadEntries(this.viewMode);
            refilter();
        }).bounds(this.width / 2 - 160, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.button.reset"), b -> {
            this.profile.sounds.entrySet().removeIf(e -> {
                SoundConfig.SoundSettings s = e.getValue();
                s.volume = 1.0f; s.muted = false; return !s.favorite;
            });
            SoundConfig.saveProfile(this.profile);
            this.soundList.loadEntries(this.viewMode); refilter();
        }).bounds(this.width / 2 + 60, this.height - 28, 80, 20).build());

        this.setInitialFocus(this.searchBox);
        this.soundList.loadEntries(this.viewMode);
        refilter();
    }

    private Component getFilterText() {
        if (this.filterMode == 1) return Component.translatable("text.soundcontrol.filter.edited");
        if (this.filterMode == 2) return Component.translatable("text.soundcontrol.filter.favorites");
        return Component.translatable("text.soundcontrol.filter.all");
    }

    private void setCategory(SoundCategory cat) { this.currentCategory = cat; refilter(); }
    private void refilter() {
        if (this.soundList != null)
            this.soundList.filter(this.searchBox.getValue(), this.currentCategory,
                this.selectedMod, this.viewMode, this.filterMode);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        context.text(this.font,
            Component.translatable("text.soundcontrol.profile.editing_label", this.profile.name),
            6, 8, 0xFF55FF55, true);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (this.searchBox.keyPressed(input) || this.searchBox.isFocused()) return true;
        return super.keyPressed(input);
    }

    @Override
    public void onClose() {
        SoundConfig.saveProfile(this.profile);
        SoundConfig.clearEditTarget();
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}

