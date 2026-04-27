package soundcontrol.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public class ChannelMixin {

    @Shadow
    private int source;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(int source, CallbackInfo ci) {
        // AL_MAX_GAIN defaults to 1.0f in OpenAL, which silently clamps any volume above 100%.
        // We set it to a higher value (e.g. 10.0f) so that 200% (2.0f) actually sounds louder!
        AL10.alSourcef(this.source, AL10.AL_MAX_GAIN, 10.0f);
    }
}
