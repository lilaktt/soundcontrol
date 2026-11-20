package soundcontrol;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;
import soundcontrol.anchor.SoundAnchorRenderer;
import soundcontrol.gui.SoundControlScreen;
import soundcontrol.render.SoundLookupRenderer;
import soundcontrol.render.SoundWorldRenderer;

final class SoundControlClient {
    static KeyMapping openMenuKey;
    static KeyMapping toggleOverlayKey;
    static KeyMapping muteLookedAtSoundKey;

    private SoundControlClient() {}

    static void init() {
        SoundConfig.load();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(SoundControlClient::registerKeys);
        MinecraftForge.EVENT_BUS.addListener(SoundControlClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(SoundControlClient::onRenderGui);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SoundControlScreen(parent)));
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        openMenuKey = new KeyMapping("key.soundcontrol.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.category.soundcontrol.main");
        toggleOverlayKey = new KeyMapping("key.soundcontrol.toggle_overlay", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.category.soundcontrol.main");
        muteLookedAtSoundKey = new KeyMapping("key.soundcontrol.mute_looked_at", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.category.soundcontrol.main");
        event.register(openMenuKey);
        event.register(toggleOverlayKey);
        event.register(muteLookedAtSoundKey);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || openMenuKey == null) return;
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

    private static void onRenderGui(RenderGuiEvent.Post event) {
        SoundWorldRenderer.render(event.getGuiGraphics());
        SoundLookupRenderer.render(event.getGuiGraphics());
        SoundAnchorRenderer.render(event.getGuiGraphics());
    }
}
