package soundcontrol.mixin;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;

/**
 * This mixin is kept for SoundManager-level hooks only.
 * Sound recording (SoundTracker, SoundWorldRenderer, RecentSoundsPickerScreen) 
 * is handled exclusively in SoundEngineMixin to avoid duplicate calls.
 */
@Mixin(SoundManager.class)
public class SoundTrackerMixin {
    // Sound recording removed — consolidated in SoundEngineMixin
}
