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

import java.io.File;

public class ProfileRenameScreen extends Screen {
    private final Screen parent;
    private final SoundConfig.SoundProfile profile;
    private final ProfileListWidget profileList;
    private EditBox nameField;
    private Button confirmButton;

    public ProfileRenameScreen(Screen parent, SoundConfig.SoundProfile profile, ProfileListWidget list) {
        super(Component.translatable("text.soundcontrol.profile.rename_title"));
        this.parent = parent;
        this.profile = profile;
        this.profileList = list;
    }

    @Override
    protected void init() {
        this.nameField = new EditBox(this.font,
            this.width / 2 - 100, this.height / 2 - 22, 200, 20, Component.literal(""));
        this.nameField.setMaxLength(32);
        this.nameField.setValue(this.profile.name);
        this.nameField.setResponder(s -> updateConfirm());
        this.addWidget(this.nameField);
        this.addRenderableWidget(this.nameField);

        this.confirmButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> confirm())
            .bounds(this.width / 2 - 102, this.height / 2 + 5, 100, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL,
            b -> this.minecraft.setScreen(this.parent))
            .bounds(this.width / 2 + 2, this.height / 2 + 5, 100, 20).build());

        this.setInitialFocus(this.nameField);
        updateConfirm();
    }

    private void updateConfirm() {
        String n = this.nameField.getValue().trim();
        boolean unique = SoundConfig.getProfiles().stream()
            .noneMatch(p -> p != this.profile && p.name.equalsIgnoreCase(n));
        this.confirmButton.active = !n.isEmpty() && unique;
    }

    private void confirm() {
        String newName = this.nameField.getValue().trim();
        if (newName.isEmpty()) return;
        if (this.profile.file != null && this.profile.file.exists() && !this.profile.name.equals("default"))
            this.profile.file.delete();
        this.profile.name = newName;
        this.profile.file = new File(SoundConfig.CONFIGS_DIR,
            newName.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase() + ".json");
        SoundConfig.saveProfile(this.profile);
        if (SoundConfig.getActiveProfile() == this.profile) SoundConfig.saveSettings();
        if (this.profileList != null) this.profileList.reload();
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

