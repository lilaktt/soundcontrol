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
        AL10.alSourcef(this.source, AL10.AL_MAX_GAIN, 10.0f);
    }
}
