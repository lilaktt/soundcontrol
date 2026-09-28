package soundcontrol;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.blaze3d.platform.InputConstants;
import soundcontrol.render.SoundLookupRenderer;

@Mod("soundcontrol")
public class SoundControl {
    public static KeyMapping openMenuKey;
    public static KeyMapping toggleOverlayKey;
    public static KeyMapping muteLookedAtSoundKey;

    public SoundControl(IEventBus modEventBus) {
        SoundConfig.load();
        modEventBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    public static final net.minecraft.client.KeyMapping.Category CATEGORY = net.minecraft.client.KeyMapping.Category.register(
            net.minecraft.resources.Identifier.tryParse("soundcontrol:main")
    );

    private void registerKeys(RegisterKeyMappingsEvent event) {
        openMenuKey = new KeyMapping(
                "key.soundcontrol.open",
                InputConstants.KEY_V,
                CATEGORY
        );
        toggleOverlayKey = new KeyMapping(
                "key.soundcontrol.toggle_overlay",
                InputConstants.KEY_Y,
                CATEGORY
        );

        muteLookedAtSoundKey = new KeyMapping("key.soundcontrol.mute_looked_at", InputConstants.UNKNOWN.getValue(), CATEGORY);
        event.register(muteLookedAtSoundKey);
        event.register(openMenuKey);
        event.register(toggleOverlayKey);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        while (openMenuKey.consumeClick()) {
            if (client.canInterruptScreen()) {
                client.setScreenAndShow(new SoundControlScreen());
            }
        }
        while (muteLookedAtSoundKey.consumeClick()) {
            if (client.gui.screen() == null) SoundWorldRenderer.toggleSoundUnderCrosshair(client);
        }
        while (toggleOverlayKey.consumeClick()) {
            SoundTracker.cycleOverlayMode();
        }
        if (SoundTracker.getOverlayMode() == 2) {
            SoundLookupRenderer.tick(client);
        }
    }
}