package hhtznr.josm.plugins.elevation.gui;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent.ZoomChangeListener;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.data.ElevationDataConsumer;
import hhtznr.josm.plugins.elevation.data.ElevationDataProvider;
import hhtznr.josm.plugins.elevation.data.SRTMTile;
import hhtznr.josm.plugins.elevation.util.IncrementalNumberedNameCreator;

/**
 * This class implements an elevation data consumer that ensures that SRTM tiles
 * for the current map view will be cached.
 *
 * @author Harald Hetzner
 */
public class MapViewElevationDataConsumer extends ElevationDataConsumer implements ZoomChangeListener {

    private static final IncrementalNumberedNameCreator namer = new IncrementalNumberedNameCreator(
            "Map view elevation data consumer");

    private double switchOffMapDimension;

    private final ElevationDataProvider elevationDataProvider;

    private MapFrame mapFrame = null;

    /**
     * The smallest value of the greater of both map dimensions (latitude and
     * longitude), for which, if exceeded, reading of elevation data is switched
     * off.
     */
    public static final double MIN_SWITCH_OFF_MAP_DIMENSION = SRTMTile.SRTM_TILE_ARC_DEGREES;

    /**
     * Creates a new map view elevation data consumer.
     *
     * @param mapFrame              The map view for which this elevation data
     *                              consumer is created.
     * @param elevationDataProvider The elevation data provider used to obtain
     *                              elevation data.
     * @param switchOffMapDimension The maximum size of the map in the greater of
     *                              both dimensions, for which, if exceeded, reading
     *                              of elevation data shall be switched off to avoid
     *                              high CPU and memory usage.
     * @throws AsyncOperationException if the {@code CompletableFuture} was
     *                                 completed exceptionally or canceled or the
     *                                 thread was interrupted.
     */
    public MapViewElevationDataConsumer(MapFrame mapFrame, ElevationDataProvider elevationDataProvider,
            double switchOffMapDimension) throws AsyncOperationException {
        super(namer.nextName(),
                elevationDataProvider.getGridMatching(mapFrame.mapView.getLatLonBounds(mapFrame.mapView.getBounds())));
        this.elevationDataProvider = elevationDataProvider;
        this.switchOffMapDimension = switchOffMapDimension;
        addToMapFrame(mapFrame);
        Logging.info("Elevation: Created map view elevation data consumer for map view " + mapFrame.toString());
    }

    /**
     * Adds this local elevation label to the status line of a map frame.
     *
     * @param mapFrame The JOSM map frame where this local elevation label should be
     *                 added to the status line.
     */
    public void addToMapFrame(MapFrame mapFrame) {
        this.mapFrame = mapFrame;
        if (mapFrame != null) {
            MapView.addZoomChangeListener(this);
            zoomChanged();
        }
    }

    /**
     * Returns the maximum size of the displayed map (latitude or longitude) for
     * which, if exceeded, reading of elevation data is switched off to avoid high
     * CPU and memory usage.
     *
     * @return The maximum size of the map in the greater of both dimensions, for
     *         which, if exceeded, reading of elevation data is switched off.
     */
    public double getSwitchOffMapDimension() {
        return switchOffMapDimension;
    }

    /**
     * Sets the maximum size of the displayed map (latitude or longitude) for which,
     * if exceeded, reading of elevation data is switched off to avoid high CPU and
     * memory usage. <br>
     * Sets the provided value only if it is greater or equal to
     * {@link #MIN_SWITCH_OFF_MAP_DIMENSION}.
     *
     * @param arcSeconds The maximum size of the map in the greater of both
     *                   dimensions, for which, if exceeded, reading of elevation
     *                   data shall be switched off.
     */
    public void setSwitchOffDimension(double arcSeconds) {
        switchOffMapDimension = Math.max(arcSeconds, MIN_SWITCH_OFF_MAP_DIMENSION);
    }

    @Override
    public synchronized void dispose() {
        MapView.removeZoomChangeListener(this);
        super.dispose();
    }

    @Override
    public void zoomChanged() {
        final Bounds bounds = mapFrame.mapView.getLatLonBounds(mapFrame.mapView.getBounds());
        boolean elevationZoomLevelEnabled = bounds.getHeight() <= switchOffMapDimension
                && bounds.getWidth() <= switchOffMapDimension;

        if (elevationZoomLevelEnabled && !getTileGrid().matchesTileGridBounds(bounds)) {
            try {
                setTileGrid(elevationDataProvider.getGridMatching(bounds));
            } catch (AsyncOperationException e) {
                return;
            }
        }
    }
}
