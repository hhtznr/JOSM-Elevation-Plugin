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
import hhtznr.josm.plugins.elevation.gui.SetNodeElevationAction;
import hhtznr.josm.plugins.elevation.gui.SetPeakProminenceAction;
import hhtznr.josm.plugins.elevation.gui.TopographicIsolationFinderAction;
import hhtznr.josm.plugins.elevation.io.SRTMFileDownloader;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.layer.LayerManager;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
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

    private final ElevationDataProvider elevationDataProvider;
    private SRTMFileDownloadErrorDialog srtmFileDownloadErrorDialog = null;

    private LocalElevationLabel localElevationLabel = null;

    private final AddElevationLayerAction addElevationLayerAction;
    private final TopographicIsolationFinderAction isolationFinderAction;
    private final Object elevationLayerLock = new Object();
    private ElevationLayer elevationLayer = null;
    private ElevationToggleDialog elevationToggleDialog = null;
    private final SetNodeElevationAction setNodeElevationAction;
    private final SetPeakProminenceAction setPeakProminenceAction;

    /**
     * Initializes the plugin.
     *
     * @param info Context information about the plugin.
     */
    public ElevationPlugin(PluginInformation info) {
        super(info);
        Migration.migrateSRTMDirectory();
        Migration.removeElevationEnabledPreference();
        createElevationDataDirectories();
        elevationDataProvider = new ElevationDataProvider();
        reconfigureElevationDataProvider();
        addElevationLayerAction = new AddElevationLayerAction(this);
        isolationFinderAction = new TopographicIsolationFinderAction(this);
        addElevationLayerAction.setEnabled(false);
        isolationFinderAction.setEnabled(false);
        MainMenu.add(MainApplication.getMenu().imagerySubMenu, addElevationLayerAction,
                MainMenu.WINDOW_MENU_GROUP.ALWAYS);
        MainMenu.add(MainApplication.getMenu().moreToolsMenu, isolationFinderAction, MainMenu.WINDOW_MENU_GROUP.ALWAYS);
        setNodeElevationAction = new SetNodeElevationAction(elevationDataProvider);
        setNodeElevationAction.setEnabled(false);
        setPeakProminenceAction = new SetPeakProminenceAction(elevationDataProvider);
        setPeakProminenceAction.setEnabled(false);
        if (MainApplication.getToolbar() != null) {
            MainApplication.getToolbar().register(setNodeElevationAction);
            MainApplication.getToolbar().register(setPeakProminenceAction);
        }
        MainApplication.getLayerManager().addLayerChangeListener(this);
        Logging.info("Elevation: Plugin initialized");
    }

    private static void createElevationDataDirectories() {
        List<ElevationDataSource> elevationDataSources = new ArrayList<>();
        elevationDataSources.addAll(ElevationPreferences.ELEVATION_DATA_SOURCES_SRTM1);
        elevationDataSources.addAll(ElevationPreferences.ELEVATION_DATA_SOURCES_SRTM3);
        for (ElevationDataSource elevationDataSource : elevationDataSources) {
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
        return elevationLayer;
    }

    private void removeElevationLayer() {
        synchronized (elevationLayerLock) {
            if (elevationLayer != null) {
                MainApplication.getLayerManager().removeLayer(elevationLayer);
                elevationLayer = null;
            }
        }
    }

    /**
     * Returns whether the elevation layer is enabled. We can deduce that the
     * elevation layer is enabled, if it is not {@code null}.
     *
     * @return {@code true} if the elevation layer is enabled, {@code false}
     *         otherwise.
     */
    public boolean isElevationLayerEnabled() {
        synchronized (elevationLayerLock) {
            return elevationLayer != null;
        }
    }

    /**
     * Returns the elevation data provider used by this plugin to download, read,
     * store and serve elevation data.
     *
     * @return The elevation data provider of this plugin.
     */
    public ElevationDataProvider getElevationDataProvider() {
        return elevationDataProvider;
    }

    /**
     * Called after Main.mapFrame is initialized. (After the first data is loaded).
     * You can use this callback to tweak the newFrame to your needs, as example
     * install an alternative Painter.
     */
    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        addLocalElevationLabel(newFrame);
        // newFrame is null if the map was active and the last layer is closed
        if (newFrame != null)
            setElevationLayerEnabled(ElevationPreferences.getElevationLayerEnabled());
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
     * Reconfigures this plugin's elevation data provider based on the preferences.
     */
    public void reconfigureElevationDataProvider() {
        // SRTM file type to read and download
        SRTMTile.Type srtmType = ElevationPreferences.getSRTMType();
        // Elevation interpolation method
        SRTMTile.Interpolation elevationInterpolation = ElevationPreferences.getElevationInterpolation();
        // Maximum size of the SRTM tile cache
        int cacheSizeLimit = ElevationPreferences.getRAMCacheSizeLimit();
        // Auto-download of SRTM files
        boolean elevationAutoDownloadEnabled = ElevationPreferences.getAutoDownloadEnabled();
        // Configure the elevation data provider
        // Setting the SRTM type also sets the appropriate SRTM data sources and flushes
        // the cache
        elevationDataProvider.setSRTMType(srtmType);
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
            elevationDataProvider.getSRTMFileDownloader().removeDownloadListener(srtmFileDownloadErrorDialog);
            srtmFileDownloadErrorDialog = null;
        }
    }

    /**
     * Enables or disables the elevation layer.
     *
     * @param enabled If {@code true}, the layer is enabled. If {@code false}, the
     *                layer is disabled.
     */
    public void setElevationLayerEnabled(boolean enabled) {
        MapFrame mapFrame = MainApplication.getMap();

        if (enabled) {
            addElevationLayerAction.setEnabled(false);
            synchronized (elevationLayerLock) {
                // Initialize and registers the layer, if it is null
                if (elevationLayer == null) {
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
                    MainApplication.getLayerManager().addLayer(elevationLayer);
                }
                elevationLayer.setRenderingLimit(ElevationPreferences.getElevationLayerRenderingLimit());
                elevationLayer.setContourLineIsostep(ElevationPreferences.getContourLineIsostep());
                elevationLayer.setContourLineStrokeWidth(ElevationPreferences.getContourLineStrokeWidth());
                elevationLayer.setContourLineColor(ElevationPreferences.getContourLineColor());
                elevationLayer.setHillshadeIllumination(ElevationPreferences.getHillshadeAltitude(),
                        ElevationPreferences.getHillshadeAzimuth());
            }
            if (mapFrame != null)
                mapFrame.mapView.repaint();
        } else {
            removeElevationLayer();
            // Allow re-adding the elevation layer if enabled in the preferences
            // i.e. the layer was (temporarily) removed by the user
            if (ElevationPreferences.getElevationLayerEnabled())
                addElevationLayerAction.setEnabled(true);
        }
    }

    private void addLocalElevationLabel(MapFrame mapFrame) {
        if (localElevationLabel == null)
            localElevationLabel = new LocalElevationLabel(mapFrame, elevationDataProvider);
        else
            localElevationLabel.addToMapFrame(mapFrame);
    }

    @Override
    public void layerAdded(LayerAddEvent e) {
        synchronized (elevationLayerLock) {
            if (e.getAddedLayer().equals(elevationLayer)) {
                addElevationLayerAction.setEnabled(false);
                if (elevationToggleDialog == null)
                    elevationToggleDialog = new ElevationToggleDialog(this);
                MapFrame mapFrame = MainApplication.getMap();
                if (mapFrame != null)
                    mapFrame.addToggleDialog(elevationToggleDialog);
            }
        }
        boolean hasDataLayer = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class).size() > 0;
        if (hasDataLayer) {
            isolationFinderAction.setEnabled(true);
            setNodeElevationAction.setEnabled(true);
            setPeakProminenceAction.setEnabled(true);
        }
    }

    @Override
    public void layerRemoving(LayerRemoveEvent e) {
        synchronized (elevationLayerLock) {
            if (e.getRemovedLayer().equals(elevationLayer)) {
                MapFrame mapFrame = MainApplication.getMap();
                if (mapFrame != null && elevationToggleDialog != null) {
                    mapFrame.removeToggleDialog(elevationToggleDialog);
                    elevationToggleDialog = null;
                }
                elevationLayer = null;
                addElevationLayerAction.setEnabled(true);
            }
        }
        boolean hasDataLayer = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class).size() > 0;
        if (!hasDataLayer) {
            isolationFinderAction.setEnabled(false);
            setNodeElevationAction.setEnabled(false);
            setPeakProminenceAction.setEnabled(false);
        }
    }

    @Override
    public void layerOrderChanged(LayerOrderChangeEvent e) {
    }
}
