package soundcontrol.mixin;

import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;


@Mixin(SoundManager.class)
public class SoundTrackerMixin {
    // SoundManager method names differ between the development and production
    // Forge namespaces. Recording is performed by SoundEngineMixin instead.
}