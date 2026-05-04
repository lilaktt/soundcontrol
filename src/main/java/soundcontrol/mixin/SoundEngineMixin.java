package soundcontrol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import soundcontrol.SoundConfig;

@Mixin(SoundSystem.class)
public class SoundEngineMixin {
    @WrapOperation(
            method = "play",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F")
    )
    private float amplifyVolume(SoundSystem instance, float volume, SoundCategory category, Operation<Float> original, SoundInstance sound) {
        float result = original.call(instance, volume, category);
        String id = sound.getId().toString();
        float modifier = SoundConfig.getVolumeModifier(id);
        if (modifier > 1.0f) {
            return result * modifier;
        }
        return result;
    }
    @org.spongepowered.asm.mixin.injection.Inject(method = "play", at = @At("HEAD"))
    private void onEnginePlay(SoundInstance sound, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (sound != null && sound.getId() != null) {
            String id = sound.getId().toString();
            soundcontrol.SoundTracker.recordSound(id);
            soundcontrol.SoundWorldRenderer.recordSound(sound, id);
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "playNextTick", at = @At("HEAD"))
    private void onEnginePlayNextTick(net.minecraft.client.sound.TickableSoundInstance sound, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (sound != null && sound.getId() != null) {
            String id = sound.getId().toString();
            soundcontrol.SoundTracker.recordSound(id);
            soundcontrol.SoundWorldRenderer.recordSound(sound, id);
        }
    }
}
