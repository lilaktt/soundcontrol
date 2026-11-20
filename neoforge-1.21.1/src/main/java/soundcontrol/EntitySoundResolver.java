package soundcontrol;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soundcontrol.mixin.LivingEntitySoundAccessor;
import soundcontrol.mixin.MobSoundAccessor;

public final class EntitySoundResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("soundcontrol");
    private static final Map<String, Set<String>> OBSERVED = new LinkedHashMap<>();
    private static final Set<Class<?>> WARNED = new LinkedHashSet<>();
    private static WeakReference<ClientLevel> observedLevel = new WeakReference<>(null);

    private EntitySoundResolver() {}

    private static void checkLevel(ClientLevel level) {
        if (observedLevel.get() != level) {
            OBSERVED.clear();
            observedLevel = new WeakReference<>(level);
        }
    }

    public static Set<String> resolve(LivingEntity mob) {
        Minecraft client = Minecraft.getInstance();
        checkLevel(client.level);
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        Set<String> actual = new LinkedHashSet<>(OBSERVED.getOrDefault(entityId, Set.of()));
        if (!EntitySoundMatcher.namespace(entityId).equals("minecraft")) {
            if (mob instanceof MobSoundAccessor ambient) addGetter(actual, mob, ambient::soundcontrol$getAmbientSound);
            if (mob instanceof LivingEntitySoundAccessor living) {
                addGetter(actual, mob, () -> living.soundcontrol$getHurtSound(mob.damageSources().generic()));
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
            if (sound != null) ids.add(sound.getLocation().toString());
        } catch (RuntimeException exception) {
            if (WARNED.add(mob.getClass())) LOGGER.debug("Cannot query client sound getters for {}", mob.getClass().getName(), exception);
        }
    }

    public static void record(Object handle, String soundId) {
        if (!(handle instanceof SoundInstance sound)) return;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            client.execute(() -> record(handle, soundId));
            return;
        }
        checkLevel(client.level);
        if (client.level == null || sound.isRelative()) return;
        String namespace = EntitySoundMatcher.namespace(soundId);
        if (namespace.equals("minecraft")) return;
        String path = EntitySoundMatcher.path(soundId);
        if (path.startsWith("block.") || path.startsWith("block/") || path.startsWith("ui.") || path.startsWith("music.")) return;
        String owner = null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity mob) || mob instanceof Player || mob.getSoundSource() != sound.getSource()) continue;
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
            if (!EntitySoundMatcher.namespace(type).equals(namespace)) continue;
            if (Math.abs(mob.getX() - sound.getX()) > 0.25 || Math.abs(mob.getZ() - sound.getZ()) > 0.25
                    || sound.getY() < mob.getY() - 0.25 || sound.getY() > mob.getY() + mob.getBbHeight() + 0.25) continue;
            if (owner != null && !owner.equals(type)) return;
            owner = type;
        }
        if (owner != null) {
            Set<String> sounds = OBSERVED.computeIfAbsent(owner, ignored -> new LinkedHashSet<>());
            if (sounds.size() < 256) sounds.add(soundId);
        }
    }
}
