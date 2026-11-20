package soundcontrol.mixin;

import net.minecraft.client.sound.Source;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Source.class)
public class ChannelMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void soundcontrol$raiseMaxGain(int source, CallbackInfo ci) {
        AL10.alSourcef(source, AL10.AL_MAX_GAIN, 10.0f);
    }
}
