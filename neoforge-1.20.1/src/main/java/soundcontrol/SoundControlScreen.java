package soundcontrol;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class SoundControlScreen extends Screen {
    private EditBox searchBox;
    private SoundListWidget soundList;
    private ModListWidget modList;
    private SoundCategory currentCategory = SoundCategory.ALL;
    private int viewMode = 0;
    public int getViewMode() { return this.viewMode; }
    private String selectedMod = "";
    private int filterMode = 0;

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

        this.searchBox = new EditBox(this.font, this.width / 2 - 140, 22, 180, 20, Component.literal(""));
        this.searchBox.setResponder(this::onSearch);
        this.addWidget(this.searchBox);

        this.addRenderableWidget(Button.builder(getFilterText(), button -> {
            this.filterMode = (this.filterMode + 1) % 3;
            button.setMessage(getFilterText());
            this.soundList.filter(this.searchBox.getValue(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
        }).bounds(this.width / 2 + 50, 22, 100, 20).build());

        int buttonWidth = 60;
        int startX = this.width / 2 - (buttonWidth * 3 + 10) / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.all"), b -> setCategory(SoundCategory.ALL)).bounds(startX, 46, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.mobs"), b -> setCategory(SoundCategory.MOBS)).bounds(startX + buttonWidth + 5, 46, buttonWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.category.blocks"), b -> setCategory(SoundCategory.BLOCKS)).bounds(startX + (buttonWidth + 5) * 2, 46, buttonWidth, 20).build());

        this.soundList = new SoundListWidget(this.minecraft, this.width, this.height, 72, this.height - 44, 25);
        this.soundList.setRenderBackground(false);
        this.addWidget(this.soundList);

        this.modList = new ModListWidget(this.minecraft, 120, this.height, 72, this.height - 44, 15, this);
        this.modList.setLeftPos(this.width - 120);
        this.addWidget(this.modList);

        if (!this.modList.children().isEmpty()) {
            this.selectedMod = ((ModListWidget.ModEntry) this.modList.children().get(0)).getModId();
        }

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.mode.basic"), button -> {
            this.viewMode = (this.viewMode + 1) % 3;
            String modeKey = this.viewMode == 0 ? "basic" : (this.viewMode == 1 ? "advanced" : "mods");
            button.setMessage(Component.translatable("text.soundcontrol.mode." + modeKey));
            this.soundList.loadEntries(this.viewMode);
            this.soundList.filter(this.searchBox.getValue(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
        }).bounds(this.width / 2 - 160, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("text.soundcontrol.button.reset"), button -> {
            SoundConfig.resetSettings();
            this.soundList.loadEntries(this.viewMode);
            this.soundList.filter(this.searchBox.getValue(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
        }).bounds(this.width / 2 + 60, this.height - 28, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("\uD83D\uDD52"), button -> {
            this.minecraft.setScreen(new RecentSoundsPickerScreen(this));
        }).bounds(this.width - 26, this.height - 28, 20, 20).build());

        this.setInitialFocus(this.searchBox);
        this.soundList.loadEntries(this.viewMode);
        this.soundList.filter(this.searchBox.getValue(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
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
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // 1. Background
        this.renderDirtBackground(context);
        // 2. Sound list (behind buttons)
        this.soundList.render(context, mouseX, mouseY, delta);
        // 3. Mod list if in mods mode
        if (this.viewMode == 2 && this.modList != null) {
            this.modList.render(context, mouseX, mouseY, delta);
        }
        // 4. Buttons and other widgets (on top of list)
        super.render(context, mouseX, mouseY, delta);
        // 5. Search box (manually rendered since we use addWidget, not addRenderableWidget)
        this.searchBox.render(context, mouseX, mouseY, delta);
        // 6. Title
        context.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.viewMode == 2 && this.modList != null && this.modList.isMouseOver(mouseX, mouseY)) {
            if (this.modList.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(this.modList);
                if (button == 0) this.setDragging(true);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (this.viewMode == 2 && this.modList != null && this.modList.isMouseOver(mouseX, mouseY)) {
            if (this.modList.mouseScrolled(mouseX, mouseY, amount)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
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
