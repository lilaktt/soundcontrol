package soundcontrol;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class SoundControl implements ClientModInitializer {
    private static KeyBinding openMenuKey;
    private static KeyBinding toggleOverlayKey;

    @Override
    public void onInitializeClient() {
        SoundConfig.load();

        KeyBinding.Category category = new KeyBinding.Category(Identifier.of("soundcontrol", "main"));

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.soundcontrol.open",
                GLFW.GLFW_KEY_V,
                category
        ));

        toggleOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.soundcontrol.toggle_overlay",
                GLFW.GLFW_KEY_Y,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new SoundControlScreen());
                }
            }
            while (toggleOverlayKey.wasPressed()) {
                SoundTracker.showOverlay = !SoundTracker.showOverlay;
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            SoundTracker.render(drawContext);
        });
    }
}