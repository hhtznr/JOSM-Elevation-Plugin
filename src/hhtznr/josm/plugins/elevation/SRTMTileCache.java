package hhtznr.josm.plugins.elevation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.openstreetmap.josm.tools.Logging;

/**
 * In-memory cache for SRTM tiles which can be limited in size. If the cache
 * size is limited, the last recently tiles will be removed if new tiles are
 * added exceeding the cache size.
 *
 * @author Harald Hetzner
 */
public class SRTMTileCache {

    private final HashMap<String, SRTMTile> cache = new HashMap<>();
    private int cacheSize = 0;
    private int cacheSizeLimit;

    /**
     * Creates a new cache for SRTM tiles.
     *
     * @param cacheSizeLimit The maximum size of the cache in bytes. A value
     *                       {@code <= 0} defines an unlimited cache size.
     */
    public SRTMTileCache(int cacheSizeLimit) {
        if (cacheSizeLimit <= ElevationPreferences.DISABLED_RAM_CACHE_SIZE_LIMIT)
            this.cacheSizeLimit = ElevationPreferences.DISABLED_RAM_CACHE_SIZE_LIMIT;
        else
            this.cacheSizeLimit = cacheSizeLimit;
    }

    /**
     * Return the SRTM tile identified by the provided tile ID.
     *
     * @param srtmTileID The ID of the SRTM tile.
     * @return The SRTM tile with the provided ID or {@code null} if no tile with
     *         that ID is in the cache.
     */
    public synchronized SRTMTile get(String srtmTileID) {
        return cache.get(srtmTileID);
    }

    /**
     * Puts the SRTM tile into the cache. In case that the cache already holds an
     * SRTM tile with the same tile ID, it is removed and returned.
     *
     * @param srtmTile The SRTM tile to put into the cache.
     * @return Removed SRTM tile if the cache was already holding an SRTM tile with
     *         the same ID or {@code null} if the cache did not hold an SRTM tile
     *         with the same ID.
     */
    public synchronized SRTMTile put(SRTMTile srtmTile) {
        SRTMTile previousTile = cache.put(srtmTile.getID(), srtmTile);
        if (previousTile != null)
            cacheSize -= previousTile.getDataSize();
        if (srtmTile != null)
            cacheSize += srtmTile.getDataSize();
        Logging.info("Elevation: Cached SRTM tile " + srtmTile.getID() + " with size "
                + getSizeString(srtmTile.getDataSize()) + " -> cache size: " + getSizeString(cacheSize));
        if (cacheSizeLimit != ElevationPreferences.DISABLED_RAM_CACHE_SIZE_LIMIT && cacheSize > cacheSizeLimit)
            cleanCache();
        return previousTile;
    }

    /**
     * Removes least recently used SRTM tiles actually holding elevation data from
     * the cache ensuring that the cache size limit is not exceeded.
     */
    private synchronized void cleanCache() {
        ArrayList<SRTMTile> allTiles = new ArrayList<>(cache.values());
        Collections.sort(allTiles, (SRTMTile tile1, SRTMTile tile2) -> {
            return Long.compare(tile1.getAccessTime(), tile2.getAccessTime());
        });
        for (SRTMTile tile : allTiles) {
            if (cacheSize <= cacheSizeLimit)
                break;
            if (tile != null && tile.getDataSize() > 0)
                remove(tile.getID());
        }
    }

    /**
     * Removes the SRTM tile with the provided ID from the cache and returns it.
     *
     * @param srtmTileID The ID of the SRTM tile to remove.
     * @return The removed SRTM tile or {@code null} if the cache did not hold an
     *         SRTM tile with the provided ID.
     */
    public synchronized SRTMTile remove(String srtmTileID) {
        SRTMTile tile = cache.remove(srtmTileID);
        if (tile != null) {
            cacheSize -= tile.getDataSize();
            Logging.info("Elevation: Removed SRTM tile " + srtmTileID + " with size "
                    + getSizeString(tile.getDataSize()) + " from cache -> cache size: " + getSizeString(cacheSize));
        }
        return tile;
    }

    /**
     * Cleans all SRTM tiles with the status {@link SRTMTile.Status#MISSING
     * SRTMTile.Status.MISSING} from the cache.
     */
    public synchronized void cleanAllMissingTiles() {
        for (String srtmTileID : cache.keySet()) {
            SRTMTile srtmTile = cache.get(srtmTileID);
            if (srtmTile.getStatus() == SRTMTile.Status.MISSING)
                cache.remove(srtmTileID);
        }
    }

    private static String getSizeString(int size) {
        return "" + size + " bytes (" + size / 1024 / 1024 + " MiB)";
    }
}
