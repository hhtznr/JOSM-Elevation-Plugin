package hhtznr.josm.plugins.elevation.data;

import java.util.LinkedList;
import java.util.List;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.tools.Logging;

import hhtznr.josm.plugins.elevation.gui.ContourLines;
import hhtznr.josm.plugins.elevation.gui.ElevationRaster;
import hhtznr.josm.plugins.elevation.gui.HillshadeImageTile;

/**
 * A 2D grid of SRTM tiles arranged in their geographic order to cover given
 * latitude-longitude bounds with elevation data. This class provides a method
 * to generate isolines from the elevation data within the bounds using the
 * Marching Squares algorithm.
 *
 * @author Harald Hetzner
 */
public class SRTMTileGrid {

    private final Bounds gridBounds;
    private final int gridIntLatSouth;
    private final int gridIntLatNorth;
    private final int gridIntLonWest;
    private final int gridIntLonEast;
    private final int gridWidth;
    private final int gridHeight;

    // Stores the SRTM tiles of this grid in row-major order. Southernmost tile
    // first, within a row from west to east.
    private final SRTMTile[] srtmTiles;
    private final int rasterWidth;
    private final int rasterHeight;

    private final double latLonStep;
    private final int tileLength;

    private boolean allTilesCached = false;

    /**
     * Creates a new 2D grid of SRTM tiles to cover the given latitude-longitude
     * bound with elevation data.
     *
     * @param elevationDataProvider The elevation data provider providing tiles for
     *                              this grid.
     * @param bounds                The bounds of the map view in latitude-longitude
     *                              coordinate space.
     */
    public SRTMTileGrid(ElevationDataProvider elevationDataProvider, Bounds bounds) {
        elevationDataProvider.cacheSRTMTiles(bounds);

        if (elevationDataProvider.getPreferredSRTMType() == SRTMTile.Type.SRTM1) {
            latLonStep = SRTMTile.SRTM1_ANGULAR_STEP;
            tileLength = SRTMTile.SRTM1_TILE_LENGTH;
        } else {
            latLonStep = SRTMTile.SRTM3_ANGULAR_STEP;
            tileLength = SRTMTile.SRTM3_TILE_LENGTH;
        }

        double latMin = Math.max(bounds.getMinLat(), -90.0);
        double latMax = Math.min(bounds.getMaxLat(), 90.0);
        double lonMin = Math.max(bounds.getMinLon(), -180.0);
        double lonMax = Math.min(bounds.getMaxLon(), 180.0);

        // Determine south west and north east corner coordinate from bounds
        gridIntLatSouth = (int) Math.floor(latMin);
        gridIntLatNorth = (int) Math.floor(latMax) + 1;
        // Not across 180th meridian
        if (lonMin <= lonMax) {
            gridIntLonWest = (int) Math.floor(lonMin);
            gridIntLonEast = (int) Math.floor(lonMax) + 1;
        }
        // Across 180th meridian
        else {
            gridIntLonWest = (int) Math.floor(lonMax);
            gridIntLonEast = (int) Math.floor(lonMin);
        }

        // TODO: Check how to deal with this across 180th meridian
        gridBounds = new Bounds(gridIntLatSouth, gridIntLonWest, gridIntLatNorth, gridIntLonEast);

        // Create an array, which stores the clipped SRTM tiles covering the bounds
        gridHeight = gridIntLatNorth - gridIntLatSouth;
        gridWidth = gridIntLonEast - gridIntLonWest;
        srtmTiles = new SRTMTile[gridHeight * gridWidth];
        // With the exception of the northernmost and easternmost tiles,
        // the SRTM tiles overlap by one row and column
        int effectiveTileLength = tileLength - 1;
        rasterHeight = gridHeight * effectiveTileLength + 1;
        rasterWidth = gridWidth * effectiveTileLength + 1;

        // Fill the 2D array with clipped SRTM tiles
        // Not across 180th meridian
        if (gridIntLonWest < gridIntLonEast) {
            for (int gridLon = gridIntLonWest; gridLon < gridIntLonEast; gridLon++) {
                int gridLonIndex = gridLon - gridIntLonWest;

                for (int gridLat = gridIntLatSouth; gridLat < gridIntLatNorth; gridLat++) {
                    int gridLatIndex = gridLat - gridIntLatSouth;
                    // Calling the getter method will ensure that tiles are being read or downloaded
                    SRTMTile tile = elevationDataProvider.getSRTMTile(SRTMTile.getTileID(gridLat, gridLon));
                    srtmTiles[gridLatIndex * gridWidth + gridLonIndex] = tile;
                }
            }
        }
        // Across 180th meridian
        else {
            for (int gridLon = gridIntLonWest; gridLon <= 179; gridLon++) {
                int gridLonIndex = gridLon - gridIntLonWest;

                for (int gridLat = gridIntLatSouth; gridLat < gridIntLatNorth; gridLat++) {
                    int gridLatIndex = gridLat - gridIntLatSouth;
                    SRTMTile tile = elevationDataProvider.getSRTMTile(SRTMTile.getTileID(gridLat, gridLon));
                    srtmTiles[gridLatIndex * gridWidth + gridLonIndex] = tile;
                }
            }
            for (int gridLon = -180; gridLon < gridIntLonEast; gridLon++) {
                int gridLonIndex = 180 - gridIntLonWest + gridLon - gridIntLonEast;

                for (int gridLat = gridIntLatSouth; gridLat < gridIntLatNorth; gridLat++) {
                    int gridLatIndex = gridLat - gridIntLatSouth;
                    SRTMTile tile = elevationDataProvider.getSRTMTile(SRTMTile.getTileID(gridLat, gridLon));
                    srtmTiles[gridLatIndex * gridWidth + gridLonIndex] = tile;
                }
            }
        }

        Logging.info("Elevation: Created new SRTM tile grid: " + gridBounds.toString() + ", grid width x height = "
                + gridWidth + " x " + gridHeight + ", raster width x height = " + rasterWidth + " x " + rasterHeight);

        areAllSRTMTilesCached();
    }

