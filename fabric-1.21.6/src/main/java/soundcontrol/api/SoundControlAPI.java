package soundcontrol.api;

import soundcontrol.SoundConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sound Control Developer API — v2
 *
 * Allows other mods to dynamically modify sound volumes at runtime.
 * Changes are applied in-memory only (not saved to disk) and have the
 * LOWEST priority — user profile settings always take precedence.
 *
 * <h3>Key design decisions</h3>
 * <ul>
 *   <li><b>Per-mod isolation:</b> Each mod's overrides are stored separately.
 *       Calling {@link #resetAll(String)} only clears the calling mod's data,
 *       never affecting other mods.</li>
 *   <li><b>Conflict resolution:</b> When multiple mods override the same sound,
 *       the <em>lowest</em> (most restrictive) volume wins.</li>
 *   <li><b>Thread safety:</b> All data structures are concurrent-safe. You may
 *       call these methods from any thread.</li>
 * </ul>
 *
 * <h3>Volume range</h3>
 * {@code 0.0} (muted) to {@code 5.0} (max amplification).
 * Values outside this range are clamped. {@code NaN} and {@code Infinity}
 * are rejected with a warning log.
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * // In your mod initializer or wherever appropriate:
 * if (SoundControlAPI.isAvailable()) {
 *     SoundControlAPI.setVolume("mymod", "minecraft:entity.zombie.hurt", 0.3f);
 *     SoundControlAPI.mute("mymod", "minecraft:block.tnt.primed");
 *
 *     // Batch operation:
 *     SoundControlAPI.setVolumes("mymod", Map.of(
 *         "minecraft:entity.creeper.primed", 0.1f,
 *         "minecraft:block.piston.extend", 0.5f
 *     ));
 * }
 *
 * // On world unload (only clears YOUR overrides):
 * SoundControlAPI.resetAll("mymod");
 * }</pre>
 *
 * @since 1.5.0
 */
public final class SoundControlAPI {

    private static final Logger LOGGER = LoggerFactory.getLogger("soundcontrol");

    /**
     * Per-mod override storage.
     * Outer key = mod ID, inner key = sound ID, value = clamped volume.
     * Thread-safe: both maps are ConcurrentHashMaps.
     */
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, Float>> MOD_OVERRIDES =
            new ConcurrentHashMap<>();

    private SoundControlAPI() {}

    // ── Version ────────────────────────────────────────────────────────────

    /**
     * Returns the API version number.
     * Useful for other mods to check compatibility at runtime.
     * <ul>
     *   <li>Version 1 — initial release (flat shared map)</li>
     *   <li>Version 2 — per-mod isolation, thread safety, NaN protection</li>
     * </ul>
     */
    public static int getApiVersion() { return 2; }

    /**
     * Always returns {@code true} — confirms Sound Control is installed
     * and the API class is loadable.
     */
    public static boolean isAvailable() { return true; }

    // ── Set / Mute ─────────────────────────────────────────────────────────

    /**
     * Override the volume of a sound for a specific mod.
     *
     * @param modId    Your mod's identifier (e.g. {@code "mymod"}).
     *                 Must not be null or empty.
     * @param soundId  The sound resource location (e.g.
     *                 {@code "minecraft:entity.zombie.hurt"}) or a global tag
     *                 (e.g. {@code "#global:step"}).
     * @param volume   Volume multiplier: 0.0 = muted, 1.0 = normal, up to 5.0.
     *                 NaN and Infinity are rejected.
     */
    public static void setVolume(String modId, String soundId, float volume) {
        if (!validateModId(modId) || !validateSoundId(soundId)) return;
        if (Float.isNaN(volume) || Float.isInfinite(volume)) {
            LOGGER.warn("[SoundControlAPI] Invalid volume value ({}) from mod '{}' for sound '{}' — ignored",
                    volume, modId, soundId);
            return;
        }
        float clamped = Math.max(0f, Math.min(5f, volume));
        MOD_OVERRIDES.computeIfAbsent(modId, k -> new ConcurrentHashMap<>()).put(soundId, clamped);
    }

    /**
     * Override the volume of multiple sounds at once.
     *
     * @param modId    Your mod's identifier.
     * @param volumes  Map of sound IDs to volume multipliers.
     *                 Invalid entries (null keys, NaN values) are skipped
     *                 individually with a warning.
     */
    public static void setVolumes(String modId, Map<String, Float> volumes) {
        if (!validateModId(modId)) return;
        if (volumes == null || volumes.isEmpty()) return;
        for (Map.Entry<String, Float> entry : volumes.entrySet()) {
            Float value = entry.getValue();
            if (value == null) {
                LOGGER.warn("[SoundControlAPI] Null volume value from mod '{}' for sound '{}' — skipped",
                        modId, entry.getKey());
                continue;
            }
            setVolume(modId, entry.getKey(), value);
        }
    }

