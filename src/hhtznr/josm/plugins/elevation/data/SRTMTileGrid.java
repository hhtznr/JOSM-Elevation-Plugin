package hhtznr.josm.plugins.elevation.data;

import java.util.ArrayList;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;

import hhtznr.josm.plugins.elevation.gui.ContourLines;
import hhtznr.josm.plugins.elevation.gui.ElevationRaster;
import hhtznr.josm.plugins.elevation.gui.HillshadeImageTile;
import hhtznr.josm.plugins.elevation.math.Hillshade;
import hhtznr.josm.plugins.elevation.math.MarchingSquares;

/**
 * A 2D grid of SRTM tiles arranged in their geographic order to cover given
 * latitude-longitude bounds with elevation data. This class provides a method
 * to generate isolines from the elevation data within the bounds using the
 * Marching Squares algorithm.
 *
 * @author Harald Hetzner
 */
public class SRTMTileGrid {

    private final Bounds nominalBounds;
    private Bounds actualBounds;

    private ClippedSRTMTile[][] clippedTiles;
    private short[][] gridEleValues = null;

    /**
     * Creates a new 2D grid of SRTM tiles to cover the given latitude-longitude
     * bound with elevation data.
     *
     * @param elevationDataProvider The elevation data provider providing tiles for
     *                              this grid.
     * @param bounds                The bounds in latitude-longitude coordinate
     *                              space.
     */
    protected SRTMTileGrid(ElevationDataProvider elevationDataProvider, Bounds bounds) {
        nominalBounds = bounds;
        double latLonIncr;
        if (elevationDataProvider.getPreferredSRTMType() == SRTMTile.Type.SRTM1)
            latLonIncr = SRTMTile.SRTM_TILE_ARC_DEGREES / (SRTMTile.SRTM1_TILE_LENGTH - 1);
        else
            latLonIncr = SRTMTile.SRTM_TILE_ARC_DEGREES / (SRTMTile.SRTM3_TILE_LENGTH - 1);

        // Increase the bounds by three raster increments, but not more as the maximum
        // possible coordinate range (-90 <= lat <= 90, -180 <= lon <= 180)
        // This will ensure that computed contour lines actually cover the bounds
        double latMin = Math.max(bounds.getMinLat() - 3 * latLonIncr, -90.0);
        double latMax = Math.min(bounds.getMaxLat() + 3 * latLonIncr, 90.0);
        double lonMin = Math.max(bounds.getMinLon() - 3 * latLonIncr, -180.0);
        double lonMax = Math.min(bounds.getMaxLon() + 3 * latLonIncr, 180.0);

        // Determine south west and north east corner coordinate from bounds
        int gridIntLatSouth = (int) Math.floor(latMin);
        int gridIntLatNorth = (int) Math.floor(latMax);
        int gridIntLonWest;
        int gridIntLonEast;
        // Not across 180th meridian
        if (lonMin <= lonMax) {
            gridIntLonWest = (int) Math.floor(lonMin);
            gridIntLonEast = (int) Math.floor(lonMax);
            actualBounds = new Bounds(latMin, lonMin, latMax, lonMax);
        }
        // Across 180th meridian
        else {
            gridIntLonWest = (int) Math.floor(lonMax);
            gridIntLonEast = (int) Math.floor(lonMin);
            actualBounds = new Bounds(latMin, lonMax, latMax, lonMin);
        }

        // Trigger needed tiles being cached, if they are not cached yet
        elevationDataProvider.cacheSRTMTiles(gridIntLatSouth, gridIntLonWest, gridIntLatNorth, gridIntLonEast);

        // Create an array, which stores the clipped SRTM tiles covering the bounds
        clippedTiles = new ClippedSRTMTile[gridIntLatNorth - gridIntLatSouth + 1][gridIntLonEast - gridIntLonWest + 1];

        double tileLatSouth;
        double tileLonWest;
        double tileLatNorth;
        double tileLonEast;

        // Fill the 2D array with clipped SRTM tiles
        // Not across 180th meridian
        if (gridIntLonWest <= gridIntLonEast) {
            for (int gridLon = gridIntLonWest; gridLon <= gridIntLonEast; gridLon++) {
                // For the most western tile, its western edge needs to be clipped
                if (gridLon == gridIntLonWest)
                    tileLonWest = actualBounds.getMinLon();
                // If the tile is not the most western tile, its western edge does not need to
                // be clipped
                else
                    tileLonWest = gridLon;
                // For the most eastern tile, its eastern edge needs to be clipped
                if (gridLon == gridIntLonEast)
                    tileLonEast = actualBounds.getMaxLon();
                // If the tile is not the most eastern tile, its eastern edge does not need to
                // be clipped
                else
                    tileLonEast = gridLon + 1;

                for (int gridLat = gridIntLatSouth; gridLat <= gridIntLatNorth; gridLat++) {
                    // Calling the getter method will ensure that tiles are being read or downloaded
                    SRTMTile tile = elevationDataProvider.getSRTMTile(SRTMTile.getTileID(gridLat, gridLon));
                    // For the most southern tile, its southern edge needs to be clipped
                    if (gridLat == gridIntLatSouth)
                        tileLatSouth = actualBounds.getMinLat();
                    // If the tile is not the most southern tile, its southern edge does not need to
                    // be clipped
                    else
                        tileLatSouth = gridLat;
                    // For the most northern tile, its northern edge needs to be clipped
                    if (gridLat == gridIntLatNorth)
                        tileLatNorth = actualBounds.getMaxLat();
                    // If the tile is not the most northern tile, its northern edge does not need to
                    // be clipped
                    else
                        tileLatNorth = gridLat + 1;
                    LatLon tileSouthWest = new LatLon(tileLatSouth, tileLonWest);
                    LatLon tileNorthEast = new LatLon(tileLatNorth, tileLonEast);
                    clippedTiles[gridIntLatNorth - gridLat][gridLon - gridIntLonWest] = new ClippedSRTMTile(tile,
                            tileSouthWest, tileNorthEast);

                }
            }
        }
        // Across 180th meridian
        else {
            for (int lon = gridIntLonWest; lon <= 179; lon++) {
                if (lon == gridIntLonWest)
                    tileLonWest = actualBounds.getMinLon();
                else
                    tileLonWest = lon;
                if (lon == gridIntLonEast)
                    tileLonEast = actualBounds.getMaxLon();
                else
                    tileLonEast = lon + 1;

                for (int lat = gridIntLatSouth; lat <= gridIntLatNorth; lat++) {
                    SRTMTile tile = elevationDataProvider.getSRTMTile(SRTMTile.getTileID(lat, lon));
                    if (lat == gridIntLatSouth)
                        tileLatSouth = actualBounds.getMinLat();
                    else
                        tileLatSouth = lat;
                    if (lat == gridIntLatNorth)
                        tileLatNorth = actualBounds.getMaxLat();
                    else
                        tileLatNorth = lat + 1;
                    LatLon tileSouthWest = new LatLon(tileLatSouth, tileLonWest);
                    LatLon tileNorthEast = new LatLon(tileLatNorth, tileLonEast);
                    clippedTiles[gridIntLatNorth - lat][lon - gridIntLonWest] = new ClippedSRTMTile(tile, tileSouthWest,
                            tileNorthEast);
                }
            }
            for (int lon = -180; lon <= gridIntLonEast; lon++) {
                if (lon == gridIntLonWest)
                    tileLonWest = actualBounds.getMinLon();
                else
                    tileLonWest = lon;
                if (lon == gridIntLonEast)
                    tileLonEast = actualBounds.getMaxLon();
                else
                    tileLonEast = lon + 1;

                for (int lat = gridIntLatSouth; lat <= gridIntLatNorth; lat++) {
                    SRTMTile tile = elevationDataProvider.getSRTMTile(SRTMTile.getTileID(lat, lon));
                    if (lat == gridIntLatSouth)
                        tileLatSouth = actualBounds.getMinLat();
                    else
                        tileLatSouth = lat;
                    if (lat == gridIntLatNorth)
                        tileLatNorth = actualBounds.getMaxLat();
                    else
                        tileLatNorth = lat + 1;
                    LatLon tileSouthWest = new LatLon(tileLatSouth, tileLonWest);
                    LatLon tileNorthEast = new LatLon(tileLatNorth, tileLonEast);
                    clippedTiles[gridIntLatNorth - lat][179 - gridIntLonWest + lon
                            - gridIntLonEast] = new ClippedSRTMTile(tile, tileSouthWest, tileNorthEast);
                }
            }
        }
    }

