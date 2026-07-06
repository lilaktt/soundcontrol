package soundcontrol.gui;

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

public class ProfileNameScreen extends Screen {
    private final Screen parent;
    private final ProfileListWidget profileList;
    private EditBox nameField;
    private Button confirmButton;

    public ProfileNameScreen(Screen parent, ProfileListWidget list) {
        super(Component.translatable("text.soundcontrol.profile.new_title"));
        this.parent = parent;
        this.profileList = list;
    }

    @Override
    protected void init() {
        this.nameField = new EditBox(this.font,
            this.width / 2 - 100, this.height / 2 - 22, 200, 20,
            Component.translatable("text.soundcontrol.profile.name_hint"));
        this.nameField.setMaxLength(32);
        this.nameField.setResponder(s -> updateConfirm());
        this.addWidget(this.nameField);
        this.addRenderableWidget(this.nameField);

        this.confirmButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> confirm())
            .bounds(this.width / 2 - 102, this.height / 2 + 5, 100, 20).build());
        this.confirmButton.active = false;

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL,
            b -> this.minecraft.setScreen(this.parent))
            .bounds(this.width / 2 + 2, this.height / 2 + 5, 100, 20).build());

        this.setInitialFocus(this.nameField);
    }

    private void updateConfirm() {
        String n = this.nameField.getValue().trim();
        this.confirmButton.active = !n.isEmpty() &&
            SoundConfig.getProfiles().stream().noneMatch(p -> p.name.equalsIgnoreCase(n));
    }

    private void confirm() {
        String name = this.nameField.getValue().trim();
        if (name.isEmpty()) return;
        SoundConfig.SoundProfile profile = SoundConfig.createProfile(name);
        SoundConfig.switchProfile(profile.name);
        if (this.profileList != null) this.profileList.reload();
        if (this.parent instanceof SoundControlScreen sc) sc.refreshAfterProfileChange();
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        ctx.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 48, 0xFFFFFFFF);
        ctx.text(this.font, Component.translatable("text.soundcontrol.profile.name_label"),
            this.width / 2 - 100, this.height / 2 - 36, 0xAAAAAA, false);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER && this.confirmButton.active) { confirm(); return true; }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { this.minecraft.setScreen(this.parent); return true; }
        return this.nameField.keyPressed(input) || super.keyPressed(input);
    }

    @Override public boolean isPauseScreen() { return false; }
}

