package soundcontrol.gui;

import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

public class ProfileNameScreen extends Screen {
    private final Screen parent;
    private final SoundConfig.SoundProfile profile;
    private TextFieldWidget nameField;
    private ButtonWidget confirmButton;
    private boolean nameTaken;

    public ProfileNameScreen(Screen parent, SoundConfig.SoundProfile profile) {
        super(Text.translatable(profile == null ? "text.soundcontrol.profile.new_title" : "text.soundcontrol.profile.rename_title"));
        this.parent = parent;
        this.profile = profile;
    }

    @Override
    protected void init() {
        String previous = this.nameField != null ? this.nameField.getText() : this.profile != null ? this.profile.name : "";
        this.nameField = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, this.height / 2 - 22, 200, 20,
            Text.translatable("text.soundcontrol.profile.name_hint"));
        this.nameField.setMaxLength(32);
        this.nameField.setText(previous);
        this.nameField.setChangedListener(s -> updateConfirm());
        this.addDrawableChild(this.nameField);

        this.confirmButton = this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> confirm())
            .dimensions(this.width / 2 - 102, this.height / 2 + 5, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> this.close())
            .dimensions(this.width / 2 + 2, this.height / 2 + 5, 100, 20).build());

        this.setInitialFocus(this.nameField);
        updateConfirm();
    }

    private void updateConfirm() {
        String name = this.nameField.getText().trim();
        this.nameTaken = !name.isEmpty() && SoundConfig.isNameTaken(name, this.profile);
        boolean unchanged = this.profile != null && name.equals(this.profile.name);
        this.confirmButton.active = !name.isEmpty() && !this.nameTaken && !unchanged;
    }

    private void confirm() {
        updateConfirm();
        if (!this.confirmButton.active) return;
        String name = this.nameField.getText().trim();
        if (this.profile == null) SoundConfig.createProfile(name);
        else SoundConfig.renameProfile(this.profile, name);
        if (this.parent instanceof SoundControlScreen screen) screen.refreshAfterProfileChange();
        Compat.setScreen(this.parent);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, SoundControlScreen.BACKDROP);
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 48, 0xFFFFFFFF);
        ctx.drawText(this.textRenderer, Text.translatable("text.soundcontrol.profile.name_label"),
            this.width / 2 - 100, this.height / 2 - 34, 0xFFAAAAAA, true);
        if (this.nameTaken) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("text.soundcontrol.profile.name_taken"),
                this.width / 2, this.height / 2 + 30, 0xFFFF5555);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if ((key == 257 || key == 335) && this.confirmButton.active) { confirm(); return true; }
        if (key == 256) { this.close(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        Compat.setScreen(this.parent);
    }

    @Override public boolean shouldPause() { return false; }
}
