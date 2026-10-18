package hhtznr.josm.plugins.elevation.data;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;

import hhtznr.josm.plugins.elevation.data.coordinate.LatLonEle;

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

    /**
     * Returns the elevation value at the specified grid raster point, given by its
     * latitude and longitude indices.
     *
     * @param latIndex The latitude index of the grid raster point.
     * @param lonIndex The longitude index of the grid raster point.
     * @return The elevation value at the specified grid raster point.
     */
    public double getElevation(int latIndex, int lonIndex);

    /**
     * Checks if the elevation value at the specified grid raster point is a no-data
     * value.
     *
     * @param latIndex The latitude index of the grid raster point.
     * @param lonIndex The longitude index of the grid raster point.
     * @return true if the elevation value is a no-data value, false otherwise.
     */
    public boolean isNoDataValue(int latIndex, int lonIndex);

    /**
     * Checks if the given elevation value is a no-data value.
     *
     * @param ele The elevation value to check.
     * @return true if the elevation value is a no-data value, false otherwise.
     */
    public boolean isNoDataValue(double ele);

    /**
     * Converts the specified grid raster point to a geographic coordinate.
     *
     * @param latIndex The latitude index of the grid raster point.
     * @param lonIndex The longitude index of the grid raster point.
     * @return The geographic coordinate at the specified grid raster point.
     */
    public LatLon toLatLon(int latIndex, int lonIndex);

    /**
     * Converts the specified grid raster point to a geographic coordinate with
     * elevation.
     *
     * @param latIndex The latitude index of the grid raster point.
     * @param lonIndex The longitude index of the grid raster point.
     * @return The geographic coordinate with elevation at the specified grid raster
     *         point.
     */
    public LatLonEle toLatLonEle(int latIndex, int lonIndex);

    /**
     * Returns a view covering the raster cells that may intersect the provided
     * bounds.
     *
     * @param bounds The bounds, e.g. the bounds of the map view.
     * @return A view covering the raster cells that may intersect the map bounds.
     * @throws ElevationGridException if an error occurs while creating the view.
     */
    public ElevationGridView getView(Bounds bounds) throws ElevationGridException;
}
