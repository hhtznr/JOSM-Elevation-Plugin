package hhtznr.josm.plugins.elevation;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.IPreferences;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.io.SRTMFiles;

/**
 * Class {@code Migration} provides methods for data migration for changes,
 * which are not backward compatible.
 *
 * @author Harald Hetzner
 */
public class Migration {

    private Migration() {
    }

    /**
     * Migrates the directory with the SRTM files to a new directory structure where
     * SRTM1 and SRTM3 files are stored in separate directories.
     *
     * @since 0.11.0
     */
    public static void migrateSRTMDirectory() {
        if (ElevationPreferences.LEGACY_SRTM_DIRECTORY.isDirectory()) {
            if (ElevationPreferences.DEFAULT_EARTHDATA_SRTM1_DIRECTORY.exists()
                    || ElevationPreferences.DEFAULT_EARTHDATA_SRTM1_DIRECTORY.mkdirs()) {
                try {
                    DirectoryStream<Path> stream = Files
                            .newDirectoryStream(ElevationPreferences.LEGACY_SRTM_DIRECTORY.toPath());
                    for (Path file : stream) {
                        if (Files.isRegularFile(file) && SRTMFiles.getSRTMTileTypeFromEarthdataFileName(
                                file.getFileName().toString()) == SRTMTile.Type.SRTM1) {
                            Path targetPath = ElevationPreferences.DEFAULT_EARTHDATA_SRTM1_DIRECTORY.toPath()
                                    .resolve(file.getFileName());
                            Files.move(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                            Logging.info("Elevation: Migrated SRTM file '" + file.toString() + "' to '"
                                    + targetPath.toAbsolutePath() + "'");
                        }
                    }
                    stream.close();
                } catch (IOException e) {
                    Logging.error("Elevation: IOException during SRTM1 file migration: " + e.toString());
                }
            }
            if (ElevationPreferences.DEFAULT_EARTHDATA_SRTM3_DIRECTORY.exists()
                    || ElevationPreferences.DEFAULT_EARTHDATA_SRTM3_DIRECTORY.mkdirs()) {
                try {
                    DirectoryStream<Path> stream = Files
                            .newDirectoryStream(ElevationPreferences.LEGACY_SRTM_DIRECTORY.toPath());
                    for (Path file : stream) {
                        if (Files.isRegularFile(file) && SRTMFiles.getSRTMTileTypeFromEarthdataFileName(
                                file.getFileName().toString()) == SRTMTile.Type.SRTM3) {
                            Path targetPath = ElevationPreferences.DEFAULT_EARTHDATA_SRTM3_DIRECTORY.toPath()
                                    .resolve(file.getFileName());
                            Files.move(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                            Logging.info("Elevation: Migrated SRTM file '" + file.toString() + "' to '"
                                    + targetPath.toAbsolutePath() + "'");
                        }
                    }
                    stream.close();
                } catch (IOException e) {
                    Logging.error("Elevation: IOException during SRTM3 file migration: " + e.toString());
                }
            }
            try {
                DirectoryStream<Path> stream = Files
                        .newDirectoryStream(ElevationPreferences.LEGACY_SRTM_DIRECTORY.toPath());
                if (!stream.iterator().hasNext()) {
                    Files.delete(ElevationPreferences.LEGACY_SRTM_DIRECTORY.toPath());
                    Logging.info("Elevation: Deleted empty legacy SRTM directory '"
                            + ElevationPreferences.LEGACY_SRTM_DIRECTORY.toPath().toAbsolutePath() + "'");
                } else {
                    Logging.info("Elevation: Will not delete legacy SRTM directory '"
                            + ElevationPreferences.LEGACY_SRTM_DIRECTORY.toPath().toAbsolutePath()
                            + "' which is not empty");
                }
                stream.close();
            } catch (IOException e) {
                Logging.error("Elevation: IOException trying to delete legacy SRTM directory: " + e.toString());
            }
        }
    }

    /**
     * Removes the preferences key previously used to enable or disable the complete plugin.
     *
     * @since 0.14.0
     */
    public static void removeElevationEnabledPreference() {
        IPreferences pref = Config.getPref();
        pref.put(ElevationPreferences.LEGACY_ELEVATION_ENABLED, null);
    }
}