    /**
     * Returns the nominal bounds.
     *
     * @return The nominal bounds.
     */
    public Bounds getNominalBounds() {
        return nominalBounds;
    }

    /**
     * Returns the actual bounds.
     *
     * @return The actual bounds which are larger than the nominal bounds.
     */
    public Bounds getActualBounds() {
        return actualBounds;
    }

    /**
     * Returns all raster coordinates and the associated elevation values within the
     * bounds.
     *
     * @return All raster coordinates and the associated elevation values within the
     *         bounds or {@code null} if not all of the SRTM tiles have the same
     *         type (i.e. different raster dimensions) or if at least one of the
     *         tiles is not valid (i.e. the data was not loaded yet or is not
     *         available at all).
     */
    public ElevationRaster getElevationRaster() {
        short[][] eleValues = getGridEleValues();
        // Avoid working on null or zero length data
        if (eleValues == null)
            return null;

        double latRange = actualBounds.getHeight();
        double lonRange = actualBounds.getWidth();
        double actualSouth = actualBounds.getMinLat();
        double actualWest = actualBounds.getMinLon();

        LatLonEle[][] latLonEleValues = new LatLonEle[eleValues.length][eleValues[0].length];
        for (int latIndex = 0; latIndex < eleValues.length; latIndex++) {
            double lat = actualSouth + latRange * (1.0 - Double.valueOf(latIndex) / (eleValues.length - 1));

            for (int lonIndex = 0; lonIndex < eleValues[latIndex].length; lonIndex++) {
                double lon = actualWest + lonRange * Double.valueOf(lonIndex) / (eleValues[latIndex].length - 1);
                short ele = eleValues[latIndex][lonIndex];
                latLonEleValues[latIndex][lonIndex] = new LatLonEle(lat, lon, ele);
            }
        }
        return new ElevationRaster(nominalBounds, actualBounds, latLonEleValues);
    }

