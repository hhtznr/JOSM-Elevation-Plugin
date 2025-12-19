package hhtznr.josm.plugins.elevation.gui;

import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.SRTMTileGridView;

/**
 * Class implementing an elevation raster composed of individual
 * latitude-longitude-elevation data points which cover specified bounds.
 *
 * This type of elevation visualization is currently intended for debugging.
 *
 * @author Harald Hetzner
 */
public class ElevationRaster extends AbstractSRTMTileGridPaintable {

    /**
     * Creates a new elevation raster which covers the specified bounds with
     * elevation data points.
     *
     * @param tileGridView The SRTM tile grid view from which elevation data should
     *                     be obtained.
     */
    public ElevationRaster(SRTMTileGridView tileGridView) {
        super(tileGridView);
    }

    /**
     * Returns the height of this raster.
     *
     * @return The height of this raster, i.e. the number of data points in latitude
     *         dimension.
     */
    public int getHeight() {
        if (!tileGridView.areAllTilesCached())
            return 0;
        return tileGridView.getHeight();
    }

    /**
     * Returns the width of this raster.
     *
     * @return The width of this raster, i.e. the number of data points in longitude
     *         dimension.
     */
    public int getWidth() {
        if (!tileGridView.areAllTilesCached())
            return 0;
        return tileGridView.getWidth();
    }

    /**
     * Returns the step between two adjacent raster data points in latitude
     * dimension.
     *
     * @return The distance in latitude in between two adjacent raster data points
     *         in arc degrees.
     */
    public double getLatStep() {
        if (getHeight() < 2 || getWidth() < 1)
            return Double.POSITIVE_INFINITY;
        return tileGridView.getLatLonStep();
    }

    /**
     * Returns the step between two adjacent raster data points in longitude
     * dimension.
     *
     * @return The distance in longitude in between two adjacent raster data points
     *         in arc degrees.
     */
    public double getLonStep() {
        if (getWidth() < 2 || getHeight() < 1)
            return Double.POSITIVE_INFINITY;
        return tileGridView.getLatLonStep();
    }

    /**
     * Returns a latitude-longitude-elevation point of this raster.
     *
     * @param latIndex The index of the data point in latitude dimension.
     * @param lonIndex The index of the data point in longitude dimension.
     * @return The latitude-longitude-elevation point of interest.
     */
    public LatLonEle getLatLonEle(int latIndex, int lonIndex) {
        return tileGridView.getLatLonEle(latIndex, lonIndex);
    }
}
