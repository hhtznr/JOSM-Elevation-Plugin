package hhtznr.josm.plugins.elevation.gui;

import org.openstreetmap.josm.data.Bounds;

import hhtznr.josm.plugins.elevation.data.LatLonEle;

/**
 * Class implementing an elevation raster composed of individual
 * latitude-longitude-elevation data points which cover specified bounds.
 *
 * This type of elevation visualization is currently intended for debugging.
 *
 * @author Harald Hetzner
 */
public class ElevationRaster extends AbstractSRTMTileGridPaintable {

    private final LatLonEle[][] latLonEleRaster;

    /**
     * Creates a new elevation raster which covers the specified bounds with
     * elevation data points.
     *
     * @param nominalBounds   The nominal bounds in latitude-longitude coordinate
     *                        space.
     * @param actualBounds    The actual bounds in latitude-longitude coordinate
     *                        space.
     * @param latLonEleRaster The two-dimensional array of
     *                        latitude-longitude-elevation points forming the
     *                        elevation raster. The array of arrays has to be in row
     *                        major order.
     */
    public ElevationRaster(Bounds nominalBounds, Bounds actualBounds, LatLonEle[][] latLonEleRaster) {
        super(nominalBounds, actualBounds);
        this.latLonEleRaster = latLonEleRaster;
    }

    /**
     * Returns the height of this raster.
     *
     * @return The height of this raster, i.e. the number of data points in latitude
     *         dimension.
     */
    public int getHeight() {
        if (latLonEleRaster == null)
            return 0;
        return latLonEleRaster.length;
    }

    /**
     * Returns the width of this raster.
     *
     * @return The width of this raster, i.e. the number of data points in longitude
     *         dimension.
     */
    public int getWidth() {
        if (latLonEleRaster == null || latLonEleRaster.length == 0)
            return 0;
        return latLonEleRaster[0].length;
    }

    /**
     * Returns a latitude-longitude-elevation point of this raster.
     *
     * @param latIndex The index of the data point in latitude dimension.
     * @param lonIndex The index of the data point in longitude dimension.
     * @return The latitude-longitude-elevation point of interest.
     */
    public LatLonEle getLatLonEle(int latIndex, int lonIndex) {
        return latLonEleRaster[latIndex][lonIndex];
    }
}