    /**
     * Mute a sound via API.
     *
     * @param modId    Your mod's identifier.
     * @param soundId  The sound resource location.
     */
    public static void mute(String modId, String soundId) {
        setVolume(modId, soundId, 0f);
    }

    // ── Reset ──────────────────────────────────────────────────────────────

    /**
     * Remove a specific mod's override for a specific sound.
     *
     * @param modId    Your mod's identifier.
     * @param soundId  The sound resource location.
     */
    public static void reset(String modId, String soundId) {
        if (!validateModId(modId) || soundId == null) return;
        MOD_OVERRIDES.computeIfPresent(modId, (k, overrides) -> {
            overrides.remove(soundId);
            return overrides.isEmpty() ? null : overrides;
        });
    }

    /**
     * Remove ALL overrides for a specific mod.
     * This only clears YOUR mod's data — other mods' overrides are untouched.
     * Recommended to call on world unload.
     *
     * @param modId  Your mod's identifier.
     */
    public static void resetAll(String modId) {
        if (!validateModId(modId)) return;
        MOD_OVERRIDES.remove(modId);
    }

    // ── Query ──────────────────────────────────────────────────────────────

    /**
     * Get the effective volume that will be applied for a sound.
     * Takes all layers into account: active profile → default profile → API overrides.
     * Returns 1.0 if no overrides are set anywhere.
     *
     * @param soundId  The sound resource location.
     */
    public static float getEffectiveVolume(String soundId) {
        return SoundConfig.getVolumeModifier(soundId);
    }

    /**
     * Check if a sound is currently muted (by any layer: profile or API).
     *
     * @param soundId  The sound resource location.
     */
    public static boolean isMuted(String soundId) {
        return getEffectiveVolume(soundId) == 0f;
    }

    /**
     * Check whether any mod has an active API override for this sound.
     * Useful to distinguish "volume 1.0 by default" from
     * "volume 1.0 because a mod explicitly set it".
     *
     * @param soundId  The sound resource location.
     * @return {@code true} if at least one mod has an override for this sound.
     */
    public static boolean hasOverride(String soundId) {
        if (soundId == null) return false;
        for (ConcurrentHashMap<String, Float> overrides : MOD_OVERRIDES.values()) {
            if (overrides.containsKey(soundId)) return true;
        }
        return false;
    }

    /**
     * Get all sound IDs that a specific mod currently has overrides for.
     *
     * <p><b>Note:</b> The returned set is a <em>live view</em> over the internal
     * data, not a snapshot. If another thread adds or removes overrides for
     * this mod concurrently, those changes may be visible through the returned
     * set (weakly consistent, no {@code ConcurrentModificationException}).
     * If you need a stable snapshot, copy the set: {@code new HashSet<>(set)}.</p>
     *
     * @param modId  The mod's identifier.
     * @return An unmodifiable live view of sound IDs, or empty set if none.
     */
    public static Set<String> getOverriddenSounds(String modId) {
        if (modId == null) return Collections.emptySet();
        ConcurrentHashMap<String, Float> overrides = MOD_OVERRIDES.get(modId);
        if (overrides == null) return Collections.emptySet();
        return Collections.unmodifiableSet(overrides.keySet());
    }

    // ── Internal: called by SoundConfig ────────────────────────────────────

    /**
     * Resolve the combined API volume for a sound across all mods.
     * When multiple mods override the same sound, the <em>lowest</em>
     * (most restrictive) volume wins. This is a deliberate design choice:
     * muting/quieting is treated as a safety-critical operation that should
     * not be silently overridden by another mod requesting a louder value.
     * Returns -1 if no mod has an override.
     *
     * <p><b>Internal use only</b> — called from {@code SoundConfig.getVolumeModifier()}.
     * Do not call this directly from other mods; use
     * {@link #getEffectiveVolume(String)} instead.</p>
     *
     * <p><b>Performance note:</b> This method linearly scans all registered
     * mods on every call. At typical mod counts (single digits) this is
     * negligible. If profiling ever shows this as a hotspot in large modpacks,
     * consider caching the resolved value and invalidating on set/reset.</p>
     */
    public static float resolveApiVolume(String soundId) {
        float result = -1f;
        for (ConcurrentHashMap<String, Float> overrides : MOD_OVERRIDES.values()) {
            Float vol = overrides.get(soundId);
            if (vol != null) {
                result = (result < 0) ? vol : Math.min(result, vol);
            }
        }
        return result;
    }

    // ── Validation helpers ─────────────────────────────────────────────────

    private static boolean validateModId(String modId) {
        if (modId == null || modId.isEmpty()) {
            LOGGER.warn("[SoundControlAPI] Null or empty modId — call ignored. " +
                    "Please pass your mod's ID as the first argument.");
            return false;
        }
        return true;
    }

    private static boolean validateSoundId(String soundId) {
        if (soundId == null || soundId.isEmpty()) {
            LOGGER.warn("[SoundControlAPI] Null or empty soundId — call ignored.");
            return false;
        }
        return true;
    }
}
