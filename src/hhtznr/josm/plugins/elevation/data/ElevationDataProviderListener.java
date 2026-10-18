package hhtznr.josm.plugins.elevation.data;

import hhtznr.josm.plugins.elevation.data.srtm.SrtmTile;
import hhtznr.josm.plugins.elevation.data.srtm.SrtmTileGrid;

/**
 * Listener interface to be implemented by classes with instances that should be
 * informed if elevation data from an SRTM tile grid is available.
 *
 * @author Harald Hetzner
 */
public interface ElevationDataProviderListener {

    /**
     * Informs the implementing class that elevation data from a SRTM tile grid is
     * available.
     *
     * @param tileGrid The SRTM tile grid with available elevation data.
     */
    public void elevationDataAvailable(SrtmTileGrid tileGrid);

    /**
     * Informs the implementing class that the type of SRTM data (SRTM1 or SRTM3)
     * has changed.
     *
     * @param oldType The old type of SRTM data.
     * @param newType The new type of SRTM data.
     */
    public void srtmTileTypeChanged(SrtmTile.Type oldType, SrtmTile.Type newType);

    /**
     * Informs the implementing class that the GeoTIFF envelope index has been
     * updated.
     */
    public void geoTiffIndexUpdated();
}
