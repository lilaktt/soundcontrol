package soundcontrol;

import soundcontrol.gui.RecentSoundsPickerScreen;
import soundcontrol.render.SoundLookupRenderer;
import soundcontrol.render.SoundWorldRenderer;

public class SoundTracker {
    private static int overlayMode = 0;

    public static int getOverlayMode() { return overlayMode; }

    public static void cycleOverlayMode() {
        overlayMode = (overlayMode + 1) % 3;
        SoundWorldRenderer.enabled = overlayMode == 1;
        SoundLookupRenderer.enabled = overlayMode == 2;
    }

    public static void onSoundStarted(Object sound, String soundId) {
        if (sound == null || soundId == null) return;
        double[] pos = Compat.soundPos(sound);
        SoundWorldRenderer.recordSound(sound, soundId);
        RecentSoundsPickerScreen.recordRecentSound(soundId, pos[0], pos[1], pos[2]);
        EntitySoundResolver.record(sound, soundId);
    }

    public static void clientTick() {
        if (overlayMode == 2) SoundLookupRenderer.tick();
        SoundConfig.tick();
    }
}
