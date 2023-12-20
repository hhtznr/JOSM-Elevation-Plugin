package hhtznr.josm.plugins.elevation.data;

/**
 * Listener interface to be implemented by classes with instances that should be
 * informed if elevation data from a requested SRTM tile is available.
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
    public void elevationDataAvailable(SRTMTileGrid tileGrid);
}
