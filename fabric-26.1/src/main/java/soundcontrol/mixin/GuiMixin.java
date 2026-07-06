package soundcontrol.mixin;

import soundcontrol.anchor.SoundAnchorRenderer;
import soundcontrol.render.SoundLookupRenderer;
import soundcontrol.render.SoundWorldRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import soundcontrol.SoundTracker;
import soundcontrol.render.SoundWorldRenderer;
import soundcontrol.anchor.SoundAnchorRenderer;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {
        SoundWorldRenderer.render(context);
        if (soundcontrol.SoundTracker.getOverlayMode() == 2) {
            soundcontrol.render.SoundLookupRenderer.render(context);
        }
        SoundAnchorRenderer.render(context);
    }
}

