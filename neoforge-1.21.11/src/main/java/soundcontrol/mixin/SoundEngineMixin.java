package soundcontrol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import soundcontrol.SoundConfig;
import soundcontrol.SoundTracker;
import soundcontrol.SoundWorldRenderer;
import soundcontrol.RecentSoundsPickerScreen;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    @WrapOperation(
            method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F")
    )
    private float amplifyVolume(SoundEngine instance, float volume, SoundSource category, Operation<Float> original, SoundInstance sound) {
        return original.call(instance, volume, category) * soundcontrol$anchorVolume(sound);
    }

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("HEAD"))
    private void onEnginePlay(SoundInstance sound, CallbackInfoReturnable<?> cir) {
        if (sound != null && sound.getIdentifier() != null) {
            String id = sound.getIdentifier().toString();
            SoundTracker.recordSound(id);
            SoundWorldRenderer.recordSound(sound, id);
            RecentSoundsPickerScreen.recordRecentSound(id, sound.getX(), sound.getY(), sound.getZ());
        }
    }

    @Inject(method = "queueTickingSound", at = @At("HEAD"), require = 0)
    private void onEngineQueueTickingSound(TickableSoundInstance sound, CallbackInfo ci) {
        if (sound != null && sound.getIdentifier() != null) {
            String id = sound.getIdentifier().toString();
            SoundTracker.recordSound(id);
            SoundWorldRenderer.recordSound(sound, id);
            RecentSoundsPickerScreen.recordRecentSound(id, sound.getX(), sound.getY(), sound.getZ());
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static float soundcontrol$anchorVolume(SoundInstance sound) {
        String id = sound.getIdentifier().toString();
        float profile = SoundConfig.getVolumeModifier(id);
        // A zone must never undo an explicit profile/global mute. UI/relative sounds have no world origin.
        if (profile <= 0f || sound.isRelative()) return profile;
        var client = net.minecraft.client.Minecraft.getInstance();
        if (client.level == null) return profile;
        float anchor = SoundConfig.getAnchorVolumeModifier(id, client.level.dimension().toString(), sound.getX(), sound.getY(), sound.getZ());
        return anchor >= 0f ? anchor : profile;
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"), cancellable = true)
    private void soundcontrol$updateVolume(SoundInstance sound,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValue() * soundcontrol$anchorVolume(sound));
    }

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private java.util.Map<SoundInstance, net.minecraft.client.sounds.ChannelAccess.ChannelHandle> instanceToChannel;

    @org.spongepowered.asm.mixin.Shadow
    protected abstract float calculateVolume(SoundInstance sound);

    @org.spongepowered.asm.mixin.Unique
    private boolean soundcontrol$hadAnchors;

    @org.spongepowered.asm.mixin.injection.Inject(method = "tick(Z)V", at = @At("TAIL"))
    private void soundcontrol$refreshAnchors(boolean paused, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (paused) return;
        boolean hasAnchors = !SoundConfig.getAnchors().isEmpty();
        if (!hasAnchors && !soundcontrol$hadAnchors) return;
        soundcontrol$hadAnchors = hasAnchors;
        for (var entry : instanceToChannel.entrySet()) {
            if (entry.getKey().isRelative()) continue;
            float volume = calculateVolume(entry.getKey());
            entry.getValue().execute(channel -> channel.setVolume(volume));
        }
    }
}
