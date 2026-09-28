package soundcontrol;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

@Mod("soundcontrol")
public class SoundControl {
    public static KeyMapping openMenuKey;
    public static KeyMapping toggleOverlayKey;
    public static KeyMapping muteLookedAtSoundKey;

    public SoundControl() {
        SoundConfig.load();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerKeys);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        openMenuKey = new KeyMapping(
                "key.soundcontrol.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "key.category.soundcontrol.main"
        );
        toggleOverlayKey = new KeyMapping(
                "key.soundcontrol.toggle_overlay",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                "key.category.soundcontrol.main"
        );
        muteLookedAtSoundKey = new KeyMapping("key.soundcontrol.mute_looked_at", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.category.soundcontrol.main");
        event.register(muteLookedAtSoundKey);
        event.register(openMenuKey);
        event.register(toggleOverlayKey);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        while (openMenuKey.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(new SoundControlScreen());
            }
        }
        while (muteLookedAtSoundKey.consumeClick()) {
            if (client.screen == null) SoundWorldRenderer.toggleSoundUnderCrosshair(client);
        }
        while (toggleOverlayKey.consumeClick()) {
            SoundTracker.cycleOverlayMode();
        }
        if (SoundTracker.getOverlayMode() == 2) {
            SoundLookupRenderer.tick(client);
        }
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        SoundWorldRenderer.render(event.getGuiGraphics());
        soundcontrol.SoundAnchorRenderer.render(event.getGuiGraphics());
        if (SoundTracker.getOverlayMode() == 2) {
            SoundLookupRenderer.render(event.getGuiGraphics());
        }
    }
}
