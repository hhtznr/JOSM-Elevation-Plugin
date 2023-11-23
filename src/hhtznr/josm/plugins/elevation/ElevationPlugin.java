package hhtznr.josm.plugins.elevation;

import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.data.SRTMTileProvider;
import hhtznr.josm.plugins.elevation.gui.ElevationLayer;
import hhtznr.josm.plugins.elevation.gui.ElevationMapMode;
import hhtznr.josm.plugins.elevation.gui.ElevationTabPreferenceSetting;
import hhtznr.josm.plugins.elevation.gui.LocalElevationLabel;

import org.openstreetmap.josm.gui.IconToggleButton;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.IPreferences;
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

    private ElevationLayer elevationLayer = null;

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

    public ElevationLayer getElevationLayer() {
        return elevationLayer;
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
        if (enabled) {
            IPreferences pref = Config.getPref();
            // SRTM file type that is preferred for reading and downloading
            SRTMTile.Type preferredSRTMType = SRTMTile.Type
                    .fromString(pref.get(ElevationPreferences.PREFERRED_SRTM_TYPE,
                            ElevationPreferences.DEFAULT_PREFERRED_SRTM_TYPE.toString()));
            // Elevation interpolation method
            SRTMTile.Interpolation elevationInterpolation = SRTMTile.Interpolation
                    .fromString(pref.get(ElevationPreferences.ELEVATION_INTERPOLATION,
                            ElevationPreferences.DEFAULT_ELEVATION_INTERPOLATION.toString()));
            // Elevation layer
            boolean elevationLayerEnabled = pref.getBoolean(ElevationPreferences.ELEVATION_LAYER_ENABLED,
                    ElevationPreferences.DEFAULT_ELEVATION_LAYER_ENABLED);
            // Auto-download of SRTM files
            boolean elevationAutoDownloadEnabled = pref.getBoolean(ElevationPreferences.ELEVATION_AUTO_DOWNLOAD_ENABLED,
                    ElevationPreferences.DEFAULT_ELEVATION_AUTO_DOWNLOAD_ENABLED);
            // Initialize and configure the SRTM file reader
            SRTMTileProvider.getInstance().setPreferredSRTMType(preferredSRTMType);
            SRTMTileProvider.getInstance().setElevationInterpolation(elevationInterpolation);
            SRTMTileProvider.getInstance().setAutoDownloadEnabled(elevationAutoDownloadEnabled);
            if (mapFrame != null) {
                if (localElevationLabel == null)
                    localElevationLabel = new LocalElevationLabel(mapFrame);
                else
                    localElevationLabel.addToMapFrame(mapFrame);

                if (elevationLayerEnabled && elevationLayer == null) {
                    double renderingLimit = pref.getDouble(ElevationPreferences.ELEVATION_LAYER_RENDERING_LIMIT,
                            ElevationPreferences.DEFAULT_ELEVATION_LAYER_RENDERING_LIMIT);
                    int isostep = pref.getInt(ElevationPreferences.CONTOUR_LINE_ISOSTEP,
                            ElevationPreferences.DEFAULT_CONTOUR_LINE_ISOSTEP);
                    int altitude = pref.getInt(ElevationPreferences.HILLSHADE_ALTITUDE,
                            ElevationPreferences.DEFAULT_HILLSHADE_ALTITUDE);
                    int azimuth = pref.getInt(ElevationPreferences.HILLSHADE_AZIMUTH,
                            ElevationPreferences.DEFAULT_HILLSHADE_AZIMUTH);
                    elevationLayer = new ElevationLayer(renderingLimit, isostep, altitude, azimuth);
                    MainApplication.getLayerManager().addLayer(elevationLayer);
                    mapFrame.addMapMode(new IconToggleButton(new ElevationMapMode(elevationLayer)));
                }
            }
        } else {
            if (localElevationLabel != null) {
                localElevationLabel.remove();
                localElevationLabel = null;
            }
            if (elevationLayer != null) {
                MainApplication.getLayerManager().removeLayer(elevationLayer);
                elevationLayer = null;
                // TODO: Map mode?
            }
            SRTMTileProvider.destroyInstance();
        }
        elevationEnabled = enabled;
    }
}
