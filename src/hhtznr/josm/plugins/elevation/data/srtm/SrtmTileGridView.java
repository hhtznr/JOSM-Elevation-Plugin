package hhtznr.josm.plugins.elevation.data.srtm;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;

import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.data.ElevationGrid;
import hhtznr.josm.plugins.elevation.data.ElevationGridView;
import hhtznr.josm.plugins.elevation.data.coordinate.LatLonEle;
import hhtznr.josm.plugins.elevation.gui.paintables.ContourLines;
import hhtznr.josm.plugins.elevation.gui.paintables.ElevationRaster;
import hhtznr.josm.plugins.elevation.gui.paintables.HillshadeImageTile;
import hhtznr.josm.plugins.elevation.gui.paintables.LowestAndHighestPoints;

/**
 * This class implements a view for convenient index-based access to a subarea
 * of an SRTM tile grid.
 *
 * @author Harald Hetzner
 */
public class SrtmTileGridView implements ElevationGridView {

    private final SrtmTileGrid tileGrid;

    /**
     * The minimum (southernmost) grid raster index in latitude direction.
     */
    private final int gridRasterLatIndexSouth;

    /**
     * The minimum (westernnmost) grid raster index in longitude direction.
     */
    private final int gridRasterLonIndexWest;

    /**
     * Raster height of this view (height = latitude direction).
     */
    private final int height;

    /**
     * Raster width of this view (width = longitude direction).
     */
    private final int width;

    /**
     * The coordinate bounds corresponding to this view.
     */
    private final Bounds bounds;

    /**
     * The lowest and highest points within this view.
     */
    private LowestAndHighestPoints lowestAndHighestPoints = null;

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
    protected SrtmTileGridView(SrtmTileGrid tileGrid, int latIndexSouth, int latIndexNorth, int lonIndexWest,
            int lonIndexEast, Bounds bounds) {
        this.tileGrid = tileGrid;
        gridRasterLatIndexSouth = latIndexSouth;
        gridRasterLonIndexWest = lonIndexWest;
        height = latIndexNorth - latIndexSouth + 1;
        width = lonIndexEast - lonIndexWest + 1;
        this.bounds = bounds;
    }

    /**
     * Returns elevation based on view raster indices as {@code short}.
     *
     * @param latIndex The view raster index in latitude direction.
     * @param lonIndex The view raster index in longitude direction.
     * @return The elevation at the provided view raster indices or
     *         {@link SrtmTile#SRTM_DATA_VOID} if no elevation data is cached yet.
     */
    public short getElevationAsShort(int latIndex, int lonIndex) {
        int gridRasterLatIndex = gridRasterLatIndexSouth + latIndex;
        int gridRasterLonIndex = gridRasterLonIndexWest + lonIndex;
        return tileGrid.getElevationAsShort(gridRasterLatIndex, gridRasterLonIndex);
    }

    /**
     * Returns the raster height of this SRTM tile grid view.
     *
     * @return The raster height, i.e. the difference between northernmost and
     *         southernmost index {@code +1}.
     */
    @Override
    public int getHeight() {
        return height;
    }

    /**
     * Returns the raster width of this SRTM tile grid view.
     *
     * @return The raster width, i.e. the difference between easternmost and
     *         westernmost index {@code +1}.
     */
    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public double getElevation(int latIndex, int lonIndex) {
        return getElevationAsShort(latIndex, lonIndex);
    }

    @Override
    public boolean isNoDataValue(int latIndex, int lonIndex) {
        return getElevationAsShort(latIndex, lonIndex) == SrtmTile.SRTM_DATA_VOID;
    }

    @Override
    public boolean isNoDataValue(double ele) {
        // SRTM_DATA_VOID is -32768. This value is properly represented as double.
        return ele == SrtmTile.SRTM_DATA_VOID || Double.isNaN(ele);
    }

    @Override
    public LatLon toLatLon(int latIndex, int lonIndex) {
        int gridRasterLatIndex = gridRasterLatIndexSouth + latIndex;
        int gridRasterLonIndex = gridRasterLonIndexWest + lonIndex;
        return tileGrid.toLatLon(gridRasterLatIndex, gridRasterLonIndex);
    }

    @Override
    public LatLonEle toLatLonEle(int latIndex, int lonIndex) {
        int gridRasterLatIndex = gridRasterLatIndexSouth + latIndex;
        int gridRasterLonIndex = gridRasterLonIndexWest + lonIndex;
        return tileGrid.toLatLonEle(gridRasterLatIndex, gridRasterLonIndex);
    }

