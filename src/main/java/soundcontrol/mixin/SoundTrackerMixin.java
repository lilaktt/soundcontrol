package soundcontrol.mixin;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import soundcontrol.SoundTracker;

@Mixin(SoundManager.class)
public class SoundTrackerMixin {
    @Inject(method = "play", at = @At("HEAD"))
    private void onPlaySound(SoundInstance sound, CallbackInfoReturnable<?> cir) {
        if (sound != null && sound.getId() != null) {
            SoundTracker.recordSound(sound.getId().toString());
        }
    }
}