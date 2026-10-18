package hhtznr.josm.plugins.elevation;

import java.awt.Color;
import java.io.IOException;
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

import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.concurrent.ParallelExecutorService;
import hhtznr.josm.plugins.elevation.data.Coloring;
import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.ElevationDataSource;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.gui.AddElevationLayerAction;
import hhtznr.josm.plugins.elevation.gui.AddGeoTiffEnvelopeLayerAction;
import hhtznr.josm.plugins.elevation.gui.ElevationTabPreferenceSetting;
import hhtznr.josm.plugins.elevation.gui.GeoTiffEnvelopeLayer;
import hhtznr.josm.plugins.elevation.gui.KeyColFinderAction;
import hhtznr.josm.plugins.elevation.gui.LocalElevationLabel;
import hhtznr.josm.plugins.elevation.gui.MapViewElevationDataConsumer;
import hhtznr.josm.plugins.elevation.gui.OpenGeoTiffAction;
import hhtznr.josm.plugins.elevation.gui.SRTMElevationLayer;
import hhtznr.josm.plugins.elevation.gui.SRTMFileDownloadErrorDialog;
import hhtznr.josm.plugins.elevation.gui.SetNodeElevationAction;
import hhtznr.josm.plugins.elevation.gui.SetPeakProminenceAction;
import hhtznr.josm.plugins.elevation.gui.TopographicIsolationFinderAction;
import hhtznr.josm.plugins.elevation.io.SRTMFileDownloader;

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
    private static MapViewElevationDataConsumer mapViewElevationDataConsumer = null;

    private final OpenGeoTiffAction openGeoTiffAction;
    private final AddElevationLayerAction addElevationLayerAction;
    private final AddGeoTiffEnvelopeLayerAction addGeoTiffEnvelopeLayerAction;
    private final TopographicIsolationFinderAction isolationFinderAction;
    private final KeyColFinderAction keyColFinderAction;
    private final Object elevationLayerLock = new Object();
    private SRTMElevationLayer elevationLayer = null;
    private final Object geoTiffEnvelopeLayerLock = new Object();
    private GeoTiffEnvelopeLayer geoTiffEnvelopeLayer = null;
    private final SetNodeElevationAction setNodeElevationAction;
    private final SetPeakProminenceAction setPeakProminenceAction;

    private static final ParallelExecutorService PARALLEL_EXECUTOR_SERVICE = new ParallelExecutorService();

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

        openGeoTiffAction = new OpenGeoTiffAction();
        openGeoTiffAction.setEnabled(false);
        MainMenu.add(MainApplication.getMenu().fileMenu, openGeoTiffAction, MainMenu.WINDOW_MENU_GROUP.ALWAYS);

        addElevationLayerAction = new AddElevationLayerAction(this);
        addElevationLayerAction.setEnabled(false);
        MainMenu.add(MainApplication.getMenu().imagerySubMenu, addElevationLayerAction,
                MainMenu.WINDOW_MENU_GROUP.ALWAYS);

        addGeoTiffEnvelopeLayerAction = new AddGeoTiffEnvelopeLayerAction(this);
        addGeoTiffEnvelopeLayerAction.setEnabled(false);
        MainMenu.add(MainApplication.getMenu().imagerySubMenu, addGeoTiffEnvelopeLayerAction,
                MainMenu.WINDOW_MENU_GROUP.ALWAYS);

        // Register shutdown thread to save cached GeoTIFF envelopes upon JOSM shutdown
        Runtime.getRuntime().addShutdownHook(new Thread("elevation-shutdown") {
            @Override
            public void run() {
                try {
                    elevationDataProvider.getGeoTiffIndex().saveGeoTiffRecordsToCacheFile();
                } catch (IOException e) {
                    System.err.println("Elevation: Could not save the cached GeoTIFF envelopes to '"
                            + elevationDataProvider.getGeoTiffIndex().getCacheFilePath().toString() + "': "
                            + e.toString());
                }
            }
        });

        isolationFinderAction = new TopographicIsolationFinderAction(this);
        isolationFinderAction.setEnabled(false);
        MainMenu.add(MainApplication.getMenu().moreToolsMenu, isolationFinderAction, MainMenu.WINDOW_MENU_GROUP.ALWAYS);

        keyColFinderAction = new KeyColFinderAction(this);
        keyColFinderAction.setEnabled(false);
        MainMenu.add(MainApplication.getMenu().moreToolsMenu, keyColFinderAction, MainMenu.WINDOW_MENU_GROUP.ALWAYS);

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
            if (!elevationDataSource.getDataDirectory().exists() && elevationDataSource.getDataDirectory().mkdirs()) {
                Logging.info("Elevation: Created elevation data directory '"
                        + elevationDataSource.getDataDirectory().getAbsolutePath() + "'");
            }
        }
    }

    /**
     * Returns the common parallel executor service of this plugin.
     *
     * @return The parallel executor service for execution of tasks in parallel
     *         threads.
     */
    public static ParallelExecutorService getParallelExecutor() {
        return PARALLEL_EXECUTOR_SERVICE;
    }

    /**
     * Returns the elevation layer.
     *
     * @return The elevation layer which displays contour lines and hillshade.
     */
    public SRTMElevationLayer getElevationLayer() {
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

    private void removeGeoTiffEnvelopeLayer() {
        synchronized (geoTiffEnvelopeLayerLock) {
            if (geoTiffEnvelopeLayer != null) {
                MainApplication.getLayerManager().removeLayer(geoTiffEnvelopeLayer);
                geoTiffEnvelopeLayer = null;
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
        if (newFrame == null) {
            if (mapViewElevationDataConsumer != null) {
                mapViewElevationDataConsumer.dispose();
                mapViewElevationDataConsumer = null;
            }
            openGeoTiffAction.setEnabled(false);
        } else {
            if (mapViewElevationDataConsumer == null) {
                double renderingLimit = ElevationPreferences.getElevationLayerRenderingLimit();
                try {
                    mapViewElevationDataConsumer = elevationDataProvider.getMapViewElevationDataConsumer(newFrame,
                            renderingLimit);
                } catch (AsyncOperationException e) {
                    mapViewElevationDataConsumer = null;
                }
            }
            mapViewElevationDataConsumer.addToMapFrame(newFrame);
            openGeoTiffAction.setEnabled(true);
        }

        addLocalElevationLabel(newFrame);
        // newFrame is null if the map was active and the last layer is closed
        if (newFrame != null) {
            setElevationLayerEnabled(ElevationPreferences.getElevationLayerEnabled());
            setGeoTiffEnvelopeLayerEnabled(ElevationPreferences.getGeoTiffEnvelopeLayerEnabled(), false);
        }
    }

    /**
     * Called in the preferences dialog to create a preferences page for the plugin,
     * if any available.
     *
     * @return The preferences page.
     */
    @Override
    public PreferenceSetting getPreferenceSetting() {
        if (tabPreferenceSetting == null) {
            tabPreferenceSetting = new ElevationTabPreferenceSetting(this);
        }
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
        if (elevationDataProvider.setSRTMType(srtmType)) {
            // Renew or delete the map view elevation data consumer
            MapFrame mapFrame = MainApplication.getMap();
            if (mapFrame != null) {
                double renderingLimit = ElevationPreferences.getElevationLayerRenderingLimit();
                try {
                    mapViewElevationDataConsumer = elevationDataProvider.getMapViewElevationDataConsumer(mapFrame,
                            renderingLimit);
                } catch (AsyncOperationException e) {
                    mapViewElevationDataConsumer = null;
                }
            } else {
                mapViewElevationDataConsumer = null;
            }
        }
        elevationDataProvider.setElevationInterpolation(elevationInterpolation);
        elevationDataProvider.setCacheSizeLimit(cacheSizeLimit);
        elevationDataProvider.setAutoDownloadEnabled(elevationAutoDownloadEnabled);
        if (elevationAutoDownloadEnabled) {
            SRTMFileDownloader.AuthType authType = ElevationPreferences.getElevationServerAuthType();
            if (authType == SRTMFileDownloader.AuthType.BASIC) {
                elevationDataProvider.getSRTMFileDownloader().setPasswordAuthentication(
                        ElevationPreferences.lookupEarthdataCredentials(), ElevationPreferences.EARTHDATA_SSO_HOST);
            } else if (authType == SRTMFileDownloader.AuthType.BEARER_TOKEN) {
                elevationDataProvider.getSRTMFileDownloader()
                        .setOAuthToken(ElevationPreferences.lookupEarthdataOAuthToken());
            }
            if (srtmFileDownloadErrorDialog == null) {
                srtmFileDownloadErrorDialog = new SRTMFileDownloadErrorDialog(
                        elevationDataProvider.getSRTMFileDownloader());
            }
        } else {
            if (elevationDataProvider.getSRTMFileDownloader() != null) {
                elevationDataProvider.getSRTMFileDownloader().removeDownloadListener(srtmFileDownloadErrorDialog);
            }
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
                    Coloring.Scheme coloringScheme = ElevationPreferences.getContourLineColoringScheme();
                    Color color = ElevationPreferences.getContourLineColor();
                    int altitude = ElevationPreferences.getHillshadeAltitude();
                    int azimuth = ElevationPreferences.getHillshadeAzimuth();
                    short lowerCutoff = ElevationPreferences.DEFAULT_LOWER_CUTOFF_ELEVATION;
                    short upperCutoff = ElevationPreferences.DEFAULT_UPPER_CUTOFF_ELEVATION;
                    elevationLayer = new SRTMElevationLayer(elevationDataProvider, renderingLimit, isostep, strokeWidth,
                            coloringScheme, color, altitude, azimuth, lowerCutoff, upperCutoff);
                    MainApplication.getLayerManager().addLayer(elevationLayer);
                } else {
                    elevationLayer.setRenderingLimit(ElevationPreferences.getElevationLayerRenderingLimit());
                    elevationLayer.setContourLineIsostep(ElevationPreferences.getContourLineIsostep());
                    elevationLayer.setContourLineStrokeWidth(ElevationPreferences.getContourLineStrokeWidth());
                    elevationLayer.setContourLineColoringScheme(ElevationPreferences.getContourLineColoringScheme());
                    elevationLayer.setContourLineConstantColor(ElevationPreferences.getContourLineColor());
                    elevationLayer.setHillshadeIllumination(ElevationPreferences.getHillshadeAltitude(),
                            ElevationPreferences.getHillshadeAzimuth());
                }

            }

            if (mapFrame != null) {
                mapFrame.mapView.repaint();
            }

        } else {
            removeElevationLayer();
            // Allow re-adding the elevation layer if enabled in the preferences
            // i.e. the layer was (temporarily) removed by the user
            if (ElevationPreferences.getElevationLayerEnabled()) {
                addElevationLayerAction.setEnabled(true);
            }
        }
    }

    /**
     * Enables or disables the {@link GeoTiffEnvelopeLayer}.
     *
     * @param enabled   If {@code true}, the layer is enabled. If {@code false}, the
     *                  layer is disabled.
     * @param setActive If {@code true}, the layer is set as active layer if is
     *                  enabled. If {@code false}, the layer will not be set as the
     *                  active layer if it is enabled.
     */
    public void setGeoTiffEnvelopeLayerEnabled(boolean enabled, boolean setActive) {
        MapFrame mapFrame = MainApplication.getMap();

        if (enabled) {
            addGeoTiffEnvelopeLayerAction.setEnabled(false);
            synchronized (geoTiffEnvelopeLayerLock) {
                if (geoTiffEnvelopeLayer == null) {
                    geoTiffEnvelopeLayer = new GeoTiffEnvelopeLayer(elevationDataProvider);
                    MainApplication.getLayerManager().addLayer(geoTiffEnvelopeLayer);
                    if (setActive) {
                        MainApplication.getLayerManager().setActiveLayer(geoTiffEnvelopeLayer);
                    }
                }
            }

            if (mapFrame != null) {
                mapFrame.mapView.repaint();
            }
        } else {
            removeGeoTiffEnvelopeLayer();
            addGeoTiffEnvelopeLayerAction.setEnabled(true);
        }
    }

    private void addLocalElevationLabel(MapFrame mapFrame) {
        if (localElevationLabel == null) {
            localElevationLabel = new LocalElevationLabel(mapFrame, elevationDataProvider);
        } else {
            localElevationLabel.addToMapFrame(mapFrame);
        }
    }

    public static MapViewElevationDataConsumer getMapViewElevationDataConsumer() {
        return mapViewElevationDataConsumer;
    }

    @Override
    public void layerAdded(LayerAddEvent e) {
        synchronized (elevationLayerLock) {
            if (e.getAddedLayer().equals(elevationLayer)) {
                addElevationLayerAction.setEnabled(false);
            }
        }
        synchronized (geoTiffEnvelopeLayerLock) {
            if (e.getAddedLayer().equals(geoTiffEnvelopeLayer)) {
                addGeoTiffEnvelopeLayerAction.setEnabled(false);
            }
        }

        boolean hasDataLayer = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class).size() > 0;
        if (hasDataLayer) {
            isolationFinderAction.setEnabled(true);
            keyColFinderAction.setEnabled(true);
            setNodeElevationAction.setEnabled(true);
            setPeakProminenceAction.setEnabled(true);
        }
    }

    @Override
    public void layerRemoving(LayerRemoveEvent e) {
        synchronized (elevationLayerLock) {
            if (e.getRemovedLayer().equals(elevationLayer)) {
                elevationLayer = null;
                addElevationLayerAction.setEnabled(true);
            }
        }
        synchronized (geoTiffEnvelopeLayerLock) {
            if (e.getRemovedLayer().equals(geoTiffEnvelopeLayer)) {
                geoTiffEnvelopeLayer = null;
                addGeoTiffEnvelopeLayerAction.setEnabled(true);
            }
        }
        boolean hasDataLayer = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class).size() > 0;
        if (!hasDataLayer) {
            isolationFinderAction.setEnabled(false);
            keyColFinderAction.setEnabled(false);
            setNodeElevationAction.setEnabled(false);
            setPeakProminenceAction.setEnabled(false);
        }
    }

    @Override
    public void layerOrderChanged(LayerOrderChangeEvent e) {
    }
}
