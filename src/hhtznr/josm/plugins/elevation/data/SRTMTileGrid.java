package hhtznr.josm.plugins.elevation.data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.tools.Logging;

/**
 * A 2D grid of SRTM tiles arranged in their geographic order to cover given
 * latitude-longitude bounds with elevation data. This class provides a method
 * to generate isolines from the elevation data within the bounds using the
 * Marching Squares algorithm.
 *
 * @author Harald Hetzner
 */
public class SRTMTileGrid implements SRTMTileCacheListener {

    private final Bounds gridBounds;
    private final int gridIntLatSouth;
    private final int gridIntLatNorth;
    private final int gridIntLonWest;
    private final int gridIntLonEast;
    private final int gridWidth;
    private final int gridHeight;

    private final SRTMTile.Type srtmType;
    // Stores the SRTM tiles of this grid in row-major order. Southernmost tile
    // first, within a row from west to east.
    private final Object tileFuturesLock = new Object();
    private List<CompletableFuture<SRTMTile>> tileFutures = null;
    private SRTMTile[] srtmTiles = null;
    private final int rasterWidth;
    private final int rasterHeight;

    private final double latLonStep;
    private final int tileLength;

    private final ElevationDataProvider elevationDataProvider;

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
        this.elevationDataProvider = elevationDataProvider;
        elevationDataProvider.cacheSRTMTiles(bounds);
        srtmType = elevationDataProvider.getSRTMType();
        if (srtmType == SRTMTile.Type.SRTM1) {
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

        gridHeight = gridIntLatNorth - gridIntLatSouth;
        gridWidth = gridIntLonEast - gridIntLonWest;
        int gridSize = gridHeight * gridWidth;
        tileFutures = new ArrayList<CompletableFuture<SRTMTile>>(gridSize);

        // Not across 180th meridian
        if (gridIntLonWest < gridIntLonEast) {
            for (int gridLat = gridIntLatSouth; gridLat < gridIntLatNorth; gridLat++) {
                for (int gridLon = gridIntLonWest; gridLon < gridIntLonEast; gridLon++) {
                    String srtmTileID = SRTMTile.getTileID(gridLat, gridLon);
                    // Calling the getter method will ensure that tiles are being read or downloaded
                    CompletableFuture<SRTMTile> future = elevationDataProvider.getSRTMTileFuture(srtmTileID);
                    tileFutures.add(future);
                }
            }
        }
        // Across 180th meridian
        else {
            for (int gridLat = gridIntLatSouth; gridLat < gridIntLatNorth; gridLat++) {
                for (int gridLon = gridIntLonWest; gridLon <= 179; gridLon++) {
                    String srtmTileID = SRTMTile.getTileID(gridLat, gridLon);
                    // Calling the getter method will ensure that tiles are being read or downloaded
                    CompletableFuture<SRTMTile> future = elevationDataProvider.getSRTMTileFuture(srtmTileID);
                    tileFutures.add(future);
                }
            }
            for (int gridLat = gridIntLatSouth; gridLat < gridIntLatNorth; gridLat++) {
                for (int gridLon = -180; gridLon < gridIntLonEast; gridLon++) {
                    String srtmTileID = SRTMTile.getTileID(gridLat, gridLon);
                    // Calling the getter method will ensure that tiles are being read or downloaded
                    CompletableFuture<SRTMTile> future = elevationDataProvider.getSRTMTileFuture(srtmTileID);
                    tileFutures.add(future);
                }
            }
        }

        // With the exception of the northernmost and easternmost tiles,
        // the SRTM tiles overlap by one row and column
        int effectiveTileLength = tileLength - 1;
        rasterHeight = gridHeight * effectiveTileLength + 1;
        rasterWidth = gridWidth * effectiveTileLength + 1;

        Logging.info("Elevation: Created new SRTM tile grid: " + gridBounds.toString() + ", grid width x height = "
                + gridWidth + " x " + gridHeight + ", raster width x height = " + rasterWidth + " x " + rasterHeight);

        elevationDataProvider.addTileCacheListener(this);
        areAllTilesCached();
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
     * Returns a view for convenient index-based access to a subarea of this SRTM
     * tile grid.
     *
     * @param bounds The coordinate bounds of the subarea.
     * @return The tile grid view corresponding to the specified subarea.
     */
    public SRTMTileGridView getView(Bounds bounds) throws SRTMTileGridException {
        if (!gridBounds.contains(bounds)) {
            String message = "Bounds " + gridBounds.toString()
                    + " of this SRTM tile grid do not contain the request view bounds " + bounds.toString();
            throw new SRTMTileGridException(message);
        }

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

        int[] tileIndicesSouthWest = SRTMTile.getClosestIndices(minLat, minLon, srtmType);
        int[] tileIndicesNorthEast = SRTMTile.getClosestIndices(maxLat, maxLon, srtmType);
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

        Bounds viewBounds = getBounds(rasterIndexSouth, rasterIndexNorth, rasterIndexWest, rasterIndexEast);
        return new SRTMTileGridView(this, rasterIndexSouth, rasterIndexNorth, rasterIndexWest, rasterIndexEast,
                viewBounds);
    }

    /**
     * Returns coordinate bounds which correspond to the given raster index bounds.
     *
     * @param latIndexSouth The raster index of the southernmost raster points
     *                      ({@code 0 <= latIndexSouth <= getHeight()}).
     * @param latIndexNorth The raster index of the northernmost raster points
     *                      ({@code 0 <= latIndexNorth <= getHeight(); latIndexSouth < latIndexNorth}).
     * @param lonIndexWest  The raster index of the westernmost raster points
     *                      ({@code 0 <= lonIndexWest <= getWidth()}).
     * @param lonIndexEast  The raster index of the easternmost raster points
     *                      ({@code 0 <= lonIndexEast <= getWidth(); lonIndexWest < lonIndexEast}).
     * @return The corresponding coordinate bounds
     */
    private Bounds getBounds(int latIndexSouth, int latIndexNorth, int lonIndexWest, int lonIndexEast) {
        double latRange = gridBounds.getHeight();
        double lonRange = gridBounds.getWidth();
        double latScale = latRange / (double) (rasterHeight - 1);
        double lonScale = lonRange / (double) (rasterWidth - 1);
        double minLat = gridIntLatSouth + latScale * (double) latIndexSouth;
        double maxLat = gridIntLatSouth + latScale * (double) latIndexNorth;
        double minLon = gridIntLonWest + lonScale * (double) lonIndexWest;
        double maxLon = gridIntLonWest + lonScale * (double) lonIndexEast;
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
        // Determine the array index at which to retrieve the elevation at the given
        // location
        double lat = latLon.lat();
        if (lat < gridIntLatSouth || lat > gridIntLatNorth)
            throw new IllegalArgumentException(
                    "Given latitude " + lat + " is not within latitude range of SRTM tile grid from " + gridIntLatSouth
                            + " to " + gridIntLatNorth);

        double lon = latLon.lon();
        if (lon < gridIntLonWest || lon > gridIntLonEast)
            throw new IllegalArgumentException(
                    "Given longitude " + lon + " is not within longitude range of SRTM tile grid from " + gridIntLonWest
                            + " to " + gridIntLonEast);

        // gridHeight = gridIntLatNorth - gridIntLatSouth
        int latIndex = (int) Math.round((lat - gridIntLatSouth) / gridHeight * (rasterHeight - 1));
        // gridWidth = gridIntLonEast - gridIntLonWest
        int lonIndex = (int) Math.round((lon - gridIntLonWest) / gridWidth * (rasterWidth - 1));
        return new int[] { latIndex, lonIndex };
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
        if (srtmTiles == null)
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

        if (srtmTiles == null)
            return SRTMTile.SRTM_DATA_VOID;

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
     * Returns whether this tile grid technically contains an SRTM tile.
     *
     * @param tile The tile for which to check if it is technically contained in
     *             this grid.
     * @return {@code true} if the coordinates of the tile are within the bounds of
     *         this grid and the SRTM type of the tile corresponds to this grid's
     *         SRTM type.
     */
    public boolean contains(SRTMTile tile) {
        int idLat = tile.getLatID();
        int idLon = tile.getLonID();
        SRTMTile.Type type = tile.getType();
        return idLat >= gridIntLatSouth && idLat < gridIntLatNorth && idLon >= gridIntLonWest && idLon < gridIntLonEast
                && type == srtmType;
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
     * Blocks the calling thread until all SRTM tiles for this grid have been
     * cached.
     * <p>
     * This method should be used by threads that require the grid to be fully
     * initialized before proceeding. It will return immediately if all tiles are
     * already cached.
     * </p>
     *
     * @throws InterruptedException if the waiting thread is interrupted while
     *                              waiting
     */
    public void waitForTilesCached() throws InterruptedException {
        List<CompletableFuture<SRTMTile>> futures;
        synchronized (tileFuturesLock) {
            futures = this.tileFutures;
        }
        if (futures == null)
            return;
        for (int gridLatIndex = 0; gridLatIndex < gridHeight; gridLatIndex++) {
            for (int gridLonIndex = 0; gridLonIndex < gridWidth; gridLonIndex++) {
                CompletableFuture<SRTMTile> future = futures.get(gridLatIndex * gridWidth + gridLonIndex);
                try {
                    // Blocks until the tile is cached or caching is interrupted
                    future.get();
                } catch (ExecutionException e) {
                    // Should never happen because we do not complete exceptionally
                    Logging.error("Elevation: Could not get tile grid tile at grid indices (" + gridLatIndex + ", "
                            + gridLonIndex + "): " + e.toString());
                    continue;
                }
            }
        }
    }

    /**
     * Returns whether all SRTM tiles required to form this grid are available and
     * in memory.
     *
     * @return {@code true} if all SRTM tiles required to form this grid are
     *         available and in memory.
     */
    public boolean areAllTilesCached() {
        if (srtmTiles != null)
            return true;
        List<CompletableFuture<SRTMTile>> futures;
        synchronized (tileFuturesLock) {
            futures = this.tileFutures;
        }
        if (futures == null)
            return true;
        for (int gridLatIndex = 0; gridLatIndex < gridHeight; gridLatIndex++) {
            for (int gridLonIndex = 0; gridLonIndex < gridWidth; gridLonIndex++) {
                CompletableFuture<SRTMTile> future = futures.get(gridLatIndex * gridWidth + gridLonIndex);
                // Note: "Done" does not necessarily mean that the tiles hold valid data
                // However, we can deal with no-data tiles by treating them like big data voids
                if (!future.isDone()) {
                    return false;
                }
            }
        }
        try {
            assmebleGrid();
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    private void assmebleGrid() throws InterruptedException {
        synchronized (tileFuturesLock) {
            // We only assemble the grid once
            if (tileFutures == null)
                return;
            int gridSize = gridHeight * gridWidth;
            SRTMTile[] tiles = new SRTMTile[gridSize];

            // Not across 180th meridian
            if (gridIntLonWest < gridIntLonEast) {
                for (int gridLat = gridIntLatSouth; gridLat < gridIntLatNorth; gridLat++) {
                    int gridLatIndex = gridLat - gridIntLatSouth;
                    for (int gridLon = gridIntLonWest; gridLon < gridIntLonEast; gridLon++) {
                        int gridLonIndex = gridLon - gridIntLonWest;

                        int linearIndex = gridLatIndex * gridWidth + gridLonIndex;
                        // Calling the getter method will ensure that tiles are being read or downloaded
                        String tileID = SRTMTile.getTileID(gridLat, gridLon);
                        SRTMTile tile;
                        try {
                            // The futures are stored in the same linear order as the tiles
                            tile = tileFutures.get(linearIndex).get();
                        } catch (ExecutionException e) {
                            // Should never happen because we do not complete exceptionally
                            Logging.error("Elevation: Could not assemble tile grid. Exception with tile " + tileID
                                    + ": " + e.toString());
                            return;
                        }
                        tiles[linearIndex] = tile;
                    }
                }
            }
            // Across 180th meridian
            else {
                for (int gridLat = gridIntLatSouth; gridLat < gridIntLatNorth; gridLat++) {
                    int gridLatIndex = gridLat - gridIntLatSouth;
                    for (int gridLon = gridIntLonWest; gridLon <= 179; gridLon++) {
                        int gridLonIndex = gridLon - gridIntLonWest;

                        int linearIndex = gridLatIndex * gridWidth + gridLonIndex;
                        String tileID = SRTMTile.getTileID(gridLat, gridLon);
                        SRTMTile tile;
                        try {
                            // The futures are stored in the same linear order as the tiles
                            tile = tileFutures.get(linearIndex).get();
                        } catch (ExecutionException e) {
                            // Should never happen because we do not complete exceptionally
                            Logging.error("Elevation: Could not assemble tile grid. Exception with tile " + tileID
                                    + ": " + e.toString());
                            return;
                        }
                        tiles[linearIndex] = tile;
                    }
                }
                for (int gridLat = gridIntLatSouth; gridLat < gridIntLatNorth; gridLat++) {
                    int gridLatIndex = gridLat - gridIntLatSouth;
                    for (int gridLon = -180; gridLon < gridIntLonEast; gridLon++) {
                        int gridLonIndex = 180 - gridIntLonWest + gridLon - gridIntLonEast;

                        int linearIndex = gridLatIndex * gridWidth + gridLonIndex;
                        String tileID = SRTMTile.getTileID(gridLat, gridLon);
                        SRTMTile tile;
                        try {
                            // The futures are stored in the same linear order as the tiles
                            tile = tileFutures.get(linearIndex).get();
                        } catch (ExecutionException e) {
                            // Should never happen because we do not complete exceptionally
                            Logging.error("Elevation: Could not assemble tile grid. Exception with tile " + tileID
                                    + ": " + e.toString());
                            return;
                        }
                        tiles[linearIndex] = tile;
                    }
                }
            }
            // When all tiles of this grid are cached, we do no longer need to listen
            elevationDataProvider.removeTileCacheListener(this);
            srtmTiles = tiles;
            // We no longer need the list of futures as soon as the grid is assembled
            tileFutures = null;
        }
    }

    @Override
    public void srtmTileCached(SRTMTile tile, SRTMTileCacheEntry.Status status) {
        if (contains(tile))
            areAllTilesCached();
    }
}