    /**
     * Creates a buffered image with the computed hillshade ARGB values for the
     * elevation values of this SRTM tile grid.
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
    public HillshadeImageTile getHillshadeImage(double altitudeDeg, double azimuthDeg, boolean withPerimeter) {
        short[][] eleValues = getGridEleValues();
        // Avoid working on null or zero length data
        if (eleValues == null)
            return null;
        Hillshade hillshade = new Hillshade(eleValues, nominalBounds, actualBounds, altitudeDeg, azimuthDeg);
        return hillshade.getHillshadeImage(withPerimeter);
    }

    /**
     * Returns a list of isoline segments defining elevation contour lines within
     * the bounds. The segments do not have a useful order. This method will
     * slightly adjust the bounds to the closest coordinates of the elevation
     * raster.
     *
     * @param isostep Step between neighboring elevation contour lines.
     * @return A list of isoline segments defining elevation contour lines within
     *         the bounds or {@code null} if not all of the SRTM tiles have the same
     *         type (i.e. different raster dimensions) or if at least one of the
     *         tiles is not valid (i.e. the data was not loaded yet or is not
     *         available at all).
     */
    public ContourLines getContourLines(int isostep) {
        short[][] eleValues = getGridEleValues();
        // Avoid working on null or zero length data
        if (eleValues == null)
            return null;
        short minEle = eleValues[0][0];
        short maxEle = minEle;

        for (int latIndex = 0; latIndex < eleValues.length; latIndex++) {
            for (int lonIndex = 0; lonIndex < eleValues[latIndex].length; lonIndex++) {
                minEle = (short) Math.min(minEle, eleValues[latIndex][lonIndex]);
                maxEle = (short) Math.max(maxEle, eleValues[latIndex][lonIndex]);
            }
        }
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
        int nSteps = (maxIsovalue - minIsovalue) / isostep + 1;
        short[] isovalues = new short[nSteps];
        for (int i = 0; i < nSteps; i++)
            isovalues[i] = (short) (minIsovalue + i * isostep);

        MarchingSquares marchingSquares = new MarchingSquares(eleValues, actualBounds, isovalues);
        return new ContourLines(nominalBounds, actualBounds, marchingSquares.getIsolineSegments(), isostep);
    }

