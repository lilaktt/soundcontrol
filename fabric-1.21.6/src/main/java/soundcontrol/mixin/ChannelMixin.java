package soundcontrol.mixin;

import net.minecraft.client.sound.Source;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Source.class)
public class ChannelMixin {

    @Shadow
    private int pointer;

    @Inject(method = "setVolume", at = @At("HEAD"))
    private void onSetVolume(float volume, CallbackInfo ci) {
        if (this.pointer != 0) {
            AL10.alSourcef(this.pointer, AL10.AL_MAX_GAIN, 10.0f);
        }
    }
}
