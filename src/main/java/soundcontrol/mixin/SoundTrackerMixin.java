package soundcontrol.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import soundcontrol.SoundTracker;
import soundcontrol.SoundWorldRenderer;

@Mixin(SoundManager.class)
public class SoundTrackerMixin {
    @Inject(method = "play", at = @At("HEAD"))
    private void onPlaySound(SoundInstance sound, CallbackInfoReturnable<?> cir) {
        if (sound != null && sound.getIdentifier() != null) {
            String id = sound.getIdentifier().toString();

            // HUD radar overlay
            SoundTracker.recordSound(id);

            // 3D world labels
            SoundWorldRenderer.recordSound(id, sound.getX(), sound.getY(), sound.getZ());
        }
    }
}