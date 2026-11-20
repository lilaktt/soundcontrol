package soundcontrol.mixin;

import net.minecraft.sound.SoundEvent;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntitySoundAccessor {
    @Invoker("getHurtSound")
    SoundEvent soundcontrol$getHurtSound(DamageSource source);

    @Invoker("getDeathSound")
    SoundEvent soundcontrol$getDeathSound();
}
