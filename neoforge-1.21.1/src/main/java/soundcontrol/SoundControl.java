package soundcontrol;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.blaze3d.platform.InputConstants;
import soundcontrol.gui.SoundControlScreen;
import soundcontrol.render.SoundWorldRenderer;

@Mod(value = "soundcontrol", dist = Dist.CLIENT)
public class SoundControl {
    public static KeyMapping openMenuKey;
    public static KeyMapping toggleOverlayKey;
    public static KeyMapping muteLookedAtSoundKey;

    public SoundControl(IEventBus modEventBus, ModContainer container) {
        SoundConfig.load();
        modEventBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SoundControlScreen(parent));
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        openMenuKey = new KeyMapping("key.soundcontrol.open", InputConstants.KEY_V, "key.category.soundcontrol.main");
        toggleOverlayKey = new KeyMapping("key.soundcontrol.toggle_overlay", InputConstants.KEY_Y, "key.category.soundcontrol.main");
        muteLookedAtSoundKey = new KeyMapping("key.soundcontrol.mute_looked_at", InputConstants.UNKNOWN.getValue(), "key.category.soundcontrol.main");
        event.register(openMenuKey);
        event.register(toggleOverlayKey);
        event.register(muteLookedAtSoundKey);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        if (openMenuKey == null) return;
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
    }
}
