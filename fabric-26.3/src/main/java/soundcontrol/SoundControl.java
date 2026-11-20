package soundcontrol;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import soundcontrol.gui.SoundControlScreen;
import soundcontrol.render.SoundWorldRenderer;

public class SoundControl implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("soundcontrol", "main"));
    public static KeyMapping openMenuKey;
    public static KeyMapping toggleOverlayKey;
    public static KeyMapping muteLookedAtSoundKey;

    @Override
    public void onInitializeClient() {
        SoundConfig.load();
        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.soundcontrol.open", InputConstants.KEY_V, CATEGORY));
        toggleOverlayKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.soundcontrol.toggle_overlay", InputConstants.KEY_Y, CATEGORY));
        muteLookedAtSoundKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.soundcontrol.mute_looked_at", InputConstants.UNKNOWN.getValue(), CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (Compat.noScreen()) Compat.setScreen(new SoundControlScreen());
            }
            while (toggleOverlayKey.consumeClick()) {
                SoundTracker.cycleOverlayMode();
            }
            while (muteLookedAtSoundKey.consumeClick()) {
                if (Compat.noScreen() && Compat.inWorld()) SoundWorldRenderer.toggleSoundUnderCrosshair();
            }
            SoundTracker.clientTick();
        });
    }
}