    /**
     * Returns the elevation values of all SRTM tiles of this grid which are located
     * within the given bounds. This method will slightly adjust the bounds to the
     * closest coordinates of the elevation raster.
     *
     * @return The elevation values which are located within the given bounds or
     *         {@code null} if not all of the SRTM tiles have the same type (i.e.
     *         different raster dimensions) or if at least one of the tiles is not
     *         valid (i.e. the data was not loaded yet or is not available at all).
     */
    private synchronized short[][] getGridEleValues() {
        if (gridEleValues != null)
            return gridEleValues;

        // Pre-check if all tiles have the preferred SRTM type and are valid
        SRTMTile.Type srtmTileType = null;
        for (int gridLatIndex = 0; gridLatIndex < clippedTiles.length; gridLatIndex++) {
            for (int gridLonIndex = 0; gridLonIndex < clippedTiles[gridLatIndex].length; gridLonIndex++) {
                ClippedSRTMTile clippedTile = clippedTiles[gridLatIndex][gridLonIndex];
                SRTMTile tile = clippedTile.tile;
                if (tile.getStatus() != SRTMTile.Status.VALID)
                    return null;
                if (srtmTileType == null)
                    srtmTileType = tile.getType();
                else if (srtmTileType != tile.getType())
                    return null;
            }
        }

        int totalTileLatLength = 0;
        int totalTileLonLength = 0;
        ArrayList<ArrayList<short[][]>> gridList = new ArrayList<>(clippedTiles.length);

        for (int gridLatIndex = 0; gridLatIndex < clippedTiles.length; gridLatIndex++) {
            ArrayList<short[][]> gridRow = new ArrayList<>(clippedTiles[gridLatIndex].length);
            boolean skipGridRow = false;

            for (int gridLonIndex = 0; gridLonIndex < clippedTiles[gridLatIndex].length; gridLonIndex++) {
                ClippedSRTMTile clippedTile = clippedTiles[gridLatIndex][gridLonIndex];
                // Retrieve the elevation values in the clipped area of the raster
                // Do not retrieve values that overlap with the adjacent tile, so we do not add
                // them twice
                short[][] tileEleValues = clippedTile.getTileEleValues(true);
                // Skip the grid row, if the clipped and cropped tile area does not contain data
                // points
                if (tileEleValues == null) {
                    skipGridRow = true;
                    break;
                }

                // While iterating through the first column, establish its total data length
                if (gridLonIndex == 0)
                    totalTileLatLength += tileEleValues.length;
                // While iterating through the first row, establish its total data length
                if (gridLatIndex == 0)
                    totalTileLonLength += tileEleValues[0].length;
                gridRow.add(tileEleValues);
            }
            if (!skipGridRow)
                gridList.add(gridRow);
        }

        // Just in case that the clipped tile areas should be so small that they contain
        // no data
        if (totalTileLatLength == 0 || totalTileLonLength == 0)
            return null;

        // 2D array for elevation raster data of all tiles in the grid
        gridEleValues = new short[totalTileLatLength][totalTileLonLength];

        // The index offset in latitude direction (row) at which to start copying
        // elevation data from the current tile into the "all tiles" array
        int allLatPos = 0;

        // Iterate through the clipped SRTM tiles of this grid
        // 1. Grid rows
        for (int gridLatIndex = 0; gridLatIndex < gridList.size(); gridLatIndex++) {
            ArrayList<short[][]> gridRow = gridList.get(gridLatIndex);

            int tileLatLength = 0;
            int allLonPos = 0;

            // 2. Grid columns
            for (int gridLonIndex = 0; gridLonIndex < gridRow.size(); gridLonIndex++) {
                // Elevation values of the current tile
                short[][] tileData = gridList.get(gridLatIndex).get(gridLonIndex);
                tileLatLength = tileData.length;

                int tileLonLength = 0;
                // Iterate through the rows of elevation data of the current tile
                // 3. Tile rows
                for (int tileLatIndex = 0; tileLatIndex < tileLatLength; tileLatIndex++) {

                    short[] src = tileData[tileLatIndex];
                    int srcPos = 0;
                    // The corresponding row in the array to be filled
                    short[] dest = gridEleValues[allLatPos + tileLatIndex];
                    int destPos = allLonPos;
                    System.arraycopy(src, srcPos, dest, destPos, src.length);
                    tileLonLength = src.length;
                }
                allLonPos += tileLonLength;
            }
            // After iterating through the tiles of a tile row, offset the index position
            // by the elevation data length in latitude direction
            allLatPos += tileLatLength;
            // Reset the index offset in longitude direction to the beginning of a row
            allLonPos = 0;
        }

        // Correct the grid bounds to the coordinates of the actual raster
        LatLon southWest = clippedTiles[clippedTiles.length - 1][0].southWest;
        LatLon northEast = clippedTiles[0][clippedTiles[0].length - 1].northEast;
        actualBounds = new Bounds(southWest.lat(), southWest.lon(), northEast.lat(), northEast.lon());

        return gridEleValues;
    }