    /**
     * Returns the width of this SRTM tile grid, i.e. its number of SRTM tiles in
     * longitude direction.
     *
     * @return The width of this SRTM tile grid.
     */
    public int getGridWidth() {
        return gridWidth;
    }

    /**
     * Returns the height of this SRTM tile grid, i.e. its number of SRTM tiles in
     * latitude direction.
     *
     * @return The height of this SRTM tile grid.
     */
    public int getGridHeight() {
        return gridHeight;
    }

    /**
     * Returns the width of the elevation data raster of this SRTM tile grid.
     *
     * @return The width of the elevation data raster of this SRTM tile grid across
     *         all SRTM tiles covered by this grid.
     */
    public int getRasterWidth() {
        return rasterWidth;
    }

    /**
     * Returns the height of the elevation data raster of this SRTM tile grid.
     *
     * @return The height of the elevation data raster of this SRTM tile grid across
     *         all SRTM tiles covered by this grid.
     */
    public int getRasterHeight() {
        return rasterHeight;
    }

    /**
     * Returns raster index bounds which correspond to the given coordinate bounds.
     *
     * @param bounds The bounds for which to determine the the indices of the
     *               elevation raster of this SRTM tile grid, which correspond to
     *               the given bounds.
     * @return Bounds described by indices of this SRTM tile grid's elevation
     *         raster, which correspond to the given coordinate bounds.
     */
    public RasterIndexBounds getRasterIndexBounds(Bounds bounds) {

        double minLat = bounds.getMinLat();
        double minLon = bounds.getMinLon();
        double maxLat = bounds.getMaxLat();
        double maxLon = bounds.getMaxLon();

        double minLatFloor = Math.floor(minLat);
        double minLonFloor = Math.floor(minLon);
        double maxLatFloor = Math.floor(maxLat);
        double maxLonFloor = Math.floor(maxLon);

        int intLatSouth = (int) minLatFloor;
        int intLonWest = (int) minLonFloor;
        int intLatNorth = (int) maxLatFloor;
        if (maxLatFloor == maxLat)
            intLatNorth -= 1;
        int intLonEast = (int) maxLonFloor;
        if (maxLonFloor == maxLon)
            intLonEast -= 1;

        int gridIndexSouth = intLatSouth - gridIntLatSouth;
        int gridIndexNorth = intLatNorth - gridIntLatSouth;
        int gridIndexWest = intLonWest - gridIntLonWest;
        int gridIndexEast = intLonEast - gridIntLonWest;

        SRTMTile tileSouthWest = srtmTiles[gridIndexSouth * gridWidth + gridIndexWest];
        SRTMTile tileNorthEast = srtmTiles[gridIndexNorth * gridWidth + gridIndexEast];
        int[] tileIndicesSouthWest = tileSouthWest.getClosestIndices(minLat, minLon);
        int[] tileIndicesNorthEast = tileNorthEast.getClosestIndices(maxLat, maxLon);
        int tileIndexSouth = tileIndicesSouthWest[0];
        int tileIndexWest = tileIndicesSouthWest[1];
        int tileIndexNorth = tileIndicesNorthEast[0];
        int tileIndexEast = tileIndicesNorthEast[1];

        // Tiles overlap by one row or column
        int effectiveTileLength = tileLength - 1;
        int rasterIndexSouth = gridIndexSouth * effectiveTileLength + tileIndexSouth;
        int rasterIndexNorth = gridIndexNorth * effectiveTileLength + tileIndexNorth;
        int rasterIndexWest = gridIndexWest * effectiveTileLength + tileIndexWest;
        int rasterIndexEast = gridIndexEast * effectiveTileLength + tileIndexEast;

        return new RasterIndexBounds(rasterIndexSouth, rasterIndexNorth, rasterIndexWest, rasterIndexEast);
    }

