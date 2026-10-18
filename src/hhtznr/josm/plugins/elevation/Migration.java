package hhtznr.josm.plugins.elevation;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.data.srtm.SrtmTile;
import hhtznr.josm.plugins.elevation.io.SrtmFiles;

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
        boolean migrateSRTM1 = true;
        boolean migrateSRTM3 = true;
        if (Files.isDirectory(ElevationPreferences.LEGACY_SRTM_DIRECTORY)) {
            migrateSRTM1 = false;
            migrateSRTM3 = false;
        } else {
            return;
        }

        if (!Files.isDirectory(ElevationPreferences.DEFAULT_EARTHDATA_SRTM1_DIRECTORY)) {
            try {
                Files.createDirectories(ElevationPreferences.DEFAULT_EARTHDATA_SRTM1_DIRECTORY);
            } catch (IOException e) {
                Logging.error("Elevation: IOException during SRTM1 file migration: " + e.toString());
                migrateSRTM1 = false;
            }
        }

        if (!Files.isDirectory(ElevationPreferences.DEFAULT_EARTHDATA_SRTM3_DIRECTORY)) {
            try {
                Files.createDirectories(ElevationPreferences.DEFAULT_EARTHDATA_SRTM3_DIRECTORY);
            } catch (IOException e) {
                Logging.error("Elevation: IOException during SRTM3 file migration: " + e.toString());
                migrateSRTM3 = false;
            }
        }

        if (migrateSRTM1) {
            try (DirectoryStream<Path>stream = Files.newDirectoryStream(ElevationPreferences.LEGACY_SRTM_DIRECTORY)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file) && SrtmFiles.getSRTMTileTypeFromEarthdataFileName(
                            file.getFileName().toString()) == SrtmTile.Type.SRTM1) {
                        Path targetPath = ElevationPreferences.DEFAULT_EARTHDATA_SRTM1_DIRECTORY
                                .resolve(file.getFileName());
                        Files.move(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        Logging.info("Elevation: Migrated SRTM file '" + file.toString() + "' to '"
                                + targetPath.toAbsolutePath() + "'");
                    }
                }

            } catch (IOException e) {
                Logging.error("Elevation: IOException during SRTM1 file migration: " + e.toString());
            }
        }

        if (migrateSRTM3) {
            try (DirectoryStream<Path>stream = Files.newDirectoryStream(ElevationPreferences.LEGACY_SRTM_DIRECTORY)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file) && SrtmFiles.getSRTMTileTypeFromEarthdataFileName(
                            file.getFileName().toString()) == SrtmTile.Type.SRTM3) {
                        Path targetPath = ElevationPreferences.DEFAULT_EARTHDATA_SRTM3_DIRECTORY
                                .resolve(file.getFileName());
                        Files.move(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        Logging.info("Elevation: Migrated SRTM file '" + file.toString() + "' to '"
                                + targetPath.toAbsolutePath() + "'");
                    }
                }
            } catch (IOException e) {
                Logging.error("Elevation: IOException during SRTM3 file migration: " + e.toString());
            }
        }

        try (DirectoryStream<Path>stream = Files.newDirectoryStream(ElevationPreferences.LEGACY_SRTM_DIRECTORY)) {
            if (!stream.iterator().hasNext()) {
                Files.delete(ElevationPreferences.LEGACY_SRTM_DIRECTORY);
                Logging.info("Elevation: Deleted empty legacy SRTM directory '"
                        + ElevationPreferences.LEGACY_SRTM_DIRECTORY.toAbsolutePath().toString() + "'");
            } else {
                Logging.info("Elevation: Will not delete legacy SRTM directory '"
                        + ElevationPreferences.LEGACY_SRTM_DIRECTORY.toAbsolutePath().toString()
                        + "' which is not empty");
            }

        } catch (IOException e) {
            Logging.error("Elevation: IOException trying to delete legacy SRTM directory: " + e.toString());
        }
    }

    /**
     * Removes the preferences key previously used to enable or disable the complete
     * plugin.
     *
     * @since 0.14.0
     */
    public static void removeElevationEnabledPreference() {
        if (ElevationPreferences.removeElevationEnabledPreference()) {
            Logging.info("Removed legacy 'elevation enabled' preference");
        }
    }
}
