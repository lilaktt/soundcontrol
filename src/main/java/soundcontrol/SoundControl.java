package soundcontrol;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;

@Mod("soundcontrol")
public class SoundControl {
    public static KeyMapping openMenuKey;
    public static KeyMapping toggleOverlayKey;

    public SoundControl(IEventBus modEventBus) {
        SoundConfig.load();
        modEventBus.addListener(this::registerKeys);
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(this::onRenderGuiOverlay);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        openMenuKey = new KeyMapping(
                "key.soundcontrol.open",
                GLFW.GLFW_KEY_V,
                "key.category.soundcontrol.main"
        );

        toggleOverlayKey = new KeyMapping(
                "key.soundcontrol.toggle_overlay",
                GLFW.GLFW_KEY_Y,
                "key.category.soundcontrol.main"
        );

        event.register(openMenuKey);
        event.register(toggleOverlayKey);
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        while (openMenuKey.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(new SoundControlScreen());
            }
        }
        while (toggleOverlayKey.consumeClick()) {
            SoundTracker.showOverlay = !SoundTracker.showOverlay;
            SoundWorldRenderer.enabled = SoundTracker.showOverlay;
        }
    }

    private void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()) {
            SoundWorldRenderer.render(event.getGuiGraphics());
        }
    }
}
