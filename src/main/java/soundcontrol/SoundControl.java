package soundcontrol;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod("soundcontrol")
public class SoundControl {
    public static KeyMapping openMenuKey;
    public static KeyMapping toggleOverlayKey;

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
                GLFW.GLFW_KEY_V,
                CATEGORY
        );
        toggleOverlayKey = new KeyMapping(
                "key.soundcontrol.toggle_overlay",
                GLFW.GLFW_KEY_Y,
                CATEGORY
        );

        event.register(openMenuKey);
        event.register(toggleOverlayKey);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        while (openMenuKey.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(new SoundControlScreen());
            }
        }
        while (toggleOverlayKey.consumeClick()) {
            SoundTracker.showOverlay = !SoundTracker.showOverlay;
        }
    }
}