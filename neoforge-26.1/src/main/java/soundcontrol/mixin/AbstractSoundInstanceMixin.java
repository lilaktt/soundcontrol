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
    private void modifyVolume(CallbackInfoReturnable<Float> cir) {
        String id = ((AbstractSoundInstance) (Object) this).getIdentifier().toString();
        float modifier = SoundConfig.getVolumeModifier(id);
        float original = cir.getReturnValue();
        float newVal = original * modifier;
        cir.setReturnValue(newVal);
    }
}