    /**
     * Returns coordinate bounds which correspond to the given raster index bounds.
     *
     * @param rasterIndexBounds The raster index bounds.
     * @return The corresponding coordinate bounds
     */
    public Bounds getBounds(RasterIndexBounds rasterIndexBounds) {
        double latRange = gridBounds.getHeight();
        double lonRange = gridBounds.getWidth();
        double minLat = gridIntLatSouth
                + latRange * (double) rasterIndexBounds.latIndexSouth / (double) (rasterHeight - 1);
        double maxLat = gridIntLatSouth
                + latRange * (double) rasterIndexBounds.latIndexNorth / (double) (rasterHeight - 1);
        double minLon = gridIntLonWest
                + lonRange * (double) rasterIndexBounds.lonIndexWest / (double) (rasterWidth - 1);
        double maxLon = gridIntLonWest
                + lonRange * (double) rasterIndexBounds.lonIndexEast / (double) (rasterWidth - 1);
        return new Bounds(minLat, minLon, maxLat, maxLon);
    }

    /**
     * Returns the indices of the grid raster point which is closest to the given
     * coordinate.
     *
     * @param latLon The coordinate for which to determine the indices of the
     *               closest point in the grid raster.
     * @return The indices of the grid raster point which is closest to the given
     *         point.
     */
    public int[] getClosestGridRasterIndices(ILatLon latLon) {
        int intLat = (int) Math.floor(latLon.lat());
        int intLon = (int) Math.floor(latLon.lon());

        int gridIndexLat = intLat - gridIntLatSouth;
        int gridIndexLon = intLon - gridIntLonWest;

        SRTMTile tile = srtmTiles[gridIndexLat * gridWidth + gridIndexLon];
        int[] tileIndices = tile.getClosestIndices(latLon);
        int tileIndexLat = tileIndices[0];
        int tileIndexLon = tileIndices[1];

        // Tiles overlap by one row or column
        int effectiveTileLength = tileLength - 1;
        int rasterIndexLat = gridIndexLat * effectiveTileLength + tileIndexLat;
        int rasterIndexLon = gridIndexLon * effectiveTileLength + tileIndexLon;

        return new int[] { rasterIndexLat, rasterIndexLon };
    }

    /**
     * Returns the bounds of the SRTM tiles of this grid.
     *
     * @return The bounds of this SRTM tile grid.
     */
    public Bounds getBounds() {
        return gridBounds;
    }

