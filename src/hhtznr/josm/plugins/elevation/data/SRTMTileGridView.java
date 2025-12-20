package hhtznr.josm.plugins.elevation.data;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.ILatLon;

import hhtznr.josm.plugins.elevation.gui.ContourLines;
import hhtznr.josm.plugins.elevation.gui.ElevationRaster;
import hhtznr.josm.plugins.elevation.gui.HillshadeImageTile;
import hhtznr.josm.plugins.elevation.gui.LowestAndHighestPoints;

/**
 * This class implements a view for convenient index-based access to a subarea
 * of an SRTM tile grid.
 *
 * @author Harald Hetzner
 */
public class SRTMTileGridView {

    private final SRTMTileGrid tileGrid;

    /**
     * The minimum (southernmost) grid raster index in latitude direction.
     */
    private final int gridRasterLatIndexSouth;

    /**
     * The minimum (westernnmost) grid raster index in longitude direction.
     */
    private final int gridRasterLonIndexWest;

    /**
     * Raster height of this view.
     */
    private final int height;

    /**
     * Raster width of this view.
     */
    private final int width;

    /**
     * The coordinate bounds corresponding to this view.
     */
    private final Bounds bounds;

    /**
     * Creates a new SRTM tile grid view.
     *
     * @param tileGrid      The underlying tile grid for which this view is created.
     * @param latIndexSouth The minimum (southernmost) grid raster index in latitude
     *                      direction.
     * @param latIndexNorth The maximum (northernmost) grid raster index in latitude
     *                      direction.
     * @param lonIndexWest  The minimum (westernnmost) grid raster index in
     *                      longitude direction.
     * @param lonIndexEast  The maximum (easternmost) grid raster index in longitude
     *                      direction.
     * @param bounds        The coordinate bounds corresponding to this SRTM tile
     *                      grid view.
     */
    protected SRTMTileGridView(SRTMTileGrid tileGrid, int latIndexSouth, int latIndexNorth, int lonIndexWest,
            int lonIndexEast, Bounds bounds) {
        this.tileGrid = tileGrid;
        gridRasterLatIndexSouth = latIndexSouth;
        gridRasterLonIndexWest = lonIndexWest;
        height = latIndexNorth - latIndexSouth + 1;
        width = lonIndexEast - lonIndexWest + 1;
        this.bounds = bounds;
    }

    /**
     * Returns elevation based on view raster indices.
     *
     * @param latIndex The view raster index in latitude direction.
     * @param lonIndex The view raster index in longitude direction.
     * @return The elevation at the provided view raster indices or
     *         {@link SRTMTile#SRTM_DATA_VOID} if no elevation data is cached yet.
     */
    public short getElevation(int latIndex, int lonIndex) {
        int gridRasterLatIndex = gridRasterLatIndexSouth + latIndex;
        int gridRasterLonIndex = gridRasterLonIndexWest + lonIndex;
        return tileGrid.getElevation(gridRasterLatIndex, gridRasterLonIndex);
    }

    /**
     * Returns the raster height of this SRTM tile grid view.
     *
     * @return The raster height, i.e. the difference between northernmost and
     *         southernmost index {@code +1}.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the raster width of this SRTM tile grid view.
     *
     * @return The raster width, i.e. the difference between easternmost and
     *         westernmost index {@code +1}.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the coordinate bounds corresponding to this SRTM tile grid view.
     *
     * @return The coordinate bounds.
     */
    public Bounds getBounds() {
        return bounds;
    }

    /**
     * Returns the coordinates, which correspond to the point at the given raster
     * indices, and its elevation value.
     *
     * @param latIndex The index of the point in latitude direction within this tile
     *                 grid view's elevation raster spanning across all included
     *                 SRTM tiles.
     * @param lonIndex The index of the point in longitude direction within this
     *                 tile grid view's elevation raster spanning across all
     *                 included SRTM tiles.
     * @return The coordinates of the raster point and its elevation.
     */
    public LatLonEle getLatLonEle(int latIndex, int lonIndex) {
        int gridRasterLatIndex = gridRasterLatIndexSouth + latIndex;
        int gridRasterLonIndex = gridRasterLonIndexWest + lonIndex;
        return tileGrid.getLatLonEle(gridRasterLatIndex, gridRasterLonIndex);
    }

