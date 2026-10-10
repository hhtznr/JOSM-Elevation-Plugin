package hhtznr.josm.plugins.elevation.data;

import org.openstreetmap.josm.tools.Logging;

/**
 * This class implements an abstract elevation data consumer which consumes
 * elevation data from an {@link SRTMTileGrid}. <br>
 * This class provides the instance method {@link #dispose()} which shall be
 * called as soon as an instance of the elevation data consumer is no longer
 * needed. It will unregister the consumer from the used tile grid. The tile
 * grid will also be disposed if it is not used by another consumer. This
 * ensures structured cleanup of the SRTM tile cache.
 *
 * @author Harald Hetzner
 */
public abstract class ElevationDataConsumer {

    private final String name;
    private SRTMTileGrid tileGrid;

    private boolean isDisposed = false;

    /**
     * Creates a new elevation data consumer.
     *
     * @param name     The name of this elevation data consumer, which can be used
     *                 for logging.
     * @param tileGrid The SRTM tile grid used by this consumer.
     */
    public ElevationDataConsumer(String name, SRTMTileGrid tileGrid) {
        this.name = name;
        if (!tileGrid.addElevationDataConsumer(this)) {
            throw new IllegalStateException("Cannot register " + name + " with an inactive tile grid.");
        }
        this.tileGrid = tileGrid;
    }

    /**
     * Returns the name of this elevation data consumer.
     *
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the SRTM tile grid used by this elevation data consumer.
     *
     * @return The SRTM tile grid.
     */
    protected synchronized SRTMTileGrid getTileGrid() {
        if (isDisposed || tileGrid == null) {
            throw new IllegalStateException("Elevation data consumer " + name + " is disposed.");
        }
        return tileGrid;
    }

    /**
     * Sets the provided SRTM tile grid as the tile grid of this elevation data
     * consumer. <br>
     * Registers this consumer with the provided tile grid and unregisters it with
     * the tile grid that was previously used.
     *
     * @param newTileGrid The new SRTM tile grid to be used by this elevation data
     *                    consumer.
     */
    protected synchronized void setTileGrid(SRTMTileGrid newTileGrid) {
        if (isDisposed || tileGrid == null) {
            throw new IllegalStateException("Cannot change the tile grid of disposed consumer " + name + ".");
        }
        if (newTileGrid == null) {
            throw new IllegalArgumentException("newTileGrid must not be null.");
        }
        if (newTileGrid == tileGrid) {
            return;
        }

        if (!newTileGrid.addElevationDataConsumer(this)) {
            throw new IllegalStateException("Cannot register " + name + " with an inactive tile grid.");
        }

        SRTMTileGrid oldTileGrid = tileGrid;
        tileGrid = newTileGrid;

        if (!oldTileGrid.removeElevationDataConsumer(this)) {
            Logging.warn("Elevation: " + name + " was not registered with its previous tile grid " + oldTileGrid + ".");
        }

        Logging.info("Elevation: Set new tile grid for " + name + ": Replace " + oldTileGrid + " by " + newTileGrid);
    }

    /**
     * Disposes this elevation data consumer by removing it from the SRTM tile grid.
     * This, in turn, may result in disposal of the SRTM tile grid if it does not
     * have any other consumers left.
     */
    public void dispose() {
        SRTMTileGrid gridToRelease;
        synchronized (this) {
            if (isDisposed) {
                return;
            }

            isDisposed = true;
            gridToRelease = tileGrid;
            tileGrid = null;
        }

        if (gridToRelease != null && !gridToRelease.removeElevationDataConsumer(this)) {
            Logging.warn("Elevation: " + name + " was not registered with its tile grid during disposal.");
        }

        Logging.info("Elevation: " + name + " disposed.");
    }

    /**
     * Returns whether this elevation data consumer is disposed.
     *
     * @return {@code true} if disposed.
     */
    public synchronized boolean isDisposed() {
        return isDisposed;
    }
}
