package soundcontrol;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class SoundControl implements ClientModInitializer {
    public static final KeyMapping.Category SOUND_CONTROL = KeyMapping.Category.register(Identifier.parse("soundcontrol:main"));
    public static KeyMapping openMenuKey;
    public static KeyMapping toggleOverlayKey;

    @Override
    public void onInitializeClient() {
        SoundConfig.load();

        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.soundcontrol.open",
                GLFW.GLFW_KEY_V,
                SOUND_CONTROL
        ));

        toggleOverlayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.soundcontrol.toggle_overlay",
                GLFW.GLFW_KEY_Y,
                SOUND_CONTROL
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new SoundControlScreen());
                }
            }
            while (toggleOverlayKey.consumeClick()) {
                SoundTracker.showOverlay = !SoundTracker.showOverlay;
                SoundWorldRenderer.enabled = SoundTracker.showOverlay;
            }
        });

    }
}
