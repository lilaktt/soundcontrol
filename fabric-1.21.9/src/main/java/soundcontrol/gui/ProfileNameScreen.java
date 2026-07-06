package soundcontrol.gui;

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

public class ProfileNameScreen extends Screen {
    private final Screen parent;
    private final ProfileListWidget profileList;
    private TextFieldWidget nameField;
    private ButtonWidget confirmButton;

    public ProfileNameScreen(Screen parent, ProfileListWidget list) {
        super(Text.translatable("text.soundcontrol.profile.new_title"));
        this.parent = parent;
        this.profileList = list;
    }

    @Override
    protected void init() {
        this.nameField = new TextFieldWidget(this.textRenderer,
            this.width / 2 - 100, this.height / 2 - 22, 200, 20,
            Text.translatable("text.soundcontrol.profile.name_hint"));
        this.nameField.setMaxLength(32);
        this.nameField.setChangedListener(s -> updateConfirm());
        this.addSelectableChild(this.nameField);
        this.setInitialFocus(this.nameField);

        this.confirmButton = this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> confirm())
            .dimensions(this.width / 2 - 102, this.height / 2 + 5, 100, 20).build());
        this.confirmButton.active = false;

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL,
            b -> this.client.setScreen(this.parent))
            .dimensions(this.width / 2 + 2, this.height / 2 + 5, 100, 20).build());
    }

    private void updateConfirm() {
        String name = this.nameField.getText().trim();
        this.confirmButton.active = !name.isEmpty() &&
            SoundConfig.getProfiles().stream().noneMatch(p -> p.name.equalsIgnoreCase(name));
    }

    private void confirm() {
        String name = this.nameField.getText().trim();
        if (name.isEmpty()) return;

        SoundConfig.SoundProfile profile = SoundConfig.createProfile(name);
        SoundConfig.switchProfile(profile.name);
        if (this.profileList != null) this.profileList.reload();
        if (this.parent instanceof SoundControlScreen sc) sc.refreshAfterProfileChange();
        this.client.setScreen(this.parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {

        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 48, 0xFFFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer,
            Text.translatable("text.soundcontrol.profile.name_label"),
            this.width / 2 - 100, this.height / 2 - 36, 0xAAAAAA);
        this.nameField.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER && this.confirmButton.active) { confirm(); return true; }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { this.client.setScreen(this.parent); return true; }
        return this.nameField.keyPressed(input) || super.keyPressed(input);
    }

    @Override public boolean shouldPause() { return false; }
}

