package hhtznr.josm.plugins.elevation.data;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class implements a lease holding one temporary use of an SRTM tile grid.
 * This temporary lease is auto-closeable.
 *
 * @author Harald Hetzner
 */
public final class SRTMTileGridLease implements AutoCloseable {

    private final SRTMTileGrid tileGrid;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Creates a new lease holding an SRTM tile grid.
     *
     * @param tileGrid The (new) SRTM tile grid for which to create the lease.
     */
    SRTMTileGridLease(SRTMTileGrid tileGrid) {
        this.tileGrid = Objects.requireNonNull(tileGrid);
    }

    /**
     * Returns the SRTM tile grid held by this lease.
     *
     * @return The SRTM tile grid.
     */
    public SRTMTileGrid getTileGrid() {
        if (closed.get()) {
            throw new IllegalStateException("SRTM tile grid lease is closed.");
        }
        return tileGrid;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            tileGrid.release();
        }
    }
}
