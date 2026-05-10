package soundcontrol.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import soundcontrol.SoundTracker;
import soundcontrol.SoundWorldRenderer;

@Mixin(SoundManager.class)
public class SoundTrackerMixin {
    @Inject(method = "play", at = @At("HEAD"))
    private void onPlaySound(SoundInstance sound, CallbackInfo ci) {
        if (sound != null && sound.getLocation() != null) {
            String id = sound.getLocation().toString();
            SoundTracker.recordSound(id);
            SoundWorldRenderer.recordSound(sound, id);
        }
    }

    @Inject(method = "queueTickingSound", at = @At("HEAD"), require = 0)
    private void onQueueTickingSound(TickableSoundInstance sound, CallbackInfo ci) {
        if (sound != null && sound.getLocation() != null) {
            String id = sound.getLocation().toString();
            SoundTracker.recordSound(id);
            SoundWorldRenderer.recordSound(sound, id);
        }
    }
}
