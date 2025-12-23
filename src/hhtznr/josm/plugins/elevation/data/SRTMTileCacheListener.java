package hhtznr.josm.plugins.elevation.data;

/**
 * Listener interface to be implemented by classes with instances that should be
 * informed if elevation data from an SRTM tile is available.
 *
 * @author Harald Hetzner
 */
public interface SRTMTileCacheListener {

    /**
     * Informs the implementing class that elevation data from an SRTM tile is
     * available.
     *
     * @param tile   The available SRTM tile.
     * @param status The final cache status of the tile.
     */
    public void srtmTileCached(SRTMTile tile, SRTMTileCacheEntry.Status status);
}
