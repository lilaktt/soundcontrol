package soundcontrol.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import soundcontrol.Compat;
import soundcontrol.SoundConfig;
import soundcontrol.SoundTracker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Unique private static final Logger soundcontrol$LOGGER = LoggerFactory.getLogger("soundcontrol");
    @Unique private static final ThreadLocal<SoundInstance> soundcontrol$CURRENT = new ThreadLocal<>();
    @Unique private static Field soundcontrol$channels;
    @Unique private static Method soundcontrol$calculate;
    @Unique private static boolean soundcontrol$reflectionFailed;
    @Unique private boolean soundcontrol$hadAnchors;

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"))
    private void soundcontrol$onPlay(SoundInstance sound, CallbackInfo ci) {
        soundcontrol$CURRENT.set(sound);
        SoundTracker.onSoundStarted(sound, Compat.soundId(sound));
    }

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("RETURN"))
    private void soundcontrol$afterPlay(SoundInstance sound, CallbackInfo ci) {
        soundcontrol$CURRENT.remove();
    }

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("HEAD"))
    private void soundcontrol$beforeUpdate(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        soundcontrol$CURRENT.set(sound);
    }

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"))
    private void soundcontrol$afterUpdate(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        soundcontrol$CURRENT.remove();
    }

    @Inject(method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
    private void soundcontrol$scale(float volume, net.minecraft.sounds.SoundSource category, CallbackInfoReturnable<Float> cir) {
        SoundInstance sound = soundcontrol$CURRENT.get();
        if (sound == null) return;
        cir.setReturnValue(cir.getReturnValue() * SoundConfig.getSoundModifier(Compat.soundId(sound), sound.isRelative(), sound.getX(), sound.getY(), sound.getZ()));
    }

    @Inject(method = "tick(Z)V", at = @At("TAIL"))
    private void soundcontrol$refreshAnchors(boolean paused, CallbackInfo ci) {
        if (paused || soundcontrol$reflectionFailed) return;
        boolean hasAnchors = !SoundConfig.getAnchors().isEmpty();
        if (!hasAnchors && !soundcontrol$hadAnchors) return;
        soundcontrol$hadAnchors = hasAnchors;
        try {
            if (soundcontrol$channels == null) {
                soundcontrol$channels = ObfuscationReflectionHelper.findField(SoundEngine.class, "f_120226_");
                soundcontrol$calculate = ObfuscationReflectionHelper.findMethod(SoundEngine.class, "m_120327_", SoundInstance.class);
            }
            Map<?, ?> channels = (Map<?, ?>) soundcontrol$channels.get(this);
            for (Map.Entry<?, ?> entry : channels.entrySet()) {
                SoundInstance sound = (SoundInstance) entry.getKey();
                if (sound.isRelative()) continue;
                float volume = (Float) soundcontrol$calculate.invoke(this, sound);
                ((net.minecraft.client.sounds.ChannelAccess.ChannelHandle) entry.getValue()).execute(channel -> channel.setVolume(volume));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            soundcontrol$reflectionFailed = true;
            soundcontrol$LOGGER.warn("Sound Control: live anchor volume updates are unavailable on this setup", e);
        }
    }
}
