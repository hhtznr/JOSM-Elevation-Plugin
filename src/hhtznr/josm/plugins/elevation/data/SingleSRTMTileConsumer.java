package hhtznr.josm.plugins.elevation.data;

import java.util.concurrent.CancellationException;
import org.openstreetmap.josm.data.coor.ILatLon;

import hhtznr.josm.plugins.elevation.util.IncrementalNumberedNameCreator;

/**
 * This class implements an SRTM tile consumer that uses only a single SRTM
 * tile. It can be used in cases where no {@link SRTMTileGrid} is need. For
 * example to obtain the elevation at a specified coordinate location.
 *
 * @author Harald Hetzner
 */
public class SingleSRTMTileConsumer extends SRTMTileConsumer {

    private static final IncrementalNumberedNameCreator namer = new IncrementalNumberedNameCreator(
            "Single SRTM tile consumer");

    /**
     * Creates a new single SRMT tile consumer.
     *
     * @param elevationDataProvider The elevation data provider that will provide
     *                              the SRTM tile.
     * @param latLon                The coordinate location for which the
     *                              corresponding SRTM tile shall be obtained.
     * @throws CancellationException if loading of the SRTM tile was canceled.
     */
    public SingleSRTMTileConsumer(ElevationDataProvider elevationDataProvider, ILatLon latLon)
            throws CancellationException {
        super(namer.nextName(), elevationDataProvider);
        String srtmTileID = SRTMTile.getTileID(latLon);
        // May throw CancellationException
        SRTMTileCacheEntry entry = elevationDataProvider.getSRTMTileCacheEntryFor(srtmTileID, this);
        synchronized (entry) {
            addCacheEntry(entry);
        }

    }

    /**
     * Returns the SRTM tile cache entry that holds the SRTM tile of this single
     * SRTM tile consumer.
     *
     * @return The SRTM tile cache entry.
     */
    public SRTMTileCacheEntry getCacheEntry() {
        return getCacheEntryList().get(0);
    }
}