    /**
     * Returns the coordinate bounds corresponding to this SRTM tile grid view.
     *
     * @return The coordinate bounds.
     */
    @Override
    public Bounds getBounds() {
        return bounds;
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
        if (!bounds.contains(latLon)) {
            throw new IllegalArgumentException("Given coordinate " + latLon.toString() + " is not within the bounds "
                    + bounds.toString() + " of this tile grid view");
        }
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
     * Returns a list of isoline segments defining elevation contour lines within
     * the bounds of this SRTM tile grid view. The segments do not have a useful
     * order. This method will slightly adjust the bounds to the closest coordinates
     * of the elevation raster.
     *
     * @param isostep              Step between two adjacent elevation contour
     *                             lines.
     * @param gridStepLat          The step in latitude direction when iterating
     *                             over the elevation grid upon computing the
     *                             contour lines. If the step is {@code 1}, each
     *                             grid point will be considered. If the step is
     *                             {@code 2}, only each 2nd grid point will be
     *                             conidered, etc. Value must be {@code >= 1}.
     * @param gridStepLon          The step in longitude direction when iterating
     *                             over the elevation grid upon computing the
     *                             contour lines. Value must be {@code >= 1}.
     * @param lowerCutoffElevation The elevation value below which contour lines
     *                             will not be returned.
     * @param upperCutoffElevation The elevation value above which contour lines
     *                             will not be returned.
     * @return A list of isoline segments defining elevation contour lines within
     *         the bounds or {@code null} if not all of the SRTM tiles have the same
     *         type (i.e. different raster dimensions) or if at least one of the
     *         tiles is not valid (i.e. the data was not loaded yet or is not
     *         available at all).
     * @throws AsyncOperationException if asynchronous computation of the contour
     *                                 lines is rejected
     * @throws InterruptedException    if the thread computing the contour lines is
     *                                 interrupted.
     */
    public ContourLines getContourLines(int isostep, int gridStepLat, int gridStepLon, int lowerCutoffElevation,
            int upperCutoffElevation) throws AsyncOperationException, InterruptedException {
        if (!tileGrid.areAllTilesCached() || tileGrid.isDisposed()) {
            return null;
        }
        return new ContourLines(this, isostep, gridStepLat, gridStepLon, lowerCutoffElevation, upperCutoffElevation);
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
    @Override
    public LowestAndHighestPoints getLowestAndHighestPoints() {
        if (!tileGrid.areAllTilesCached() || tileGrid.isDisposed()) {
            return null;
        }
        if (lowestAndHighestPoints == null) {
            lowestAndHighestPoints = new LowestAndHighestPoints(this);
        }
        return lowestAndHighestPoints;
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
     * @throws AsyncOperationException if an exception occured during asynchronous
     *                                 computation of the hillshade image.
     * @throws InterruptedException    if computation of the hillshade image was
     *                                 interrupted.
     */
    public HillshadeImageTile getHillshadeImageTile(double altitudeDeg, double azimuthDeg, boolean withPerimeter)
            throws AsyncOperationException, InterruptedException {
        if (!tileGrid.areAllTilesCached() || tileGrid.isDisposed()) {
            return null;
        }
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
        if (!tileGrid.areAllTilesCached() || tileGrid.isDisposed()) {
            return null;
        }
        return new ElevationRaster(this);
    }

    /**
     * Blocks the calling thread until all SRTM tiles for the underlying grid have
     * been cached. See {@link SrtmTileGrid#waitForTilesCached}.
     *
     * @throws AsyncOperationException if the {@code CompletableFuture} was
     *                                 completed exceptionally or canceled
     *
     * @throws InterruptedException    if the thread was interrupted.
     */
    public void waitForTilesCached() throws AsyncOperationException, InterruptedException {
        tileGrid.waitForTilesCached();
    }

    /**
     * Returns whether all SRTM tiles required to form the underlying grid are
     * available and in memory. See {@link SrtmTileGrid#areAllTilesCached}.
     *
     *
     * @return {@code true} if all SRTM tiles required to form the underlying grid
     *         are available and in memory.
     */
    public boolean areAllTilesCached() {
        return tileGrid.areAllTilesCached();
    }

    /**
     * Returns the SRTM tile grid this view is based on.
     *
     * @return The SRTM tile grid.
     */
    public SrtmTileGrid getTileGrid() {
        return tileGrid;
    }

    @Override
    public ElevationGrid getElevationGrid() {
        return tileGrid;
    }
}
