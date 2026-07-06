package soundcontrol.api;



import soundcontrol.SoundConfig;
import soundcontrol.SoundControl;
/**
 * Sound Control Developer API.
 *
 * Allows other mods to dynamically modify sound volumes at runtime.
 * Changes are applied in-memory only (not saved to disk) and have the
 * LOWEST priority — user profile settings always take precedence.
 *
 * Volume range: 0.0 (muted) to 5.0 (max amplification for API callers).
 *
 * Usage example:
 * <pre>{@code
 * if (SoundControlAPI.isAvailable()) {
 *     SoundControlAPI.setVolume("minecraft:entity.zombie.hurt", 0.3f);
 *     SoundControlAPI.mute("minecraft:block.tnt.primed");
 * }
 * }</pre>
 */
public final class SoundControlAPI {
    private SoundControlAPI() {}

    /** Always returns true — confirms Sound Control is installed. */
    public static boolean isAvailable() { return true; }

    /**
     * Override the volume of a sound.
     * @param soundId  The sound resource location (e.g. "minecraft:entity.zombie.hurt")
     *                 or a global tag (e.g. "#global:step").
     * @param volume   Volume multiplier: 0.0 = muted, 1.0 = normal, up to 5.0.
     */
    public static void setVolume(String soundId, float volume) {
        if (soundId == null || soundId.isEmpty()) return;
        float clamped = Math.max(0f, Math.min(5f, volume));
        SoundConfig.API_OVERRIDES.put(soundId, clamped);
    }

    /**
     * Mute a sound via API.
     * @param soundId  The sound resource location.
     */
    public static void mute(String soundId) {
        setVolume(soundId, 0f);
    }

    /**
     * Remove API override for a specific sound, restoring profile-based volume.
     * @param soundId  The sound resource location.
     */
    public static void reset(String soundId) {
        if (soundId != null) SoundConfig.API_OVERRIDES.remove(soundId);
    }

    /**
     * Remove all API overrides (from all mods). Useful on world unload.
     */
    public static void resetAll() {
        SoundConfig.API_OVERRIDES.clear();
    }

    /**
     * Get the effective volume that will be applied for a sound.
     * Takes all layers into account (active profile, default profile, API overrides).
     * Returns 1.0 if no overrides are set.
     * @param soundId  The sound resource location.
     */
    public static float getEffectiveVolume(String soundId) {
        return SoundConfig.getVolumeModifier(soundId);
    }

    /**
     * Check if a sound is currently muted (by any layer: profile or API).
     * @param soundId  The sound resource location.
     */
    public static boolean isMuted(String soundId) {
        return getEffectiveVolume(soundId) == 0f;
    }
}
