package soundcontrol.util;

import soundcontrol.SoundConfig;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;

public final class FilePickerUtil {
    private FilePickerUtil() {}

    public static File pickFile(boolean forSave, String title) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JFileChooser chooser = new JFileChooser(SoundConfig.CONFIGS_DIR);
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("JSON Profile (*.json)", "json"));

        int result;
        if (forSave) {
            result = chooser.showSaveDialog(null);
        } else {
            result = chooser.showOpenDialog(null);
        }

        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (forSave && !selected.getName().endsWith(".json")) {
                selected = new File(selected.getAbsolutePath() + ".json");
            }
            return selected;
        }
        return null;
    }
}
