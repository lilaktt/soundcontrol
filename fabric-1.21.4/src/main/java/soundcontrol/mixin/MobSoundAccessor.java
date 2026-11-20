package soundcontrol.mixin;

import net.minecraft.sound.SoundEvent;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MobEntity.class)
public interface MobSoundAccessor {
    @Invoker("getAmbientSound")
    SoundEvent soundcontrol$getAmbientSound();
}
