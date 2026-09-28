package soundcontrol.mixin;

import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SoundManager.class)
public class SoundTrackerMixin {
    // Radar recording lives in SoundEngineMixin so every sound is captured once
    // at the point where the audio engine actually receives it.
}

