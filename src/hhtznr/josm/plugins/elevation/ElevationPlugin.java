package hhtznr.josm.plugins.elevation;

import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.gui.ElevationTabPreferenceSetting;
import hhtznr.josm.plugins.elevation.gui.LocalElevationLabel;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;

/**
 * Plugin class for displaying an the local elevation at the mouse location on
 * the map.
 *
 * @author Harald Hetzner
 */
public class ElevationPlugin extends Plugin {

    private ElevationTabPreferenceSetting tabPreferenceSetting = null;

    private boolean elevationEnabled = Config.getPref().getBoolean(ElevationPreferences.ELEVATION_ENABLED,
            ElevationPreferences.DEFAULT_ELEVATION_ENABLED);

    private LocalElevationLabel localElevationLabel = null;

    private static ElevationPlugin instance = null;

    public static ElevationPlugin getInstance() {
        return instance;
    }

    /**
     * Initializes the plugin.
     *
     * @param info Context information about the plugin.
     */
    public ElevationPlugin(PluginInformation info) {
        super(info);
        instance = this;
        Logging.info("Elevation: Plugin initialized");
    }

    /**
     * Called after Main.mapFrame is initialized. (After the first data is loaded).
     * You can use this callback to tweak the newFrame to your needs, as example
     * install an alternative Painter.
     */
    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        super.mapFrameInitialized(oldFrame, newFrame);
        setElevationEnabled(elevationEnabled, newFrame);
    }

    /**
     * Called in the preferences dialog to create a preferences page for the plugin,
     * if any available.
     *
     * @return The preferences page.
     */
    @Override
    public PreferenceSetting getPreferenceSetting() {
        if (tabPreferenceSetting == null)
            tabPreferenceSetting = new ElevationTabPreferenceSetting();
        return tabPreferenceSetting;
    }

    /**
     * Returns whether displaying elevation is enabled.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    public boolean isElevationEnabled() {
        return elevationEnabled;
    }

    /**
     * Enable or disable displaying elevation at the position of the mouse pointer.
     *
     * @param enabled If {@code true} displaying of elevation is enabled, else
     *                disabled.
     */
    public void setElevationEnabled(boolean enabled) {
        setElevationEnabled(enabled, MainApplication.getMap());
    }

    private void setElevationEnabled(boolean enabled, MapFrame mapFrame) {
        if (mapFrame != null && enabled) {
            if (localElevationLabel == null)
                localElevationLabel = new LocalElevationLabel(mapFrame);
            else
                localElevationLabel.addToMapFrame(mapFrame);
            localElevationLabel.setVisible(true);
        }

        if (enabled) {
            // STRM file type that is preferred for reading and downloading
            SRTMTile.Type preferredSRTMType = SRTMTile.Type
                    .fromName(Config.getPref().get(ElevationPreferences.PREFERRED_SRTM_TYPE,
                            ElevationPreferences.DEFAULT_PREFERRED_SRTM_TYPE.getName()));
            // Auto-download of SRTM files
            boolean elevationAutoDownloadEnabled = Config.getPref().getBoolean(
                    ElevationPreferences.ELEVATION_AUTO_DOWNLOAD_ENABLED,
                    ElevationPreferences.DEFAULT_ELEVATION_AUTO_DOWNLOAD_ENABLED);
            // Initialize and configure the SRTM file reader
            SRTMFileReader.getInstance().setPreferredSRTMType(preferredSRTMType);
            SRTMFileReader.getInstance().setAutoDownloadEnabled(elevationAutoDownloadEnabled);
        } else {
            if (localElevationLabel != null) {
                localElevationLabel.remove();
                localElevationLabel = null;
            }
            SRTMFileReader.destroyInstance();
        }
        elevationEnabled = enabled;
    }
}