    /**
     * Returns the step between two adjacent raster points in latitude or longitude
     * direction.
     *
     * @return The step in arc-seconds between two adjacent raster points in
     *         latitude or longitude direction.
     */
    public double getLatLonStep() {
        return latLonStep;
    }

    /**
     * Returns elevation based on global raster indices.
     *
     * @param latIndex The global raster index in latitude direction.
     * @param lonIndex The global raster index in longitude direction.
     * @return The elevation at the provided global raster indices or
     *         {@link SRTMTile#SRTM_DATA_VOID} if no elevation data is cached yet.
     */
    public short getElevation(int latIndex, int lonIndex) {
        if (!allTilesCached)
            return SRTMTile.SRTM_DATA_VOID;
        if (latIndex < 0)
            throw new IllegalArgumentException("Latitude index " + latIndex + " < min.index = 0");
        if (lonIndex < 0)
            throw new IllegalArgumentException("Longitude index " + lonIndex + " < min. index = 0");
        int maxLatIndex = rasterHeight - 1;
        if (latIndex > maxLatIndex)
            throw new IllegalArgumentException("Latitude index " + latIndex + " > max. index = " + maxLatIndex);
        int maxLonIndex = rasterWidth - 1;
        if (lonIndex > maxLonIndex)
            throw new IllegalArgumentException("Longitude index " + lonIndex + " > max. index = " + maxLonIndex);

        return getElevationNoCheck(latIndex, lonIndex);
    }

    private short getElevationNoCheck(int latIndex, int lonIndex) {
        // Add the offsets into the first tiles to the south and to the west

        // Tiles overlap by one row or column
        int effectiveTileLength = tileLength - 1;

        int gridLatIndex;
        int tileLatIndex;
        int gridLonIndex;
        int tileLonIndex;

        // Last tile to the north
        if (latIndex >= rasterHeight - tileLength) {
            gridLatIndex = gridHeight - 1;
            tileLatIndex = latIndex - (gridHeight - 1) * effectiveTileLength;
        } else {
            gridLatIndex = latIndex / effectiveTileLength;
            tileLatIndex = latIndex % effectiveTileLength;
        }

        // Last tile to the east
        if (lonIndex >= rasterWidth - tileLength) {
            gridLonIndex = gridWidth - 1;
            tileLonIndex = lonIndex - (gridWidth - 1) * effectiveTileLength;
        } else {
            gridLonIndex = lonIndex / effectiveTileLength;
            tileLonIndex = lonIndex % effectiveTileLength;
        }

        SRTMTile tile = srtmTiles[gridLatIndex * gridWidth + gridLonIndex];
        return tile.getElevation(tileLatIndex, tileLonIndex);
    }

    /**
     * Returns the coordinates, which correspond to the point at the given raster
     * indices, and its elevation value.
     *
     * @param latIndex The index of the point in latitude direction within this tile
     *                 grid's elevation raster spanning across all included SRTM
     *                 tiles.
     * @param lonIndex The index of the point in longitude direction within this
     *                 tile grid's elevation raster spanning across all included
     *                 SRTM tiles.
     * @return The coordinates of the raster point and its elevation.
     */
    public LatLonEle getLatLonEle(int latIndex, int lonIndex) {
        int maxLatIndex = rasterHeight - 1;
        int maxLonIndex = rasterWidth - 1;
        double lat = gridBounds.getMinLat() + ((double) latIndex / (double) maxLatIndex) * gridBounds.getHeight();
        double lon = gridBounds.getMinLon() + ((double) lonIndex / (double) maxLonIndex) * gridBounds.getWidth();
        short ele = getElevationNoCheck(latIndex, lonIndex);
        return new LatLonEle(lat, lon, ele);
    }

