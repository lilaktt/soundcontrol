package soundcontrol.util;

import soundcontrol.SoundConfig;
import soundcontrol.SoundControl;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;

public final class FilePickerUtil {
    private FilePickerUtil() {}

    public static File pickFile(boolean forSave, String title) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.json"));
            filters.flip();

            String defaultPath = SoundConfig.CONFIGS_DIR.getAbsolutePath() + File.separator;

            String result;
            if (forSave) {
                result = TinyFileDialogs.tinyfd_saveFileDialog(
                    title, defaultPath, filters, "JSON Profile (*.json)");
            } else {
                result = TinyFileDialogs.tinyfd_openFileDialog(
                    title, defaultPath, filters, "JSON Profile (*.json)", false);
            }

            if (result != null && !result.isEmpty()) {
                return new File(result);
            }
        } catch (Exception e) {

        }
        return null;
    }
}

