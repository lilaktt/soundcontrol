package soundcontrol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import soundcontrol.SoundConfig;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {


    @WrapOperation(
            method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F")
    )
    private float amplifyVolume(SoundEngine instance, float volume, SoundSource category, Operation<Float> original, SoundInstance sound) {
        float result = original.call(instance, volume, category);
        String id = sound.getIdentifier().toString();
        float modifier = SoundConfig.getVolumeModifier(id);
        if (modifier > 1.0f) {
            return result * modifier;
        }
        return result;
    }
}