    /**
     * Returns whether the given bounds are covered by the nominal bounds of this
     * grid.
     *
     * @param bounds The bounds for which to check if they are covered by this grid.
     * @return {@code true} if the given bounds are contained in this grid's nominal
     *         bounds, {@code false} otherwise.
     */
    public boolean covers(Bounds bounds) {
        return nominalBounds.contains(bounds);
    }

    /**
     * Helper class representing an SRTM tile which is clipped, i.e. from which only
     * that portion of the elevation values which is located within the clipping
     * bounds should be obtained.
     */
    private static class ClippedSRTMTile {
        public final SRTMTile tile;
        public LatLon southWest;
        public LatLon northEast;

        public ClippedSRTMTile(SRTMTile tile, LatLon southWest, LatLon northEast) {
            this.tile = tile;
            this.southWest = southWest;
            this.northEast = northEast;
        }

        /**
         * Returns the elevation values of the tile which are located within the defined
         * bounds.
         *
         * @param cropOverlap If {@code true}, the northern most (first) row and the
         *                    eastern most (last) column of the tile are cropped if
         *                    required. The first row and the last column of data
         *                    overlap with the adjacent tile.
         * @return The elevation values of the tile which are located within the defined
         *         bounds under consideration of the optional cropping.
         */
        public short[][] getTileEleValues(boolean cropOverlap) {
            int[] indicesSouthWest = tile.getIndices(southWest);
            int[] indicesNorthEast = tile.getIndices(northEast);
            int indexLatSouth = indicesSouthWest[0];
            int indexLonWest = indicesSouthWest[1];
            int indexLatNorth = indicesNorthEast[0];
            int indexLonEast = indicesNorthEast[1];

            if (cropOverlap) {
                int tileLength = tile.getTileLength();
                if (indexLatNorth == 0)
                    indexLatNorth = 1;
                if (indexLatSouth < indexLatNorth)
                    return null;
                if (indexLonEast == tileLength - 1)
                    indexLonEast = tileLength - 2;
                if (indexLonEast < indexLonWest)
                    return null;
            }
            // Update the clipping bounds to the actual raster coordinates
            southWest = tile.getRasterLatLon(indexLatSouth, indexLonWest);
            northEast = tile.getRasterLatLon(indexLatNorth, indexLonEast);
            return tile.getEleValues(indexLatSouth, indexLonWest, indexLatNorth, indexLonEast);
        }
    }
}
