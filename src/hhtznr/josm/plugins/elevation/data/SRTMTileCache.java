package hhtznr.josm.plugins.elevation.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.ElevationPreferences;

/**
 * In-memory cache for SRTM tiles which can be limited in size. If the cache
 * size is limited, the last recently tiles will be removed if new tiles are
 * added exceeding the cache size.
 *
 * @author Harald Hetzner
 */
public class SRTMTileCache {

    private final HashMap<String, SRTMTile> cache = new HashMap<>();

    /**
     * Current size of the cache in bytes.
     */
    private int cacheSize = 0;

    /**
     * Maximum size of the cache in bytes.
     */
    private int cacheSizeLimit;

    /**
     * Creates a new cache for SRTM tiles.
     *
     * @param cacheSizeLimit The maximum size of the cache in MiB.
     */
    public SRTMTileCache(int cacheSizeLimit) {
        setCacheSizeLimit(cacheSizeLimit);
    }

    /**
     * Sets the maximum cache size to a new value and cleans the cache if required
     * to adopt to the new size limit.
     *
     * @param limit The maximum size of the cache in MiB.
     */
    public synchronized void setCacheSizeLimit(int limit) {
        if (limit == cacheSizeLimit)
            return;
        if (limit < ElevationPreferences.MIN_RAM_CACHE_SIZE_LIMIT)
            limit = ElevationPreferences.MIN_RAM_CACHE_SIZE_LIMIT;
        else if (limit > ElevationPreferences.MAX_RAM_CACHE_SIZE_LIMIT)
            limit = ElevationPreferences.MAX_RAM_CACHE_SIZE_LIMIT;
        cacheSizeLimit = limit * 1024 * 1024;
        Logging.info("Elevation: Maximum size of the SRTM tile cache set to " + getSizeString(cacheSizeLimit));
        if (cacheSize > cacheSizeLimit)
            cleanCache();
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
     * Puts a new SRTM tile with the provided data and attributes into the cache or
     * updates an existing SRTM tile if a tile with the provided ID already exists
     * in the cache.
     *
     * @param id            The ID of the SRTM tile.
     * @param type          The type of the SRTM tile or {@code null} if there is no
     *                      data and therefore the type is not known.
     * @param elevationData The elevation data or {@code null} if the data is not
     *                      available (yet).
     * @param status        The status of the SRTM tile.
     * @return The new SRTM tile that was put into the cache or the existing SRTM
     *         tile that was updated, never {@code null}.
     */
    public synchronized SRTMTile putOrUpdateSRTMTile(String id, SRTMTile.Type type, short[][] elevationData,
            SRTMTile.Status status) {
        if (type == null)
            type = SRTMTile.Type.UNKNOWN;
        SRTMTile srtmTile = cache.get(id);
        if (srtmTile == null) {
            srtmTile = new SRTMTile(id, type, elevationData, status);
            cache.put(id, srtmTile);
            cacheSize += srtmTile.getDataSize();
            Logging.info("Elevation: Cached new SRTM tile " + id + " with type '" + type.toString() + "', status '"
                    + srtmTile.getStatus().toString() + "' and size " + getSizeString(srtmTile.getDataSize())
                    + "; cache size: " + getSizeString(cacheSize));
        } else {
            cacheSize -= srtmTile.getDataSize();
            srtmTile.update(type, elevationData, status);
            cacheSize += srtmTile.getDataSize();
            Logging.info("Elevation: Updated cached SRTM tile " + id + " with type '" + type.toString() + "', status '"
                    + srtmTile.getStatus().toString() + "' and size " + getSizeString(srtmTile.getDataSize())
                    + "; cache size: " + getSizeString(cacheSize));
        }
        if (cacheSize > cacheSizeLimit)
            cleanCache();
        return srtmTile;
    }

    /**
     * Puts a new SRTM tile with the data and attributes from the given SRTM tile
     * into the cache or updates an existing SRTM tile with this data and attributes
     * if a tile with the provided ID already exists in the cache.
     *
     * @param tile An SRTM tile with the data and attributes to be cached.
     * @return The new SRTM tile that was put into the cache or the existing SRTM
     *         tile that was updated, never {@code null}.
     */
    public synchronized SRTMTile putOrUpdateSRTMTile(SRTMTile tile) {
        return putOrUpdateSRTMTile(tile.getID(), tile.getType(), tile.getElevationData(), tile.getStatus());
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
            Logging.info("Elevation: Removed SRTM tile " + srtmTileID + " with status '" + tile.getStatus().toString()
                    + "' and size " + getSizeString(tile.getDataSize()) + " from cache; cache size: "
                    + getSizeString(cacheSize));
        }
        return tile;
    }

    /**
     * Cleans all SRTM tiles with the given {@link SRTMTile.Status} from the cache.
     *
     * @param status The status of the SRTM tiles to clean from the cache.
     */
    public synchronized void cleanAllTilesWithStatus(SRTMTile.Status status) {
        for (String srtmTileID : cache.keySet()) {
            SRTMTile srtmTile = cache.get(srtmTileID);
            if (srtmTile.getStatus() == status)
                cache.remove(srtmTileID);
        }
    }

    private static String getSizeString(int size) {
        return "" + size + " bytes (" + size / 1024 / 1024 + " MiB)";
    }
}
