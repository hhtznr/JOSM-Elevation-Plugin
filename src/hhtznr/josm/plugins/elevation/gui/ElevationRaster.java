package hhtznr.josm.plugins.elevation.gui;

import java.util.List;

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

    private final List<LatLonEle> latLonEleList;

    /**
     * Creates a new elevation raster which covers the specified bounds with
     * elevation data points.
     *
     * @param nominalBounds The nominal bounds in latitude-longitude coordinate
     *                      space.
     * @param actualBounds  The actual bounds in latitude-longitude coordinate
     *                      space.
     * @param latLonEleList The list of latitude-longitude-elevation points forming
     *                      the elevation raster.
     */
    public ElevationRaster(Bounds nominalBounds, Bounds actualBounds, List<LatLonEle> latLonEleList) {
        super(nominalBounds, actualBounds);
        this.latLonEleList = latLonEleList;
    }

    /**
     * Returns the latitude-longitude-elevation points of this raster.
     *
     * @return The list of latitude-longitude-elevation points forming the elevation
     *         raster.
     */
    public List<LatLonEle> getLatLonEleList() {
        return latLonEleList;
    }
}
