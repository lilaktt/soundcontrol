package soundcontrol;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
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

    private void registerKeys(RegisterKeyMappingsEvent event) {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("soundcontrol", "main"));

        openMenuKey = new KeyMapping(
                "key.soundcontrol.open",
                GLFW.GLFW_KEY_V,
                category
        );

        toggleOverlayKey = new KeyMapping(
                "key.soundcontrol.toggle_overlay",
                GLFW.GLFW_KEY_Y,
                category
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
