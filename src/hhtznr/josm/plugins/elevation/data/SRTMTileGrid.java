package hhtznr.josm.plugins.elevation.data;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;

import hhtznr.josm.plugins.elevation.io.SRTMFileReader;
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

    /**
     * The step in between two neighboring elevation contour lines.
     */
    public static final short ISO_STEP = 10;

    private LatLon southWest;
    private LatLon northEast;

    private ClippedSRTMTile[][] clippedTiles;

    /**
     * Creates a new 2D grid of SRTM tiles to cover the given latitude-longitude
     * bound with elevation data.
     *
     * @param bounds The bounds in latitude-longitude coordinate space.
     */
    public SRTMTileGrid(Bounds bounds) {
        SRTMTile.Type preferredSRTMType = SRTMFileReader.getInstance().getPreferredSRTMType();
        // TODO: More elegant solution
        double latLonIncr;
        if (preferredSRTMType == SRTMTile.Type.SRTM1)
            latLonIncr = SRTMTile.SRTM_TILE_ARC_DEGREES / (SRTMTile.SRTM1_TILE_LENGTH - 1);
        else
            latLonIncr = SRTMTile.SRTM_TILE_ARC_DEGREES / (SRTMTile.SRTM3_TILE_LENGTH - 1);

        // Increase the bound by one raster increment, but not more as the coordinate
        // range
        // This will ensure that computed contour lines actually cover the bounds
        double latMin = Math.max(bounds.getMinLat() - latLonIncr, -90.0);
        double latMax = Math.min(bounds.getMaxLat() + latLonIncr, 90.0);
        double lonMin = Math.max(bounds.getMinLon() - latLonIncr, -180.0);
        ;
        double lonMax = Math.min(bounds.getMaxLon() + latLonIncr, 180.0);

        // Determine south west and north east corner coordinate from bounds
        int gridIntLatSouth = (int) Math.floor(latMin);
        int gridIntLatNorth = (int) Math.floor(latMax);
        int gridIntLonWest;
        int gridIntLonEast;
        // Not across the Prime Meridian
        if (lonMin <= lonMax) {
            gridIntLonWest = (int) Math.floor(lonMin);
            gridIntLonEast = (int) Math.floor(lonMax);
            southWest = new LatLon(latMin, lonMin);
            northEast = new LatLon(latMax, lonMax);
        }
        // Across the Prime Meridian (+/-180 °C)
        else {
            gridIntLonWest = (int) Math.floor(lonMax);
            gridIntLonEast = (int) Math.floor(lonMin);
            southWest = new LatLon(latMin, lonMax);
            northEast = new LatLon(latMax, lonMin);
        }

        // Trigger needed tiles being cached, if they are not cached yet
        SRTMFileReader srtmFileReader = SRTMFileReader.getInstance();
        srtmFileReader.cacheSRTMTiles(gridIntLatSouth, gridIntLonWest, gridIntLatNorth, gridIntLonEast);

        // Create an array, which stores the clipped SRTM tiles covering the bounds
        clippedTiles = new ClippedSRTMTile[gridIntLatNorth - gridIntLatSouth + 1][gridIntLonEast - gridIntLonWest + 1];

        double tileLatSouth;
        double tileLonWest;
        double tileLatNorth;
        double tileLonEast;

        // Fill the 2D array with clipped SRTM tiles
        // Not across the Prime Meridian
        if (gridIntLonWest <= gridIntLonEast) {
            for (int gridLon = gridIntLonWest; gridLon <= gridIntLonEast; gridLon++) {
                // For the most western tile, its western edge needs to be clipped
                if (gridLon == gridIntLonWest)
                    tileLonWest = southWest.lon();
                // If the tile is not the most western tile, its western edge does not need to
                // be clipped
                else
                    tileLonWest = gridLon;
                // For the most eastern tile, its eastern edge needs to be clipped
                if (gridLon == gridIntLonEast)
                    tileLonEast = northEast.lon();
                // If the tile is not the most eastern tile, its eastern edge does not need to
                // be clipped
                else
                    tileLonEast = gridLon + 1;

                for (int gridLat = gridIntLatSouth; gridLat <= gridIntLatNorth; gridLat++) {
                    // Calling the getter method will ensure that tiles are being read or downloaded
                    //System.out.println("SRMTTileGrid(): Get SRTM tile for lat = " + gridLat + ", lon = " + gridLon);
                    SRTMTile tile = srtmFileReader.getSRTMTile(SRTMTile.getTileID(gridLat, gridLon));
                    // For the most southern tile, its southern edge needs to be clipped
                    if (gridLat == gridIntLatSouth)
                        tileLatSouth = southWest.lat();
                    // If the tile is not the most southern tile, its southern edge does not need to
                    // be clipped
                    else
                        tileLatSouth = gridLat;
                    // For the most northern tile, its northern edge needs to be clipped
                    if (gridLat == gridIntLatNorth)
                        tileLatNorth = northEast.lat();
                    // If the tile is not the most northern tile, its northern edge does not need to
                    // be clipped
                    else
                        tileLatNorth = gridLat + 1;
                    LatLon tileSouthWest = new LatLon(tileLatSouth, tileLonWest);
                    LatLon tileNorthEast = new LatLon(tileLatNorth, tileLonEast);
                    //System.out.println("SRMTTileGrid(): Clip SRTM tile for lat = " + gridLat + ", lon = " + gridLon
                    //        + ": S = " + tileLatSouth + ", N = " + tileLatNorth + ", W = " + tileLonWest + ", E = "
                    //        + tileLonEast);
                    clippedTiles[gridIntLatNorth - gridLat][gridLon - gridIntLonWest] = new ClippedSRTMTile(tile,
                            tileSouthWest, tileNorthEast);

                }
            }
        }
        // Across the Prime Meridian (+/-180 °C)
        else {
            for (int lon = gridIntLonWest; lon <= 179; lon++) {
                if (lon == gridIntLonWest)
                    tileLonWest = southWest.lon();
                else
                    tileLonWest = lon;
                if (lon == gridIntLonEast)
                    tileLonEast = northEast.lon();
                else
                    tileLonEast = lon + 1;

                for (int lat = gridIntLatSouth; lat <= gridIntLatNorth; lat++) {
                    SRTMTile tile = srtmFileReader.getSRTMTile(SRTMTile.getTileID(lat, lon));
                    if (lat == gridIntLatSouth)
                        tileLatSouth = southWest.lat();
                    else
                        tileLatSouth = lat;
                    if (lat == gridIntLatNorth)
                        tileLatNorth = northEast.lat();
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
                    tileLonWest = southWest.lon();
                else
                    tileLonWest = lon;
                if (lon == gridIntLonEast)
                    tileLonEast = northEast.lon();
                else
                    tileLonEast = lon + 1;

                for (int lat = gridIntLatSouth; lat <= gridIntLatNorth; lat++) {
                    SRTMTile tile = srtmFileReader.getSRTMTile(SRTMTile.getTileID(lat, lon));
                    if (lat == gridIntLatSouth)
                        tileLatSouth = southWest.lat();
                    else
                        tileLatSouth = lat;
                    if (lat == gridIntLatNorth)
                        tileLatNorth = northEast.lat();
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
     * Returns the south west coordinate of the bounds.
     *
     * @return The south west (lower left) coordinate of the bounds.
     */
    public LatLon getSouthWest() {
        return southWest;
    }

    /**
     * Returns the north east coordinate of the bounds.
     *
     * @return The north east (upper right) coordinate of the bounds.
     */
    public LatLon getNorthEast() {
        return northEast;
    }

    /**
     * Returns a list of all raster coordinates and the associated elevation values
     * within the bounds. This method will slightly adjust the bounds to the closest
     * coordinates of the elevation raster.
     *
     * @return a list of all raster coordinates and the associated elevation values
     *         within the bounds or an empty list if not all of the SRTM tiles have
     *         the same type (i.e. different raster dimensions) or if at least one
     *         of the tiles is not valid (i.e. the data was not loaded yet or is not
     *         available at all).
     */
    public List<LatLonEle> getLatLonEleList() {
        short[][] eleValues = getGridEleValues();
        // Avoid working on null or zero length data
        if (eleValues == null)
            return new ArrayList<>(0);

        double latRange = northEast.lat() - southWest.lat();
        double lonRange = northEast.lon() - southWest.lon();

        ArrayList<LatLonEle> list = new ArrayList<>();
        for (int latIndex = 0; latIndex < eleValues.length; latIndex++) {
            double lat = southWest.lat() + latRange * (1.0 - Double.valueOf(latIndex) / (eleValues.length - 1));

            for (int lonIndex = 0; lonIndex < eleValues[latIndex].length; lonIndex++) {
                double lon = southWest.lon() + lonRange * Double.valueOf(lonIndex) / (eleValues[latIndex].length - 1);
                short ele = eleValues[latIndex][lonIndex];
                list.add(new LatLonEle(lat, lon, ele));
            }
        }
        return list;
    }

    /**
     * Returns a list of isoline segments defining elevation contour lines within
     * the bounds. The segments do not have a useful order. This method will
     * slightly adjust the bounds to the closest coordinates of the elevation
     * raster.
     *
     * @return A list of isoline segments defining elevation contour lines within
     *         the bounds.or {@code null} if not all of the SRTM tiles have the same
     *         type (i.e. different raster dimensions) or if at least one of the
     *         tiles is not valid (i.e. the data was not loaded yet or is not
     *         available at all).
     */
    public List<LatLonLine> getIsolineSegments() {
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
        if (minEle % ISO_STEP == 0)
            minIsovalue = minEle;
        else
            minIsovalue = (short) ((minEle / ISO_STEP) * ISO_STEP);
        if (maxEle % ISO_STEP == 0)
            maxIsovalue = maxEle;
        else
            maxIsovalue = (short) ((maxEle / ISO_STEP) * ISO_STEP);
        int nSteps = (maxIsovalue - minIsovalue) / ISO_STEP + 1;
        short[] isovalues = new short[nSteps];
        for (int i = 0; i < nSteps; i++)
            isovalues[i] = (short) (minIsovalue + i * ISO_STEP);

        MarchingSquares marchingSquares = new MarchingSquares(eleValues, southWest, northEast, isovalues);
        return marchingSquares.getIsolineSegments();
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
    private short[][] getGridEleValues() {
        //System.out.println("SRTMTileGrid.getGridEleValues(): Given bounds: S = " + southWest.lat() + ", N = "
        //        + northEast.lat() + ", W = " + southWest.lon() + ", E = " + northEast.lon());
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
                // System.out.println("SRTMTileGrid.getGridEleValues(): Storing ele values of
                // tile " + clippedTile.tile.getID() + " at grid indices lat/lon = " +
                // gridLatIndex + "/" + gridLonIndex);
                // Retrieve the elevation values in the clipped area of the raster
                // Do not retrieve values that overlap with the adjacent tile, so we do not add
                // them twice
                short[][] tileEleValues = clippedTile.getTileEleValues(true);
                // Skip the grid row, if the clipped and cropped tile area does not contain data
                // points
                if (tileEleValues == null) {
                    // System.out.println("SRTMTileGrid.getGridEleValues(): Skipping empty grid row
                    // with lat index = " + gridLatIndex + " (lon index = " + gridLonIndex + ")");
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
        short[][] allEleValues = new short[totalTileLatLength][totalTileLonLength];

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
                    short[] dest = allEleValues[allLatPos + tileLatIndex];
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
        southWest = clippedTiles[clippedTiles.length - 1][0].southWest;
        northEast = clippedTiles[0][clippedTiles[0].length - 1].northEast;
        //System.out.println("SRTMTileGrid.getGridEleValues(): Raster bounds: S = " + southWest.lat() + ", N = "
        //        + northEast.lat() + ", W = " + southWest.lon() + ", E = " + northEast.lon());

        return allEleValues;
    }

    /**
     * Returns whether the given bounds are covered by this grid.
     *
     * @param bounds The bounds for which to check if they are covered by this grid.
     * @return {@code true} if the given bounds are contained in this grid's bounds,
     *         {@code false} otherwise.
     */
    public boolean covers(Bounds bounds) {
        return new Bounds(southWest, northEast).contains(bounds);
    }

    /**
     * Helper class representing an SRTM tile which is clipped, i.e. from which only
     * that portion of the elevation values which is located within the clipping
     * bounds should be obtained.
     */
    private static class ClippedSRTMTile {
        public SRTMTile tile;
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
            //System.out.println(
            //        "SRTMTileGrid.getTileEleValues(): For tile " + tile.getID() + ": Bounds: S = " + southWest.lat()
            //                + ", N = " + northEast.lat() + ", W = " + southWest.lon() + ", E = " + northEast.lon());
            //System.out.println("SRTMTileGrid.getTileEleValues(): For tile " + tile.getID() + ": Indices: S = "
            //        + indexLatSouth + ", N = " + indexLatNorth + ", W = " + indexLonWest + ", E = " + indexLonEast);
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
