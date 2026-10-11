package hhtznr.josm.plugins.elevation.data;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;

/**
 * Interface for views on different types of elevation grids, where the
 * underlying grids use different types of elevation data and therefore
 * different approaches to evaluate it. Implementing the interface ensures
 * compatibility of different grid types with common methods like determination
 * of contour lines using the marching squares algorithm.
 *
 * @author Harald Hetzner
 */
public interface ElevationGridView {

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
     * Returns the elevation value at the provided grid indices.
     *
     * @param latIndex The index in latitude direction of the grid.
     * @param lonIndex The index in longitude direction of the grid.
     * @return The elevation value.
     */
    public double getElevation(int latIndex, int lonIndex);

    /**
     * Returns whether the elevation value at the provided grid indices is not a
     * valid data value.
     *
     * @param latIndex The index in latitude direction of the grid.
     * @param lonIndex The index in longitude direction of the grid.
     * @return {@code true} if the elevation value is not a valid data value,
     *         {@code false} otherwise.
     */
    public boolean isNoDataValue(int latIndex, int lonIndex);

    /**
     * Returns the latitude for the provided index of the grid view in latitude
     * direction.
     *
     * @param latIndex The grid view index in latitude direction.
     * @return The latitude corresponding to the provided grid view index.
     */
    // public double toLat(int latIndex);

    /**
     * Returns the longitude for the provided index of the grid view in longitude
     * direction.
     *
     * @param lonIndex The grid view index in longitude direction.
     * @return The longitude corresponding to the provided grid view index.
     */
    // public double toLon(int lonIndex);

    /**
     * Returns the coordinate for the provided indices of the grid view in latitude
     * and longitude direction.
     *
     * @param latIndex The grid view index in latitude direction.
     * @param lonIndex The grid view index in longitude direction.
     * @return The coordinate corresponding to the provided grid view indices.
     */
    public LatLon toLatLon(int latIndex, int lonIndex);

    /**
     * Returns the coordinate bounds of this view on an elevation grid.
     *
     * @return The coordinate bounds of this elevation grid view.
     */
    public Bounds getBounds();

    /**
     * Returns isovalues for this elevation grid view based on the provided isostep.
     * Isovalues returned shall be multiples of the isostep.
     *
     * @param isostep              The difference between an isobalue and the next
     *                             greater isovalue.
     * @param lowerCutoffElevation Elevation values below this value shall not be
     *                             considered when determining the isovalues.
     * @param upperCutoffElevation Elevation values above this value shall not be
     *                             considered then determining the isovalues.
     * @return An array holding the isovalues.
     */
    public short[] getIsovalues(int isostep, int lowerCutoffElevation, int upperCutoffElevation);
}
