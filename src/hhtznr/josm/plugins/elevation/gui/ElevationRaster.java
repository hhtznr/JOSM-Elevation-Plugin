package hhtznr.josm.plugins.elevation.gui;

import org.openstreetmap.josm.data.Bounds;

import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.SRTMTileGrid;

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
     * @param tileGrid        The SRTM tile grid from which elevation data should be
     *                        obtained.
     * @param renderingBounds The bounds in latitude-longitude coordinate space
     *                        where this grid should be rendered.
     */
    public ElevationRaster(SRTMTileGrid tileGrid, Bounds renderingBounds) {
        super(tileGrid, renderingBounds, renderingBounds);
    }

    /**
     * Returns the height of this raster.
     *
     * @return The height of this raster, i.e. the number of data points in latitude
     *         dimension.
     */
    public int getHeight() {
        if (!tileGrid.areAllSRTMTilesCached())
            return 0;
        return renderingRasterIndexBounds.latIndexNorth - renderingRasterIndexBounds.latIndexSouth;
    }

    /**
     * Returns the width of this raster.
     *
     * @return The width of this raster, i.e. the number of data points in longitude
     *         dimension.
     */
    public int getWidth() {
        if (!tileGrid.areAllSRTMTilesCached())
            return 0;
        return renderingRasterIndexBounds.lonIndexEast - renderingRasterIndexBounds.lonIndexWest;
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
        return tileGrid.getLatLonStep();
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
        return tileGrid.getLatLonStep();
    }

    /**
     * Returns a latitude-longitude-elevation point of this raster.
     *
     * @param latIndex The index of the data point in latitude dimension.
     * @param lonIndex The index of the data point in longitude dimension.
     * @return The latitude-longitude-elevation point of interest.
     */
    public LatLonEle getLatLonEle(int latIndex, int lonIndex) {
        int gridRasterLatIndex = renderingRasterIndexBounds.latIndexSouth + latIndex;
        int gridRasterLonIndex = renderingRasterIndexBounds.lonIndexWest + lonIndex;
        return tileGrid.getLatLonEle(gridRasterLatIndex, gridRasterLonIndex);
    }
}
