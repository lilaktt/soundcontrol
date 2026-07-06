package soundcontrol.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import soundcontrol.SoundConfig;

@Mixin(AbstractSoundInstance.class)
public class AbstractSoundInstanceMixin {
    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private void soundcontrol_getVolume(CallbackInfoReturnable<Float> cir) {
        AbstractSoundInstance sound = (AbstractSoundInstance) (Object) this;
        float modifier = SoundConfig.getVolumeModifier(sound.getLocation().toString());
        // Only apply reduction/muting here.
        // Amplification (modifier > 1.0f) is handled exclusively by WrapOperation
        // in SoundEngineMixin to avoid double-multiplication.
        if (modifier < 1.0f) {
            cir.setReturnValue(cir.getReturnValue() * modifier);
        }
    }
}
