package hhtznr.josm.plugins.elevation.data;

/**
 * Listener interface to be implemented by classes with instances that should be
 * informed if elevation data from a requested SRTM tile is available.
 *
 * @author Harald Hetzner
 */
public interface SRTMTileProviderListener {

    /**
     * Informs the implementing class that elevation data from a requested SRTM tile
     * is available.
     *
     * @param tile The SRTM tile which contains the requested elevation data.
     */
    public void elevationDataAvailable(SRTMTile tile);
}
