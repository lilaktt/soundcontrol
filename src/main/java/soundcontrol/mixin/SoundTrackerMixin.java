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
            String id = sound.getId().toString();
            SoundTracker.recordSound(id);
            soundcontrol.SoundWorldRenderer.recordSound(sound, id);
        }
    }

    @Inject(method = {"playNextTick", "play(Lnet/minecraft/client/sound/TickableSoundInstance;)V"}, at = @At("HEAD"))
    private void onPlayNextTick(net.minecraft.client.sound.TickableSoundInstance sound, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (sound != null && sound.getId() != null) {
            String id = sound.getId().toString();
            SoundTracker.recordSound(id);
            soundcontrol.SoundWorldRenderer.recordSound(sound, id);
        }
    }
}
