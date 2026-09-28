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

/** Shared by the crosshair mute action and SoundList. Client-thread only. */
public final class EntitySoundResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("soundcontrol");
    private static final Map<String, Set<String>> observed = new LinkedHashMap<>();
    private static final Set<Class<?>> warnedGetters = new LinkedHashSet<>();
    private static WeakReference<ClientLevel> observedLevel = new WeakReference<>(null);

    private EntitySoundResolver() {}

    private static void checkLevel(ClientLevel level) {
        if (observedLevel.get() != level) {
            observed.clear();
            observedLevel = new WeakReference<>(level);
        }
    }

    public static Set<String> resolve(Minecraft client, Entity entity) {
        checkLevel(client.level);
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        Set<String> actual = new LinkedHashSet<>(observed.getOrDefault(entityId, Set.of()));
        if (!EntitySoundMatcher.namespace(entityId).equals("minecraft") && entity instanceof LivingEntity mob) {
            // Invokers call the virtual overrides, not the vanilla base implementations.
            // No arbitrary mod methods are reflected/invoked and no sound is played here.
            if (mob instanceof MobSoundAccessor ambient) {
                addGetter(actual, mob, ambient::soundcontrol$getAmbientSound);
            }
            if (mob instanceof LivingEntitySoundAccessor living) {
                addGetter(actual, mob, () -> living.soundcontrol$getHurtSound(mob.damageSources().generic()));
                addGetter(actual, mob, living::soundcontrol$getDeathSound);
            }
        }
        Set<String> available = new LinkedHashSet<>();
        for (var id : BuiltInRegistries.SOUND_EVENT.keySet()) available.add(id.toString());
        for (var id : client.getSoundManager().getAvailableSounds()) available.add(id.toString());
        Set<String> types = new LinkedHashSet<>();
        for (var id : BuiltInRegistries.ENTITY_TYPE.keySet()) types.add(id.toString());
        return EntitySoundMatcher.collect(entityId, available, types, actual);
    }

    private static void addGetter(Set<String> ids, LivingEntity mob, Supplier<SoundEvent> getter) {
        try {
            SoundEvent sound = getter.get();
            if (sound != null) ids.add(sound.getLocation().toString());
        } catch (RuntimeException exception) {
            // Some mods assume server-only state in their getters. Keep the other fallbacks usable.
            if (warnedGetters.add(mob.getClass())) {
                LOGGER.debug("Cannot query all client sound getters for {}", mob.getClass().getName(), exception);
            }
        }
    }

    /** Called before radar filtering, even when the radar is off or the sound is already muted. */
    public static void record(SoundInstance sound, String soundId) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            client.execute(() -> record(sound, soundId));
            return;
        }
        checkLevel(client.level);
        if (client.level == null || sound.isRelative()) return;
        String namespace = EntitySoundMatcher.namespace(soundId);
        if (namespace.equals("minecraft")) return;
        String path = EntitySoundMatcher.path(soundId);
        if (path.startsWith("block.") || path.startsWith("block/")
                || path.startsWith("ui.") || path.startsWith("music.")) return;
        String owner = null;
        // Positional packets often carry no entity ID. Only learn an unambiguous mod-mob
        // origin, in the same namespace and sound category; never take every nearby sound.
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity mob) || mob instanceof Player || mob.getSoundSource() != sound.getSource()) continue;
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
            if (!EntitySoundMatcher.namespace(type).equals(namespace)) continue;
            if (Math.abs(mob.getX() - sound.getX()) > 0.25
                    || Math.abs(mob.getZ() - sound.getZ()) > 0.25
                    || sound.getY() < mob.getY() - 0.25
                    || sound.getY() > mob.getY() + mob.getBbHeight() + 0.25) continue;
            if (owner != null && !owner.equals(type)) return; // Overlapping different mobs are ambiguous.
            owner = type;
        }
        if (owner != null) {
            Set<String> sounds = observed.computeIfAbsent(owner, ignored -> new LinkedHashSet<>());
            if (sounds.size() < 256) sounds.add(soundId);
        }
    }
}
