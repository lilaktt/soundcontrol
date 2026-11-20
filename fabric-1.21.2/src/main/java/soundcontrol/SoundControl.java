package soundcontrol;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import soundcontrol.anchor.SoundAnchorRenderer;
import soundcontrol.gui.SoundControlScreen;
import soundcontrol.render.SoundLookupRenderer;
import soundcontrol.render.SoundWorldRenderer;

public class SoundControl implements ClientModInitializer {
    public static KeyBinding openMenuKey;
    public static KeyBinding toggleOverlayKey;
    public static KeyBinding muteLookedAtSoundKey;

    @Override
    public void onInitializeClient() {
        SoundConfig.load();
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.soundcontrol.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.soundcontrol.main"));
        toggleOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.soundcontrol.toggle_overlay", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "category.soundcontrol.main"));
        muteLookedAtSoundKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.soundcontrol.mute_looked_at", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.soundcontrol.main"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (Compat.noScreen()) Compat.setScreen(new SoundControlScreen());
            }
            while (toggleOverlayKey.wasPressed()) {
                SoundTracker.cycleOverlayMode();
            }
            while (muteLookedAtSoundKey.wasPressed()) {
                if (Compat.noScreen() && Compat.inWorld()) SoundWorldRenderer.toggleSoundUnderCrosshair();
            }
            SoundTracker.clientTick();
        });
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
        SoundWorldRenderer.render(context);
        SoundLookupRenderer.render(context);
        SoundAnchorRenderer.render(context);
        });
    }
}