    /**
     * Returns the indices of the grid raster point which is closest to the given
     * coordinate.
     *
     * @param latLon The coordinate for which to determine the indices of the
     *               closest point in this grid view's raster.
     * @return The indices of the grid view raster point which is closest to the
     *         given point.
     */
    public int[] getClosestViewRasterIndices(ILatLon latLon) {
        if (!bounds.contains(latLon))
            throw new IllegalArgumentException("Given coordinate " + latLon.toString() + " is not within the bounds "
                    + bounds.toString() + " of this tile grid view");
        int[] tileGridIndices = tileGrid.getClosestGridRasterIndices(latLon);
        int latIndex = tileGridIndices[0] - gridRasterLatIndexSouth;
        int lonIndex = tileGridIndices[1] - gridRasterLonIndexWest;
        return new int[] { latIndex, lonIndex };
    }

    /**
     * Returns the step between two adjacent raster points in latitude or longitude
     * direction.
     *
     * @return The step in arc-seconds between two adjacent raster points in
     *         latitude or longitude direction.
     */
    public double getLatLonStep() {
        return tileGrid.getLatLonStep();
    }

    /**
     * Returns an array of isovalues within the bounds of this view.
     *
     * @param isostep              Step between two adjacent isolines.
     * @param lowerCutoffElevation The elevation value below which isovalues will
     *                             not be returned.
     * @param upperCutoffElevation The elevation value above which isovalues will
     *                             not be returned.
     * @return An array of the isovaluesconsidering the given isostep and the given
     *         cutoff values.
     */
    public short[] getIsovalues(int isostep, int lowerCutoffElevation, int upperCutoffElevation) {
        short minEle = Short.MAX_VALUE;
        short maxEle = SRTMTile.SRTM_DATA_VOID;
        for (int latIndex = 0; latIndex < height; latIndex++) {
            for (int lonIndex = 0; lonIndex < width; lonIndex++) {
                short ele = getElevation(latIndex, lonIndex);
                // Ignore data voids in the assessment of minimum elevation
                if (ele == SRTMTile.SRTM_DATA_VOID)
                    continue;
                minEle = (short) Math.min(minEle, ele);
                maxEle = (short) Math.max(maxEle, ele);
            }
        }
        // If the area consists of data voids only,
        // minEle will still have its initial value
        if (minEle == Short.MAX_VALUE)
            minEle = SRTMTile.SRTM_DATA_VOID;

        // Apply the lower cutoff elevation value, if it is greater than the minimum
        // elevation within the grid
        minEle = (short) Math.max(minEle, lowerCutoffElevation);
        // Apply the upper cutoff elevation value, if it is smaller than the maximum
        // elevation within the grid
        maxEle = (short) Math.min(maxEle, upperCutoffElevation);

        // Determine the list of isovalues, i.e. the elevation levels for which contour
        // lines should be computed
        short minIsovalue;
        short maxIsovalue;
        if (minEle % isostep == 0)
            minIsovalue = minEle;
        else
            minIsovalue = (short) ((minEle / isostep) * isostep);
        if (maxEle % isostep == 0)
            maxIsovalue = maxEle;
        else
            maxIsovalue = (short) ((maxEle / isostep) * isostep);
        if (maxIsovalue < minIsovalue)
            return new short[] {};
        int nSteps = (maxIsovalue - minIsovalue) / isostep + 1;
        short[] isovalues = new short[nSteps];
        for (int i = 0; i < nSteps; i++)
            isovalues[i] = (short) (minIsovalue + i * isostep);

        return isovalues;
    }

