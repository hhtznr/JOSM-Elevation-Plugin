package hhtznr.josm.plugins.elevation.gui.paintables;

import hhtznr.josm.plugins.elevation.data.coordinate.LatLonEle;
import hhtznr.josm.plugins.elevation.data.srtm.SrtmTileGridView;
import hhtznr.josm.plugins.elevation.util.IncrementalNumberedNameCreator;

/**
 * Class implementing an elevation raster composed of individual
 * latitude-longitude-elevation data points which cover specified bounds.
 *
 * This type of elevation visualization is currently intended for debugging.
 *
 * @author Harald Hetzner
 */
public class ElevationRaster extends AbstractElevationGridPaintable {

    private static final IncrementalNumberedNameCreator namer = new IncrementalNumberedNameCreator("Elevation raster");

    /**
     * Creates a new elevation raster which covers the specified bounds with
     * elevation data points.
     *
     * @param tileGridView The SRTM tile grid view from which elevation data should
     *                     be obtained.
     */
    public ElevationRaster(SrtmTileGridView tileGridView) {
        super(namer.nextName(), tileGridView);
    }

    /**
     * Returns the height of this raster.
     *
     * @return The height of this raster, i.e. the number of data points in latitude
     *         dimension.
     */
    public int getHeight() {
        // TODO: Resolve this temporary compatibility workaround
        // This is safe as long as the constructor requires SRTMTileGridView
        SrtmTileGridView srtmTileGridView = (SrtmTileGridView) elevationGridView;
        if (!srtmTileGridView.areAllTilesCached() || srtmTileGridView.getTileGrid().isDisposed()) {
            return 0;
        }
        return elevationGridView.getHeight();
    }

    /**
     * Returns the width of this raster.
     *
     * @return The width of this raster, i.e. the number of data points in longitude
     *         dimension.
     */
    public int getWidth() {
        // TODO: Resolve this temporary compatibility workaround
        // This is safe as long as the constructor requires SRTMTileGridView
        SrtmTileGridView srtmTileGridView = (SrtmTileGridView) elevationGridView;
        if (!srtmTileGridView.areAllTilesCached() || srtmTileGridView.getTileGrid().isDisposed()) {
            return 0;
        }
        return elevationGridView.getWidth();
    }

    /**
     * Returns the step between two adjacent raster data points in latitude
     * dimension.
     *
     * @return The distance in latitude in between two adjacent raster data points
     *         in arc degrees.
     */
    public double getLatStep() {
        if (getHeight() < 2 || getWidth() < 1) {
            return Double.POSITIVE_INFINITY;
        }
        // TODO: Resolve this temporary compatibility workaround
        // This is safe as long as the constructor requires SRTMTileGridView
        SrtmTileGridView srtmTileGridView = (SrtmTileGridView) elevationGridView;
        return srtmTileGridView.getLatLonStep();
    }

    /**
     * Returns the step between two adjacent raster data points in longitude
     * dimension.
     *
     * @return The distance in longitude in between two adjacent raster data points
     *         in arc degrees.
     */
    public double getLonStep() {
        if (getWidth() < 2 || getHeight() < 1) {
            return Double.POSITIVE_INFINITY;
        }
        // TODO: Resolve this temporary compatibility workaround
        // This is safe as long as the constructor requires SRTMTileGridView
        SrtmTileGridView srtmTileGridView = (SrtmTileGridView) elevationGridView;
        return srtmTileGridView.getLatLonStep();
    }

    /**
     * Returns a latitude-longitude-elevation point of this raster.
     *
     * @param latIndex The index of the data point in latitude dimension.
     * @param lonIndex The index of the data point in longitude dimension.
     * @return The latitude-longitude-elevation point of interest.
     */
    public LatLonEle getLatLonEle(int latIndex, int lonIndex) {
        // TODO: Resolve this temporary compatibility workaround
        // This is safe as long as the constructor requires SRTMTileGridView
        SrtmTileGridView srtmTileGridView = (SrtmTileGridView) elevationGridView;
        return srtmTileGridView.getLatLonEle(latIndex, lonIndex);
    }

    /**
     * Returns the step between two adjacent raster points in latitude or longitude
     * direction.
     *
     * @return The step in arc-seconds between two adjacent raster points in
     *         latitude or longitude direction.
     */
    public double getLatLonStep() {
        // TODO: Resolve this temporary compatibility workaround
        // This is safe as long as the constructor requires SRTMTileGridView
        SrtmTileGridView srtmTileGridView = (SrtmTileGridView) elevationGridView;
        return srtmTileGridView.getLatLonStep();
    }
}
