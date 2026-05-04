package soundcontrol;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class SoundControlScreen extends Screen {
    private EditBox searchBox;
    private SoundListWidget soundList;
    private ModListWidget modList;
    private SoundCategory currentCategory = SoundCategory.ALL;
    private int viewMode = 0;
    private String selectedMod = "";


    private int filterMode = 0;
    private final Screen parent;

    public SoundControlScreen(Screen parent) {
        super(Component.translatable("text.soundcontrol.title"));
        this.parent = parent;
    }

    public SoundControlScreen() {
        this(null);
    }

    private Component getFilterText() {
        if (this.filterMode == 1) return Component.translatable("text.soundcontrol.filter.edited");
        if (this.filterMode == 2) return Component.translatable("text.soundcontrol.filter.favorites");
        return Component.translatable("text.soundcontrol.filter.all");
    }

    @Override
    protected void init() {
        this.searchBox = new EditBox(this.font, this.width / 2 - 140, 22, 180, 20, Component.literal(""));
        this.searchBox.setResponder(this::onSearch);
        this.addWidget(this.searchBox);
        this.addRenderableWidget(this.searchBox);

        this.addRenderableWidget(Button.builder(getFilterText(), button -> {
            this.filterMode = (this.filterMode + 1) % 3;
            button.setMessage(getFilterText());
            this.soundList.filter(this.searchBox.getValue(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
        }).bounds(this.width / 2 + 50, 22, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.button.radar_position"), button -> {
            this.minecraft.setScreen(new RadarPositionScreen(this));
        }).bounds(this.width - 110, 5, 100, 20).build());

        int buttonWidth = 60;
        int startX = this.width / 2 - (buttonWidth * 3 + 10) / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.all"), b -> setCategory(SoundCategory.ALL)).bounds(startX, 46, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.mobs"), b -> setCategory(SoundCategory.MOBS)).bounds(startX + buttonWidth + 5, 46, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.blocks"), b -> setCategory(SoundCategory.BLOCKS)).bounds(startX + (buttonWidth + 5) * 2, 46, buttonWidth, 20).build());

        this.soundList = new SoundListWidget(this.minecraft, this.width, this.height - 116, 72, 25);
        this.addWidget(this.soundList);
        this.addRenderableWidget(this.soundList);

        this.modList = new ModListWidget(this.minecraft, 120, this.height - 116, 72, 15, this);
        this.modList.setX(this.width - 120);
        this.addWidget(this.modList);
        this.addRenderableWidget(this.modList);

        if (!this.modList.children().isEmpty()) {
            this.selectedMod = ((ModListWidget.ModEntry) this.modList.children().get(0)).getModId();
        }

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.mode.basic"), button -> {
            this.viewMode = (this.viewMode + 1) % 3;
            String modeKey = this.viewMode == 0 ? "basic" : (this.viewMode == 1 ? "advanced" : "mods");
            button.setMessage(Component.translatable("text.soundcontrol.mode." + modeKey));
            if (this.viewMode != 2) {
                this.modList.active = false;
                this.modList.visible = false;
            } else {
                this.modList.active = true;
                this.modList.visible = true;
            }
            this.soundList.loadEntries(this.viewMode);
            this.soundList.filter(this.searchBox.getValue(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
        }).bounds(this.width / 2 - 160, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.button.reset"), button -> {
            SoundConfig.resetSettings();
            this.soundList.loadEntries(this.viewMode);
            this.soundList.filter(this.searchBox.getValue(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
        }).bounds(this.width / 2 + 60, this.height - 28, 100, 20).build());

        this.setInitialFocus(this.searchBox);
        this.soundList.loadEntries(this.viewMode);
        this.soundList.filter(this.searchBox.getValue(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
        
        if (this.viewMode != 2) {
            this.modList.active = false;
            this.modList.visible = false;
        }
    }

    private void setCategory(SoundCategory category) {
        this.currentCategory = category;
        this.soundList.filter(this.searchBox.getValue(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
    }

    public void setSelectedMod(String modId) {
        this.selectedMod = modId;
        this.soundList.filter(this.searchBox.getValue(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
    }

    public String getSelectedMod() {
        return this.selectedMod;
    }

    private void onSearch(String query) {
        if (this.soundList != null) {
            this.soundList.filter(query, this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) {
            this.onClose();
            return true;
        }
        if (this.searchBox.keyPressed(input) || this.searchBox.isFocused()) {
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        } else {
            super.onClose();
        }
    }
}