    /**
     * Returns a list with two lists of the coordinate points from the elevation
     * raster, which have the lowest and highest elevation within the given map
     * bounds, respectively.
     *
     * @param bounds The bounds in latitude-longitude coordinate space.
     * @return A list of two lists providing the coordinate points with the lowest
     *         and highest elevation within the given bounds. The first list holds
     *         the lowest points. The second list holds the highest points.
     *         {@code null} is returned if not all SRTM tiles are cached yet.
     */
    public List<List<LatLonEle>> getLowestAndHighestPoints(Bounds bounds) {
        if (!allTilesCached)
            return null;

        RasterIndexBounds rasterIndexBounds = getRasterIndexBounds(bounds);
        int rasterBoundsWidth = rasterIndexBounds.getWidth();
        int rasterBoundsHeight = rasterIndexBounds.getHeight();
        double gridBoundsWidth = gridBounds.getWidth();
        double gridBoundsHeight = gridBounds.getHeight();

        short previousMinEle = Short.MAX_VALUE;
        short previousMaxEle = Short.MIN_VALUE;
        LinkedList<LatLonEle> lowestPoints = new LinkedList<>();
        LinkedList<LatLonEle> highestPoints = new LinkedList<>();

        for (int latIndex = 0; latIndex < rasterBoundsHeight - 1; latIndex++) {
            int gridRasterLatIndex = latIndex + rasterIndexBounds.latIndexSouth;
            double lat = gridIntLatSouth + gridBoundsHeight * (double) gridRasterLatIndex / (double) (rasterHeight - 1);

            for (int lonIndex = 0; lonIndex < rasterBoundsWidth - 1; lonIndex++) {
                int gridRasterLonIndex = lonIndex + rasterIndexBounds.lonIndexWest;
                double lon = gridIntLonWest
                        + gridBoundsWidth * (double) gridRasterLonIndex / (double) (rasterWidth - 1);

                short ele = getElevation(gridRasterLatIndex, gridRasterLonIndex);

                if (ele < previousMinEle) {
                    lowestPoints.clear();
                    lowestPoints.add(new LatLonEle(lat, lon, ele));
                    previousMinEle = ele;
                } else if (ele == previousMinEle) {
                    lowestPoints.add(new LatLonEle(lat, lon, ele));
                }

                if (ele > previousMaxEle) {
                    highestPoints.clear();
                    highestPoints.add(new LatLonEle(lat, lon, ele));
                    previousMaxEle = ele;
                } else if (ele == previousMaxEle) {
                    highestPoints.add(new LatLonEle(lat, lon, ele));
                }
            }
        }

        List<List<LatLonEle>> highestAndLowestPoints = new LinkedList<>();
        highestAndLowestPoints.add(lowestPoints);
        highestAndLowestPoints.add(highestPoints);
        return highestAndLowestPoints;
    }

    /**
     * Returns all raster coordinates and the associated elevation values within the
     * bounds.
     *
     * @param renderingBounds The bounds, within which the requested elevation
     *                        raster shall be renderable.
     * @return All raster coordinates and the associated elevation values within the
     *         bounds or {@code null} if not all of the SRTM tiles have the same
     *         type (i.e. different raster dimensions) or if at least one of the
     *         tiles is not valid (i.e. the data was not loaded yet or is not
     *         available at all).
     */
    public ElevationRaster getElevationRaster(Bounds renderingBounds) {
        if (!allTilesCached)
            return null;
        if (!covers(renderingBounds))
            return null;
        return new ElevationRaster(this, renderingBounds);
    }

    /**
     * Creates a buffered image with the computed hillshade ARGB values for the
     * elevation values of this SRTM tile grid.
     *
     * @param renderingBounds The bounds, within which the requested hillshade image
     *                        shall be renderable.
     * @param altitudeDeg     The altitude is the angle of the illumination source
     *                        above the horizon. The units are in degrees, from 0
     *                        (on the horizon) to 90 (overhead).
     * @param azimuthDeg      The azimuth is the angular direction of the sun,
     *                        measured from north in clockwise degrees from 0 to
     *                        360.
     * @param withPerimeter   If {@code} true, the a first and last row as well as
     *                        the a first and last column without computed values
     *                        will be added such that the size of the 2D array
     *                        corresponds to that of the input data. If
     *                        {@code false}, these rows and columns will be omitted.
     * @return An image with the computed hillshade values or {@code null} if this
     *         SRTM tile grid cannot deliver elevation values or there are less than
     *         3 elevation values in one of the two dimensions.
     */
    public HillshadeImageTile getHillshadeImageTile(Bounds renderingBounds, double altitudeDeg, double azimuthDeg,
            boolean withPerimeter) {
        if (!allTilesCached)
            return null;
        if (!covers(renderingBounds))
            return null;
        return new HillshadeImageTile(this, renderingBounds, altitudeDeg, azimuthDeg, withPerimeter);
    }

