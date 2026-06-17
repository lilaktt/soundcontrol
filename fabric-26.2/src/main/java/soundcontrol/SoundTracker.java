package soundcontrol;

public class SoundTracker {
    private static int overlayMode = 0;
    public static int getOverlayMode() { return overlayMode; }

    public static void cycleOverlayMode() {
        overlayMode = (overlayMode + 1) % 3;
        if (overlayMode == 1) {
            SoundWorldRenderer.enabled = true;
            SoundLookupRenderer.enabled = false;
        } else if (overlayMode == 2) {
            SoundWorldRenderer.enabled = false;
            SoundLookupRenderer.enabled = true;
        } else {
            SoundWorldRenderer.enabled = false;
            SoundLookupRenderer.enabled = false;
        }
    }
    
    public static void recordSound(String soundId) {
        
    }
}