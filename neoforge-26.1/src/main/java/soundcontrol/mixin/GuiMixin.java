package soundcontrol.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import soundcontrol.anchor.SoundAnchorRenderer;
import soundcontrol.render.SoundLookupRenderer;
import soundcontrol.render.SoundWorldRenderer;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void soundcontrol$renderOverlays(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {
        SoundWorldRenderer.render(context);
        SoundLookupRenderer.render(context);
        SoundAnchorRenderer.render(context);
    }
}
