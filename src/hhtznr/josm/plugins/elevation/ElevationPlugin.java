package hhtznr.josm.plugins.elevation;

import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.gui.AddElevationLayerAction;
import hhtznr.josm.plugins.elevation.gui.ElevationLayer;
import hhtznr.josm.plugins.elevation.gui.ElevationTabPreferenceSetting;
import hhtznr.josm.plugins.elevation.gui.LocalElevationLabel;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.layer.LayerManager;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.IPreferences;
import org.openstreetmap.josm.tools.Logging;

/**
 * Plugin class for displaying and visualizing elevation data.
 *
 * @author Harald Hetzner
 */
public class ElevationPlugin extends Plugin implements LayerManager.LayerChangeListener {

    private ElevationTabPreferenceSetting tabPreferenceSetting = null;

    private boolean elevationEnabled = Config.getPref().getBoolean(ElevationPreferences.ELEVATION_ENABLED,
            ElevationPreferences.DEFAULT_ELEVATION_ENABLED);

    private ElevationDataProvider elevationDataProvider = null;

    private LocalElevationLabel localElevationLabel = null;

    private final AddElevationLayerAction addElevationLayerAction;
    private boolean elevationLayerEnabled = true;
    private ElevationLayer elevationLayer = null;

    /**
     * Initializes the plugin.
     *
     * @param info Context information about the plugin.
     */
    public ElevationPlugin(PluginInformation info) {
        super(info);
        addElevationLayerAction = new AddElevationLayerAction(this);
        addElevationLayerAction.setEnabled(false);
        MainMenu.add(MainApplication.getMenu().imagerySubMenu, addElevationLayerAction,
                MainMenu.WINDOW_MENU_GROUP.ALWAYS);
        Logging.info("Elevation: Plugin initialized");
    }

    /**
     * Returns the elevation layer.
     *
     * @return The elevation layer which displays contour lines and hillshade.
     */
    public ElevationLayer getElevationLayer() {
        if (elevationLayerEnabled && elevationLayer == null) {
            IPreferences pref = Config.getPref();
            double renderingLimit = pref.getDouble(ElevationPreferences.ELEVATION_LAYER_RENDERING_LIMIT,
                    ElevationPreferences.DEFAULT_ELEVATION_LAYER_RENDERING_LIMIT);
            int isostep = pref.getInt(ElevationPreferences.CONTOUR_LINE_ISOSTEP,
                    ElevationPreferences.DEFAULT_CONTOUR_LINE_ISOSTEP);
            int altitude = pref.getInt(ElevationPreferences.HILLSHADE_ALTITUDE,
                    ElevationPreferences.DEFAULT_HILLSHADE_ALTITUDE);
            int azimuth = pref.getInt(ElevationPreferences.HILLSHADE_AZIMUTH,
                    ElevationPreferences.DEFAULT_HILLSHADE_AZIMUTH);
            elevationLayer = new ElevationLayer(elevationDataProvider, renderingLimit, isostep, altitude, azimuth);
        }
        return elevationLayer;
    }

    /**
     * Called after Main.mapFrame is initialized. (After the first data is loaded).
     * You can use this callback to tweak the newFrame to your needs, as example
     * install an alternative Painter.
     */
    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
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
            tabPreferenceSetting = new ElevationTabPreferenceSetting(this);
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
        // Elevation enabled
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

            // Maximum size of the SRTM tile cache
            int cacheSizeLimit = pref.getInt(ElevationPreferences.RAM_CACHE_SIZE_LIMIT,
                    ElevationPreferences.DEFAULT_RAM_CACHE_SIZE_LIMIT);
            // Elevation layer
            elevationLayerEnabled = pref.getBoolean(ElevationPreferences.ELEVATION_LAYER_ENABLED,
                    ElevationPreferences.DEFAULT_ELEVATION_LAYER_ENABLED);
            // Auto-download of SRTM files
            boolean elevationAutoDownloadEnabled = pref.getBoolean(ElevationPreferences.ELEVATION_AUTO_DOWNLOAD_ENABLED,
                    ElevationPreferences.DEFAULT_ELEVATION_AUTO_DOWNLOAD_ENABLED);
            // Initialize and configure the elevation data provider
            if (elevationDataProvider == null)
                elevationDataProvider = new ElevationDataProvider();
            elevationDataProvider.setPreferredSRTMType(preferredSRTMType);
            elevationDataProvider.setElevationInterpolation(elevationInterpolation);
            elevationDataProvider.setCacheSizeLimit(cacheSizeLimit);
            elevationDataProvider.setAutoDownloadEnabled(elevationAutoDownloadEnabled);
            if (mapFrame != null) {
                if (localElevationLabel == null)
                    localElevationLabel = new LocalElevationLabel(mapFrame, elevationDataProvider);
                else
                    localElevationLabel.addToMapFrame(mapFrame);

                if (elevationLayerEnabled) {
                    double renderingLimit = pref.getDouble(ElevationPreferences.ELEVATION_LAYER_RENDERING_LIMIT,
                            ElevationPreferences.DEFAULT_ELEVATION_LAYER_RENDERING_LIMIT);
                    int isostep = pref.getInt(ElevationPreferences.CONTOUR_LINE_ISOSTEP,
                            ElevationPreferences.DEFAULT_CONTOUR_LINE_ISOSTEP);
                    int altitude = pref.getInt(ElevationPreferences.HILLSHADE_ALTITUDE,
                            ElevationPreferences.DEFAULT_HILLSHADE_ALTITUDE);
                    int azimuth = pref.getInt(ElevationPreferences.HILLSHADE_AZIMUTH,
                            ElevationPreferences.DEFAULT_HILLSHADE_AZIMUTH);
                    if (elevationLayer == null) {
                        elevationLayer = new ElevationLayer(elevationDataProvider, renderingLimit, isostep, altitude, azimuth);
                        MainApplication.getLayerManager().addLayer(elevationLayer);
                    }
                    else {
                        elevationLayer.setRenderingLimit(renderingLimit);
                        elevationLayer.setContourLineIsostep(isostep);
                        elevationLayer.setHillshadeIllumination(altitude, azimuth);
                    }
                }
            }
            if (!elevationLayerEnabled) {
                if (elevationLayer != null) {
                    MainApplication.getLayerManager().removeLayer(elevationLayer);
                    elevationLayer = null;
                }
                addElevationLayerAction.setEnabled(false);
            }
        }
        // Elevation disabled
        else {
            if (localElevationLabel != null) {
                localElevationLabel.remove();
                localElevationLabel = null;
            }
            addElevationLayerAction.setEnabled(false);
            if (elevationLayer != null) {
                elevationLayerEnabled = false;
                MainApplication.getLayerManager().removeLayer(elevationLayer);
                elevationLayer = null;
            }
            elevationDataProvider = null;
        }
        elevationEnabled = enabled;
    }

    @Override
    public void layerAdded(LayerAddEvent e) {
        if (e.getAddedLayer().equals(elevationLayer))
            addElevationLayerAction.setEnabled(false);
    }

    @Override
    public void layerRemoving(LayerRemoveEvent e) {
        if (e.getRemovedLayer().equals(elevationLayer)) {
            elevationLayer = null;
            addElevationLayerAction.setEnabled(true);
        }

    }

    @Override
    public void layerOrderChanged(LayerOrderChangeEvent e) {
    }
}
