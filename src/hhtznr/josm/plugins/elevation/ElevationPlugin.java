package hhtznr.josm.plugins.elevation;

import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.ElevationDataSource;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.gui.AddElevationLayerAction;
import hhtznr.josm.plugins.elevation.gui.ElevationLayer;
import hhtznr.josm.plugins.elevation.gui.ElevationTabPreferenceSetting;
import hhtznr.josm.plugins.elevation.gui.ElevationToggleDialog;
import hhtznr.josm.plugins.elevation.gui.LocalElevationLabel;
import hhtznr.josm.plugins.elevation.gui.SRTMFileDownloadErrorDialog;
import hhtznr.josm.plugins.elevation.io.SRTMFileDownloader;

import java.awt.Color;

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
import org.openstreetmap.josm.tools.Logging;

/**
 * Plugin class for displaying and visualizing elevation data.
 *
 * @author Harald Hetzner
 */
public class ElevationPlugin extends Plugin implements LayerManager.LayerChangeListener {

    private ElevationTabPreferenceSetting tabPreferenceSetting = null;

    private boolean elevationEnabled = ElevationPreferences.getElevationEnabled();

    private ElevationDataProvider elevationDataProvider = null;
    private SRTMFileDownloadErrorDialog srtmFileDownloadErrorDialog = null;

    private LocalElevationLabel localElevationLabel = null;

    private final AddElevationLayerAction addElevationLayerAction;
    private boolean elevationLayerEnabled = true;
    private ElevationLayer elevationLayer = null;
    private ElevationToggleDialog elevationToggleDialog = null;

    /**
     * Initializes the plugin.
     *
     * @param info Context information about the plugin.
     */
    public ElevationPlugin(PluginInformation info) {
        super(info);
        Migration.migrateSRTMDirectory();
        createElevationDataDirectories();
        addElevationLayerAction = new AddElevationLayerAction(this);
        addElevationLayerAction.setEnabled(false);
        MainMenu.add(MainApplication.getMenu().imagerySubMenu, addElevationLayerAction,
                MainMenu.WINDOW_MENU_GROUP.ALWAYS); 
        Logging.info("Elevation: Plugin initialized");
    }

    private static void createElevationDataDirectories() {
        for (ElevationDataSource elevationDataSource : ElevationPreferences.ELEVATION_DATA_SOURCES) {
            if (!elevationDataSource.getDataDirectory().exists() && elevationDataSource.getDataDirectory().mkdirs())
                Logging.info("Elevation: Created elevation data directory '"
                        + elevationDataSource.getDataDirectory().getAbsolutePath() + "'");
        }
    }

    /**
     * Returns the elevation layer.
     *
     * @return The elevation layer which displays contour lines and hillshade.
     */
    public ElevationLayer getElevationLayer() {
        if (elevationLayerEnabled && elevationLayer == null) {
            double renderingLimit = ElevationPreferences.getElevationLayerRenderingLimit();
            int isostep = ElevationPreferences.getContourLineIsostep();
            float strokeWidth = ElevationPreferences.getContourLineStrokeWidth();
            Color color = ElevationPreferences.getContourLineColor();
            int altitude = ElevationPreferences.getHillshadeAltitude();
            int azimuth = ElevationPreferences.getHillshadeAzimuth();
            int lowerCutoff = ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION;
            int upperCutoff = ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION;
            elevationLayer = new ElevationLayer(elevationDataProvider, renderingLimit, isostep, strokeWidth, color,
                    altitude, azimuth, lowerCutoff, upperCutoff);
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
            // SRTM file type that is preferred for reading and downloading
            SRTMTile.Type preferredSRTMType = ElevationPreferences.getPreferredSRTMType();
            // Elevation interpolation method
            SRTMTile.Interpolation elevationInterpolation = ElevationPreferences.getElevationInterpolation();

            // Maximum size of the SRTM tile cache
            int cacheSizeLimit = ElevationPreferences.getRAMCacheSizeLimit();
            // Elevation layer
            elevationLayerEnabled = ElevationPreferences.getElevationLayerEnabled();
            // Auto-download of SRTM files
            boolean elevationAutoDownloadEnabled = ElevationPreferences.getAutoDownloadEnabled();
            // Initialize and configure the elevation data provider
            if (elevationDataProvider == null)
                elevationDataProvider = new ElevationDataProvider();
            elevationDataProvider.setPreferredSRTMType(preferredSRTMType);
            elevationDataProvider.setElevationInterpolation(elevationInterpolation);
            elevationDataProvider.setCacheSizeLimit(cacheSizeLimit);
            elevationDataProvider.setAutoDownloadEnabled(elevationAutoDownloadEnabled);
            if (elevationAutoDownloadEnabled) {
                SRTMFileDownloader.AuthType authType = ElevationPreferences.getElevationServerAuthType();
                if (authType == SRTMFileDownloader.AuthType.BASIC)
                    elevationDataProvider.getSRTMFileDownloader().setPasswordAuthentication(
                            ElevationPreferences.lookupEarthdataCredentials(), ElevationPreferences.EARTHDATA_SSO_HOST);
                else if (authType == SRTMFileDownloader.AuthType.BEARER_TOKEN)
                    elevationDataProvider.getSRTMFileDownloader()
                            .setOAuthToken(ElevationPreferences.lookupEarthdataOAuthToken());
                if (srtmFileDownloadErrorDialog == null)
                    srtmFileDownloadErrorDialog = new SRTMFileDownloadErrorDialog(
                            elevationDataProvider.getSRTMFileDownloader());
            } else {
                srtmFileDownloadErrorDialog = null;
            }
            if (mapFrame != null) {
                if (localElevationLabel == null)
                    localElevationLabel = new LocalElevationLabel(mapFrame, elevationDataProvider);
                else
                    localElevationLabel.addToMapFrame(mapFrame);

                if (elevationLayerEnabled) {
                    if (elevationLayer == null) {
                        MainApplication.getLayerManager().addLayer(getElevationLayer());
                    } else {
                        elevationLayer.setRenderingLimit(ElevationPreferences.getElevationLayerRenderingLimit());
                        elevationLayer.setContourLineIsostep(ElevationPreferences.getContourLineIsostep());
                        elevationLayer.setContourLineStrokeWidth(ElevationPreferences.getContourLineStrokeWidth());
                        elevationLayer.setContourLineColor(ElevationPreferences.getContourLineColor());
                        elevationLayer.setHillshadeIllumination(ElevationPreferences.getHillshadeAltitude(),
                                ElevationPreferences.getHillshadeAzimuth());
                        elevationLayer.repaint();
                    }
                    elevationToggleDialog = new ElevationToggleDialog(this);
                    mapFrame.addToggleDialog(elevationToggleDialog);
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
            if (elevationToggleDialog != null) {
                if (mapFrame != null)
                    mapFrame.removeToggleDialog(elevationToggleDialog);
                elevationToggleDialog = null;
            }
            if (srtmFileDownloadErrorDialog != null) {
                // Removes the download listener
                srtmFileDownloadErrorDialog.disable();
                srtmFileDownloadErrorDialog = null;
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
