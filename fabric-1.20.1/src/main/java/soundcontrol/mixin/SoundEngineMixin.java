package soundcontrol.mixin;

import soundcontrol.gui.RecentSoundsPickerScreen;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import soundcontrol.SoundConfig;
import soundcontrol.SoundTracker;
import soundcontrol.render.SoundWorldRenderer;

@Mixin(SoundSystem.class)
public abstract class SoundEngineMixin {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"))
    private void onEnginePlay(SoundInstance sound, CallbackInfo ci) {
        if (sound != null && sound.getId() != null) {
            String id = sound.getId().toString();
            SoundTracker.recordSound(id);
            SoundWorldRenderer.recordSound(sound, id);
            RecentSoundsPickerScreen.recordRecentSound(id, sound.getX(), sound.getY(), sound.getZ());
        }
    }

    /**
     * Inject into getAdjustedVolume(SoundInstance) which is called from play().
     * This version takes a SoundInstance and internally resolves volume + category.
     */
    @Inject(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F",
            at = @At("RETURN"), cancellable = true)
    private void modifyVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound != null && sound.getId() != null) {
            String id = sound.getId().toString();
            float modifier = soundcontrol$anchorVolume(sound);
            cir.setReturnValue(cir.getReturnValue() * modifier);
        }
    }

    @Inject(method = "playNextTick", at = @At("HEAD"), require = 0)
    private void onEnginePlayNextTick(TickableSoundInstance sound, CallbackInfo ci) {
        if (sound != null && sound.getId() != null) {
            String id = sound.getId().toString();
            SoundTracker.recordSound(id);
            SoundWorldRenderer.recordSound(sound, id);
            RecentSoundsPickerScreen.recordRecentSound(id, sound.getX(), sound.getY(), sound.getZ());
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

    @org.spongepowered.asm.mixin.Shadow
    protected abstract float getAdjustedVolume(float volume, SoundCategory category);

    @org.spongepowered.asm.mixin.injection.Redirect(method = "play",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundSystem;getAdjustedVolume(FLnet/minecraft/sound/SoundCategory;)F"))
    private float soundcontrol$initialAnchorVolume(SoundSystem engine, float volume, SoundCategory category, SoundInstance sound) {
        return getAdjustedVolume(volume, category) * soundcontrol$anchorVolume(sound);
    }
}
