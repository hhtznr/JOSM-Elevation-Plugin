package hhtznr.josm.plugins.elevation;

import java.io.File;
import java.nio.file.Paths;

import org.openstreetmap.josm.data.Preferences;

/**
 * Property keys and default values for elevation data preferences.
 *
 * @author Harald Hetzner
 */
public class ElevationPreferences {

    /**
     * Default path, where elevation data is stored.
     */
    public static final File DEFAULT_ELEVATION_DIRECTORY = Paths
            .get(Preferences.main().getDirs().getCacheDirectory(true).toString(), "elevation").toFile();

    /**
     * Default path, where SRTM1 and SRTM3 files need to be located.
     */
    public static final File DEFAULT_SRTM_DIRECTORY = Paths.get(DEFAULT_ELEVATION_DIRECTORY.toString(), "SRTM")
            .toFile();
}
