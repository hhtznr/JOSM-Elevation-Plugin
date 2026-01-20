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
        tileGrid.addElevationDataConsumer(this);
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
        synchronized (newTileGrid) {
            SRTMTileGrid oldTileGrid = this.tileGrid;
            synchronized (oldTileGrid) {
                if (newTileGrid.equals(oldTileGrid))
                    return;
                newTileGrid.addElevationDataConsumer(this);
                Logging.info("Elevation: Set new tile grid for " + name + ": Replace " + oldTileGrid.toString() + " by "
                        + newTileGrid.toString());
                this.tileGrid = newTileGrid;
                oldTileGrid.removeElevationDataConsumer(this);
            }
        }
    }

    /**
     * Disposes this elevation data consumer by removing it from the SRTM tile grid.
     * This, in turn, may result in disposal of the SRTM tile grid if it does not
     * have any other consumers left.
     */
    public synchronized void dispose() {
        tileGrid.removeElevationDataConsumer(this);
        isDisposed = true;
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
