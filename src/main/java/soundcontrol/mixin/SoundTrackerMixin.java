package soundcontrol.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import soundcontrol.SoundTracker;

@Mixin(SoundManager.class)
public class SoundTrackerMixin {
    @Inject(method = "play", at = @At("HEAD"))
    private void soundcontrol_onPlay(SoundInstance sound, CallbackInfoReturnable<?> ci) {
        if (sound != null && sound.getLocation() != null) {
            SoundTracker.recordSound(sound.getLocation().toString());
        }
    }
}
