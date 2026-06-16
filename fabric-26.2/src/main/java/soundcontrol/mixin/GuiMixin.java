package soundcontrol.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import soundcontrol.SoundTracker;
import soundcontrol.SoundWorldRenderer;
import soundcontrol.SoundAnchorRenderer;
import soundcontrol.SoundLookupRenderer;

@Mixin(Hud.class)
public class GuiMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {
        // 26.2: Hud.extractRenderState(GuiGraphicsExtractor, DeltaTracker) has the graphics context
        SoundWorldRenderer.render(context);
        if (SoundTracker.getOverlayMode() == 2) {
            SoundLookupRenderer.render(context);
        }
        SoundAnchorRenderer.render(context);
    }
}
