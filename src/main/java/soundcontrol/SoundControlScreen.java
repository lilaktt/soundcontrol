package soundcontrol;

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
    private SoundCategory currentCategory = SoundCategory.ALL;
    private int viewMode = 0;
    private String selectedMod = "";

    // Наша єдина правильна змінна! 0 = Всі, 1 = Змінені, 2 = Обрані
    private int filterMode = 0;

    public SoundControlScreen() {
        super(Text.translatable("text.soundcontrol.title"));
    }

    private Text getFilterText() {
        if (this.filterMode == 1) return Text.translatable("text.soundcontrol.filter.edited");
        if (this.filterMode == 2) return Text.translatable("text.soundcontrol.filter.favorites");
        return Text.translatable("text.soundcontrol.filter.all");
    }

    @Override
    protected void init() {
        this.searchBox = new TextFieldWidget(this.textRenderer, this.width / 2 - 140, 22, 180, 20, Text.literal(""));
        this.searchBox.setChangedListener(this::onSearch);
        this.addSelectableChild(this.searchBox);

        this.addDrawableChild(ButtonWidget.builder(getFilterText(), button -> {
            this.filterMode = (this.filterMode + 1) % 3;
            button.setMessage(getFilterText());
            this.soundList.filter(this.searchBox.getText(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
        }).dimensions(this.width / 2 + 50, 22, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.button.radar_position"), button -> {
            this.client.setScreen(new RadarPositionScreen(this));
        }).dimensions(this.width - 110, 5, 100, 20).build());

        int buttonWidth = 60;
        int startX = this.width / 2 - (buttonWidth * 3 + 10) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.category.all"), b -> setCategory(SoundCategory.ALL)).dimensions(startX, 46, buttonWidth, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.category.mobs"), b -> setCategory(SoundCategory.MOBS)).dimensions(startX + buttonWidth + 5, 46, buttonWidth, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.category.blocks"), b -> setCategory(SoundCategory.BLOCKS)).dimensions(startX + (buttonWidth + 5) * 2, 46, buttonWidth, 20).build());

        this.soundList = new SoundListWidget(this.client, this.width, this.height - 116, 72, 25);
        this.addSelectableChild(this.soundList);

        this.modList = new ModListWidget(this.client, 120, this.height - 116, 72, 15, this);
        this.modList.setX(this.width - 120);
        this.addSelectableChild(this.modList);

        if (!this.modList.children().isEmpty()) {
            this.selectedMod = ((ModListWidget.ModEntry) this.modList.children().get(0)).getModId();
        }

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.mode.basic"), button -> {
            this.viewMode = (this.viewMode + 1) % 3;
            String modeKey = this.viewMode == 0 ? "basic" : (this.viewMode == 1 ? "advanced" : "mods");
            button.setMessage(Text.translatable("text.soundcontrol.mode." + modeKey));
            this.soundList.loadEntries(this.viewMode);
            this.soundList.filter(this.searchBox.getText(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
        }).dimensions(this.width / 2 - 160, this.height - 28, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> this.close())
                .dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("text.soundcontrol.button.reset"), button -> {
            SoundConfig.resetSettings();
            this.soundList.loadEntries(this.viewMode);
            this.soundList.filter(this.searchBox.getText(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
        }).dimensions(this.width / 2 + 60, this.height - 28, 100, 20).build());

        this.setInitialFocus(this.searchBox);
        this.soundList.loadEntries(this.viewMode);
        this.soundList.filter(this.searchBox.getText(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
    }

    private void setCategory(SoundCategory category) {
        this.currentCategory = category;
        this.soundList.filter(this.searchBox.getText(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
    }

    public void setSelectedMod(String modId) {
        this.selectedMod = modId;
        this.soundList.filter(this.searchBox.getText(), this.currentCategory, this.selectedMod, this.viewMode, this.filterMode);
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.soundList.render(context, mouseX, mouseY, delta);

        if (this.viewMode == 2) {
            this.modList.render(context, mouseX, mouseY, delta);
        }

        this.searchBox.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) {
            this.close();
            return true;
        }
        if (this.searchBox.keyPressed(input) || this.searchBox.isActive()) {
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}