    /**
     * Returns a list of isoline segments defining elevation contour lines within
     * the bounds of this SRTM tile grid view. The segments do not have a useful
     * order. This method will slightly adjust the bounds to the closest coordinates
     * of the elevation raster.
     *
     * @param isostep              Step between two adjacent elevation contour
     *                             lines.
     * @param lowerCutoffElevation The elevation value below which contour lines
     *                             will not be returned.
     * @param upperCutoffElevation The elevation value above which contour lines
     *                             will not be returned.
     * @return A list of isoline segments defining elevation contour lines within
     *         the bounds or {@code null} if not all of the SRTM tiles have the same
     *         type (i.e. different raster dimensions) or if at least one of the
     *         tiles is not valid (i.e. the data was not loaded yet or is not
     *         available at all).
     */
    public ContourLines getContourLines(int isostep, int lowerCutoffElevation, int upperCutoffElevation) {
        if (!areAllTilesCached())
            return null;
        return new ContourLines(this, isostep, lowerCutoffElevation, upperCutoffElevation);
    }

    /**
     * Returns an object with two lists providing the coordinate points from the
     * elevation raster, which have the lowest and highest elevation within the
     * bounds of this SRTM tile grid view, respectively.
     *
     * @return An object with two lists providing the coordinate points with the
     *         lowest and highest elevation within the given bounds. {@code null} is
     *         returned if not all SRTM tiles are cached yet.
     */
    public LowestAndHighestPoints getLowestAndHighestPoints() {
        if (!areAllTilesCached())
            return null;
        return new LowestAndHighestPoints(this);
    }

    /**
     * Creates a buffered image with the computed hillshade ARGB values for the
     * elevation values within the bounds this SRTM tile grid view.
     *
     * @param altitudeDeg   The altitude is the angle of the illumination source
     *                      above the horizon. The units are in degrees, from 0 (on
     *                      the horizon) to 90 (overhead).
     * @param azimuthDeg    The azimuth is the angular direction of the sun,
     *                      measured from north in clockwise degrees from 0 to 360.
     * @param withPerimeter If {@code} true, the a first and last row as well as the
     *                      a first and last column without computed values will be
     *                      added such that the size of the 2D array corresponds to
     *                      that of the input data. If {@code false}, these rows and
     *                      columns will be omitted.
     * @return An image with the computed hillshade values or {@code null} if this
     *         SRTM tile grid cannot deliver elevation values or there are less than
     *         3 elevation values in one of the two dimensions.
     */
    public HillshadeImageTile getHillshadeImageTile(double altitudeDeg, double azimuthDeg, boolean withPerimeter) {
        if (!areAllTilesCached())
            return null;
        return new HillshadeImageTile(this, altitudeDeg, azimuthDeg, withPerimeter);
    }

    /**
     * Returns all raster coordinates and the associated elevation values within the
     * bounds of this SRTM tile grid view.
     *
     * @return All raster coordinates and the associated elevation values within the
     *         bounds or {@code null} if not all of the SRTM tiles have the same
     *         type (i.e. different raster dimensions) or if at least one of the
     *         tiles is not valid (i.e. the data was not loaded yet or is not
     *         available at all).
     */
    public ElevationRaster getElevationRaster() {
        if (!areAllTilesCached())
            return null;
        return new ElevationRaster(this);
    }

    /**
     * Blocks the calling thread until all SRTM tiles for the underlying grid have
     * been cached. See {@link SRTMTileGrid#waitForTilesCached}.
     *
     * @throws InterruptedException if the waiting thread is interrupted while
     *                              waiting
     */
    public void waitForTilesCached() throws InterruptedException {
        tileGrid.waitForTilesCached();
    }

    /**
     * Returns whether all SRTM tiles required to form the underlying grid are
     * available and in memory. See {@link SRTMTileGrid#areAllTilesCached}.
     *
     *
     * @return {@code true} if all SRTM tiles required to form the underlying grid
     *         are available and in memory.
     */
    public boolean areAllTilesCached() {
        return tileGrid.areAllTilesCached();
    }
}
