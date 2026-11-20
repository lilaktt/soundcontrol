package soundcontrol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import soundcontrol.SoundTracker;

@Mixin(SoundSystem.class)
public abstract class SoundEngineMixin {
    @Shadow @Final private java.util.Map<SoundInstance, net.minecraft.client.sound.Channel.SourceManager> sources;
    @Shadow protected abstract float getAdjustedVolume(SoundInstance sound);
    @Unique private boolean soundcontrol$hadAnchors;

    @Inject(method = "play", at = @At("HEAD"))
    private void soundcontrol$onPlay(SoundInstance sound, CallbackInfoReturnable<?> ci) {
        SoundTracker.onSoundStarted(sound, Compat.soundId(sound));
    }

    @WrapOperation(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F"))
    private float soundcontrol$initialVolume(SoundSystem engine, float volume, SoundCategory category, Operation<Float> original, SoundInstance sound) {
        return original.call(engine, volume, category) * soundcontrol$modifier(sound);
    }

    @Inject(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void soundcontrol$updatedVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValue() * soundcontrol$modifier(sound));
    }

    @Inject(method = "tick(Z)V", at = @At("TAIL"))
    private void soundcontrol$refreshAnchors(boolean paused, CallbackInfo ci) {
        if (paused) return;
        boolean hasAnchors = !SoundConfig.getAnchors().isEmpty();
        if (!hasAnchors && !soundcontrol$hadAnchors) return;
        soundcontrol$hadAnchors = hasAnchors;
        for (var entry : sources.entrySet()) {
            if (entry.getKey().isRelative()) continue;
            float volume = getAdjustedVolume(entry.getKey());
            entry.getValue().run(channel -> channel.setVolume(volume));
        }
    }

    @Unique
    private static float soundcontrol$modifier(SoundInstance sound) {
        return SoundConfig.getSoundModifier(Compat.soundId(sound), sound.isRelative(), sound.getX(), sound.getY(), sound.getZ());
    }
}
