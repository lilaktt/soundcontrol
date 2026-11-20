package soundcontrol.gui;

import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ProfileNameScreen extends Screen {
    private final Screen parent;
    private final SoundConfig.SoundProfile profile;
    private EditBox nameField;
    private Button confirmButton;
    private boolean nameTaken;

    public ProfileNameScreen(Screen parent, SoundConfig.SoundProfile profile) {
        super(Component.translatable(profile == null ? "text.soundcontrol.profile.new_title" : "text.soundcontrol.profile.rename_title"));
        this.parent = parent;
        this.profile = profile;
    }

    @Override
    protected void init() {
        String previous = this.nameField != null ? this.nameField.getValue() : this.profile != null ? this.profile.name : "";
        this.nameField = new EditBox(this.font, this.width / 2 - 100, this.height / 2 - 22, 200, 20,
            Component.translatable("text.soundcontrol.profile.name_hint"));
        this.nameField.setMaxLength(32);
        this.nameField.setValue(previous);
        this.nameField.setResponder(s -> updateConfirm());
        this.addRenderableWidget(this.nameField);

        this.confirmButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> confirm())
            .bounds(this.width / 2 - 102, this.height / 2 + 5, 100, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
            .bounds(this.width / 2 + 2, this.height / 2 + 5, 100, 20).build());

        this.setInitialFocus(this.nameField);
        updateConfirm();
    }

    private void updateConfirm() {
        String name = this.nameField.getValue().trim();
        this.nameTaken = !name.isEmpty() && SoundConfig.isNameTaken(name, this.profile);
        boolean unchanged = this.profile != null && name.equals(this.profile.name);
        this.confirmButton.active = !name.isEmpty() && !this.nameTaken && !unchanged;
    }

    private void confirm() {
        updateConfirm();
        if (!this.confirmButton.active) return;
        String name = this.nameField.getValue().trim();
        if (this.profile == null) SoundConfig.createProfile(name);
        else SoundConfig.renameProfile(this.profile, name);
        if (this.parent instanceof SoundControlScreen screen) screen.refreshAfterProfileChange();
        Compat.setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, SoundControlScreen.BACKDROP);
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 48, 0xFFFFFFFF);
        ctx.drawString(this.font, Component.translatable("text.soundcontrol.profile.name_label"),
            this.width / 2 - 100, this.height / 2 - 34, 0xFFAAAAAA);
        if (this.nameTaken) {
            ctx.drawCenteredString(this.font, Component.translatable("text.soundcontrol.profile.name_taken"),
                this.width / 2, this.height / 2 + 30, 0xFFFF5555);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode;
        if ((key == 257 || key == 335) && this.confirmButton.active) { confirm(); return true; }
        if (key == 256) { this.onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Compat.setScreen(this.parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}
