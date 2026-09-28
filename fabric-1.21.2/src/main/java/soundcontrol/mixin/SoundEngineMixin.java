package soundcontrol.mixin;

import soundcontrol.gui.RecentSoundsPickerScreen;
import soundcontrol.render.SoundWorldRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import soundcontrol.SoundConfig;

@Mixin(SoundSystem.class)
public abstract class SoundEngineMixin {
    @WrapOperation(
            method = "play",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F")
    )
    private float amplifyVolume(SoundSystem instance, float volume, SoundCategory category, Operation<Float> original, SoundInstance sound) {
        return original.call(instance, volume, category) * soundcontrol$anchorVolume(sound);
    }
    @org.spongepowered.asm.mixin.injection.Inject(method = "play", at = @At("HEAD"))
    private void onEnginePlay(SoundInstance sound, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (sound != null && sound.getId() != null) {
            String id = sound.getId().toString();
            soundcontrol.SoundTracker.recordSound(id);
            soundcontrol.render.SoundWorldRenderer.recordSound(sound, id);
            soundcontrol.gui.RecentSoundsPickerScreen.recordRecentSound(id, sound.getX(), sound.getY(), sound.getZ());
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "playNextTick", at = @At("HEAD"))
    private void onEnginePlayNextTick(net.minecraft.client.sound.TickableSoundInstance sound, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (sound != null && sound.getId() != null) {
            String id = sound.getId().toString();
            soundcontrol.SoundTracker.recordSound(id);
            soundcontrol.render.SoundWorldRenderer.recordSound(sound, id);
            soundcontrol.gui.RecentSoundsPickerScreen.recordRecentSound(id, sound.getX(), sound.getY(), sound.getZ());
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static float soundcontrol$anchorVolume(SoundInstance sound) {
        String id = sound.getId().toString();
        float profile = SoundConfig.getVolumeModifier(id);
        // A zone must never undo an explicit profile/global mute. UI/relative sounds have no world origin.
        if (profile <= 0f || sound.isRelative()) return profile;
        var client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.world == null) return profile;
        float anchor = SoundConfig.getAnchorVolumeModifier(id, client.world.getRegistryKey().getValue().toString(), sound.getX(), sound.getY(), sound.getZ());
        return anchor >= 0f ? anchor : profile;
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F",
            at = @At("RETURN"), cancellable = true)
    private void soundcontrol$updateVolume(SoundInstance sound,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValue() * soundcontrol$anchorVolume(sound));
    }

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private java.util.Map<SoundInstance, net.minecraft.client.sound.Channel.SourceManager> sources;

    @org.spongepowered.asm.mixin.Shadow
    protected abstract float getAdjustedVolume(SoundInstance sound);

    @org.spongepowered.asm.mixin.Unique
    private boolean soundcontrol$hadAnchors;

    @org.spongepowered.asm.mixin.injection.Inject(method = "tick(Z)V", at = @At("TAIL"))
    private void soundcontrol$refreshAnchors(boolean paused, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
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
}
