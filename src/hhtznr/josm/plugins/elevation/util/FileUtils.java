package hhtznr.josm.plugins.elevation.util;

import java.nio.file.Path;

/**
 * This class provides methods used with files, which are
 *
 * @author Harald Hetzner
 */
public class FileUtils {

    private FileUtils() {
    }

    /**
     * Returns the extension of a file.
     *
     * @param path The path of the file from which to obtain the extension.
     * @return The extension of the file without leading dot.
     */
    public static String getFileExtension(Path path) {
        String name = path.getFileName().toString();
        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        return name.substring(lastIndexOf + 1);
    }

    /**
     * Returns the file name of the provided path removing the specified extensions
     * including the dot. Works case-insensitive.
     *
     * @param path       The path with the file name.
     * @param extensions The extensions to remove.
     * @return The file name from the path without extension, if it had one of the
     *         specified extensions.
     */
    public static String getFileNameWithoutExtension(Path path, String... extensions) {
        String fileName = path.getFileName().toString();
        String lowerCaseFileName = fileName.toLowerCase();

        for (String extension : extensions) {
            String lowerCaseExtension = extension.toLowerCase();
            if (!lowerCaseExtension.startsWith(".")) {
                lowerCaseExtension = "." + lowerCaseExtension;
            }
            if (lowerCaseFileName.endsWith(lowerCaseExtension)) {
                return fileName.substring(0, fileName.length() - lowerCaseExtension.length());
            }
        }
        return fileName;
    }
}
