package soundcontrol;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soundcontrol.mixin.LivingEntitySoundAccessor;
import soundcontrol.mixin.MobSoundAccessor;

public final class EntitySoundResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("soundcontrol");
    private static final Map<String, Set<String>> OBSERVED = new LinkedHashMap<>();
    private static final Set<Class<?>> WARNED = new LinkedHashSet<>();
    private static WeakReference<ClientWorld> observedLevel = new WeakReference<>(null);

    private EntitySoundResolver() {}

    private static void checkLevel(ClientWorld level) {
        if (observedLevel.get() != level) {
            OBSERVED.clear();
            observedLevel = new WeakReference<>(level);
        }
    }

    public static Set<String> resolve(LivingEntity mob) {
        MinecraftClient client = MinecraftClient.getInstance();
        checkLevel(client.world);
        String entityId = Registries.ENTITY_TYPE.getId(mob.getType()).toString();
        Set<String> actual = new LinkedHashSet<>(OBSERVED.getOrDefault(entityId, Set.of()));
        if (!EntitySoundMatcher.namespace(entityId).equals("minecraft")) {
            if (mob instanceof MobSoundAccessor ambient) addGetter(actual, mob, ambient::soundcontrol$getAmbientSound);
            if (mob instanceof LivingEntitySoundAccessor living) {
                addGetter(actual, mob, () -> living.soundcontrol$getHurtSound(mob.getDamageSources().generic()));
                addGetter(actual, mob, living::soundcontrol$getDeathSound);
            }
        }
        Set<String> available = new LinkedHashSet<>(Compat.registrySoundIds());
        available.addAll(Compat.clientSoundIds());
        return EntitySoundMatcher.collect(entityId, available, Compat.entityTypeIds(), actual);
    }

    private static void addGetter(Set<String> ids, LivingEntity mob, Supplier<SoundEvent> getter) {
        try {
            SoundEvent sound = getter.get();
            if (sound != null) ids.add(sound.id().toString());
        } catch (RuntimeException exception) {
            if (WARNED.add(mob.getClass())) LOGGER.debug("Cannot query client sound getters for {}", mob.getClass().getName(), exception);
        }
    }

    public static void record(Object handle, String soundId) {
        if (!(handle instanceof SoundInstance sound)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread()) {
            client.execute(() -> record(handle, soundId));
            return;
        }
        checkLevel(client.world);
        if (client.world == null || sound.isRelative()) return;
        String namespace = EntitySoundMatcher.namespace(soundId);
        if (namespace.equals("minecraft")) return;
        String path = EntitySoundMatcher.path(soundId);
        if (path.startsWith("block.") || path.startsWith("block/") || path.startsWith("ui.") || path.startsWith("music.")) return;
        String owner = null;
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof LivingEntity mob) || mob instanceof PlayerEntity || mob.getSoundCategory() != sound.getCategory()) continue;
            String type = Registries.ENTITY_TYPE.getId(mob.getType()).toString();
            if (!EntitySoundMatcher.namespace(type).equals(namespace)) continue;
            if (Math.abs(mob.getX() - sound.getX()) > 0.25 || Math.abs(mob.getZ() - sound.getZ()) > 0.25
                    || sound.getY() < mob.getY() - 0.25 || sound.getY() > mob.getY() + mob.getHeight() + 0.25) continue;
            if (owner != null && !owner.equals(type)) return;
            owner = type;
        }
        if (owner != null) {
            Set<String> sounds = OBSERVED.computeIfAbsent(owner, ignored -> new LinkedHashSet<>());
            if (sounds.size() < 256) sounds.add(soundId);
        }
    }
}
