package soundcontrol.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import soundcontrol.SoundTracker;
import soundcontrol.SoundWorldRenderer;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics context, DeltaTracker deltaTracker, CallbackInfo ci) {
        SoundWorldRenderer.render(context);
        if (soundcontrol.SoundTracker.getOverlayMode() == 2) {
            soundcontrol.SoundLookupRenderer.render(context);
        }
    }
}