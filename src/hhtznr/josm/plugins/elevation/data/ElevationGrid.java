package hhtznr.josm.plugins.elevation.data;

import org.openstreetmap.josm.data.Bounds;

/**
 * Interface for different types of elevation grids, where different types of
 * elevation data and therefore different approaches to evaluate it are used.
 * Implementing the interface ensures compatibility of different grid types with
 * common methods.
 *
 * @author Harald Hetzner
 */
public interface ElevationGrid {

    /**
     * Returns the width of this grid view. The width is the number of grid raster
     * points in longitude direction.
     *
     * @return The width of this grid view in grid raster points.
     */
    public int getWidth();

    /**
     * Returns the height of this grid view. The height is the number of grid raster
     * points in latitude direction.
     *
     * @return The height of this grid view in grid raster points.
     */
    public int getHeight();

    /**
     * Returns the geographic bounds of the elevation grid in JOSM's representation
     * {@code Bounds}.
     *
     * @return The geographic bounds of the elevation grid.
     */
    public Bounds getBounds();
}