    /**
     * Returns a list of isoline segments defining elevation contour lines within
     * the bounds. The segments do not have a useful order. This method will
     * slightly adjust the bounds to the closest coordinates of the elevation
     * raster.
     *
     * @param renderingBounds      The bounds, within which the requested contour
     *                             lines shall be renderable.
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
    public ContourLines getContourLines(Bounds renderingBounds, int isostep, int lowerCutoffElevation,
            int upperCutoffElevation) {
        if (!allTilesCached)
            return null;
        if (!covers(renderingBounds))
            return null;
        return new ContourLines(this, renderingBounds, isostep, lowerCutoffElevation, upperCutoffElevation);
    }

    /**
     * Returns an array of isovalues within the given bounds.
     *
     * @param rasterIndexBounds    The indices of the elevation raster of this SRTM
     *                             tile grid, which describe the bounds, within
     *                             which the isovalues shall be determined.
     * @param isostep              Step between two adjacent isolines.
     * @param lowerCutoffElevation The elevation value below which isovalues will
     *                             not be returned.
     * @param upperCutoffElevation The elevation value above which isovalues will
     *                             not be returned.
     * @return An array of the isovalues within the given bounds considering the
     *         given isostep and the given cutoff values.
     */
    public short[] getIsovalues(RasterIndexBounds rasterIndexBounds, int isostep, int lowerCutoffElevation,
            int upperCutoffElevation) {
        short minEle = Short.MAX_VALUE;
        short maxEle = SRTMTile.SRTM_DATA_VOID;
        for (int latIndex = rasterIndexBounds.latIndexSouth; latIndex <= rasterIndexBounds.latIndexNorth; latIndex++) {
            for (int lonIndex = rasterIndexBounds.lonIndexWest; lonIndex <= rasterIndexBounds.lonIndexEast; lonIndex++) {
                short ele = getElevation(latIndex, lonIndex);
                // Ignore data voids in the assessment of minimum elevation
                if (ele != SRTMTile.SRTM_DATA_VOID)
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
     * Returns whether all SRTM tiles required to form this grid are available and
     * in memory.
     *
     * @return {@code true} if all SRTM tiles required to form this grid are
     *         available and in memory.
     */
    public boolean areAllSRTMTilesCached() {
        if (allTilesCached)
            return true;
        for (int gridLatIndex = 0; gridLatIndex < gridHeight; gridLatIndex++) {
            for (int gridLonIndex = 0; gridLonIndex < gridWidth; gridLonIndex++) {
                SRTMTile tile = srtmTiles[gridLatIndex * gridWidth + gridLonIndex];
                SRTMTile.Status tileStatus = tile.getStatus();
                // If at least one SRTM tile does not have a final status
                /**
                 * Note: E.g. tile N43E014 might always be missing because it is neither
                 * available from NASA Earthdata nor from Sonny because it would only cover a
                 * part of the Adriatic Sea and therefore is not really meaningful.
                 */
                if (!(tileStatus == SRTMTile.Status.VALID || tileStatus == SRTMTile.Status.DOWNLOAD_FAILED
                        || tileStatus == SRTMTile.Status.FILE_MISSING))
                    return false;
            }
        }

        allTilesCached = true;
        return true;
    }

    /**
     * Returns whether the given bounds are covered by the bounds of this grid.
     *
     * @param bounds The bounds for which to check if they are covered by this grid.
     * @return {@code true} if the given bounds are contained in this grid's full
     *         bounds, {@code false} otherwise.
     */
    public boolean covers(Bounds bounds) {
        return gridBounds.contains(bounds);
    }

    /**
     * Returns bounds, which are smaller by the given number of raster steps.
     *
     * @param bounds              The bounds, from which smaller bounds shall be
     *                            derived.
     * @param rasterStepDecrement The number of raster steps by which to decrease
     *                            the size of the bounds.
     * @return Bounds, which are smaller than the given bounds by the given number
     *         of raster steps.
     */
    public Bounds getViewBoundsScaledByRasterStep(Bounds bounds, int rasterStepDecrement) {
        if (rasterStepDecrement < 0)
            throw new IllegalArgumentException("Raster decrement must be >= 0. Given: " + rasterStepDecrement);
        return scaleBoundsByRasterStep(bounds, -rasterStepDecrement);
    }

    /**
     * Returns bounds, which are bigger by the given number of raster steps.
     *
     * @param bounds              The bounds, from which smaller bounds shall be
     *                            derived.
     * @param rasterStepIncrement The number of raster steps by which to increase
     *                            the size of the bounds.
     * @return Bounds, which are bigger than the given bounds by the given number of
     *         raster steps.
     */
    public Bounds getRenderingBoundsScaledByRasterStep(Bounds bounds, int rasterStepIncrement) {
        if (rasterStepIncrement < 0)
            throw new IllegalArgumentException("Raster increment must be >= 0. Given: " + rasterStepIncrement);
        return scaleBoundsByRasterStep(bounds, rasterStepIncrement);
    }

    private Bounds scaleBoundsByRasterStep(Bounds bounds, int rasterStep) {
        // Increase or decreases the bounds by the amount of raster steps. But in case
        // of increase not more as the maximum
        // possible coordinate range (-90 <= lat <= 90, -180 <= lon <= 180)
        // This will ensure that computed contour lines actually cover the bounds
        double latMin = Math.max(bounds.getMinLat() - rasterStep * latLonStep, -90.0);
        double latMax = Math.min(bounds.getMaxLat() + rasterStep * latLonStep, 90.0);
        double lonMin = Math.max(bounds.getMinLon() - rasterStep * latLonStep, -180.0);
        double lonMax = Math.min(bounds.getMaxLon() + rasterStep * latLonStep, 180.0);
        return new Bounds(latMin, lonMin, latMax, lonMax);
    }

    /**
     * Bounds described by latitude and longitude indices of an SRTM tile grid.
     */
    public static class RasterIndexBounds {

        /**
         * The minimum (southernmost) index in latitude direction.
         */
        public final int latIndexSouth;

        /**
         * The maximum (northernmost) index in latitude direction.
         */
        public final int latIndexNorth;

        /**
         * The minimum (westernnmost) index in longitude direction.
         */
        public final int lonIndexWest;

        /**
         * The maximum (easternmost) index in longitude direction.
         */
        public final int lonIndexEast;

        /**
         * Creates new raster index bounds.
         *
         * @param latIndexSouth The minimum (southernmost) index in latitude direction.
         * @param latIndexNorth The maximum (northernmost) index in latitude direction.
         * @param lonIndexWest  The minimum (westernnmost) index in longitude direction.
         * @param lonIndexEast  The maximum (easternmost) index in longitude direction.
         */
        public RasterIndexBounds(int latIndexSouth, int latIndexNorth, int lonIndexWest, int lonIndexEast) {
            this.latIndexSouth = latIndexSouth;
            this.latIndexNorth = latIndexNorth;
            this.lonIndexWest = lonIndexWest;
            this.lonIndexEast = lonIndexEast;
        }

        /**
         * Returns the index height of these bounds.
         *
         * @return The index height of these bounds, i.e. the difference between
         *         northernmost and southernmost index {@code +1}.
         */
        public int getHeight() {
            return latIndexNorth - latIndexSouth + 1;
        }

        /**
         * Returns the index width of these bounds.
         *
         * @return The index width of these bounds, i.e. the difference between
         *         easternmost and westernmost index {@code +1}.
         */
        public int getWidth() {
            return lonIndexEast - lonIndexWest + 1;
        }
    }
}
