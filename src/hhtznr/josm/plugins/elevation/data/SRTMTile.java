package hhtznr.josm.plugins.elevation.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;

import hhtznr.josm.plugins.elevation.math.BiLinearInterpolation;

/**
 * Class {@code SRTMTile} provides an in-memory representation of elevation data
 * from SRTM files and methods to work with the data.
 *
 * <b>SRTM tiles</b>
 *
 * Source: https://lpdaac.usgs.gov/documents/179/SRTM_User_Guide_V3.pdf
 *
 * The names of individual data tiles refer to the latitude and longitude of the
 * south west (lower left) corner of the tile. For example, N37W105 has its
 * lower left corner at 37°N latitude and 105°W longitude and covers (slightly
 * more than) the area 37-38°N and 104-105°W. To be more exact, the file name
 * coordinates refer to the geometric center of the lower-left pixel, and all
 * edge pixels of the tile are centered on whole-degree lines of latitude and/or
 * longitude. The unit of elevation is meters as referenced to the WGS84/EGM96
 * geoid (NGA, 1997; Lemoine, 1998).
 *
 * SRTM1 data are sampled at 1 arc-second of latitude and longitude and each
 * file contains 3,601 lines and 3,601 samples. The rows at the north and south
 * edges, as well as the columns at the east and west edges of each tile,
 * overlap, and are identical to, the edge rows and columns in the adjacent
 * tile. SRTM3 data are sampled at 3 arc-seconds and contain 1,201 lines and
 * 1,201 samples with similar overlapping rows and columns.
 *
 * The data are in "geographic " projection (also known as Equirectangular or
 * Plate Carrée), which means the data is presented with respectively equal
 * intervals of latitude and longitude in the vertical and horizontal
 * dimensions. More technically, the projection maps meridians to vertical
 * straight lines of constant spacing, and circles of latitude (“parallels”) to
 * horizontal straight lines of constant spacing. This might be thought of as no
 * projection at all, but simply a latitude-longitude data array.
 *
 * [...]
 *
 * Voids in Versions 1.0 and 2.1 are flagged with the value -32,768. There are
 * no voids in Version 3.0.
 *
 * <b>Geographic latitude and longitude</b>
 *
 * Source: https://en.wikipedia.org/wiki/Decimal_degrees
 *
 * Positive latitudes are north of the equator, negative latitudes are south of
 * the equator. Positive longitudes are east of the Prime Meridian; negative
 * longitudes are west of the Prime Meridian. Latitude and longitude are usually
 * expressed in that sequence, latitude before longitude.
 *
 * @author Harald Hetzner
 */
public class SRTMTile {

    /**
     * Value indicating voids in SRTM data. It would only be needed for version 1.0
     * and 2.1 since there are no voids in version 3.0 data. However, it is also
     * used to indicate missing data if elevation data cannot be obtained for other
     * reasons.
     *
     * See https://lpdaac.usgs.gov/documents/179/SRTM_User_Guide_V3.pdf
     */
    public static final short SRTM_DATA_VOID = -32768;

    /**
     * The number of rows and columns of elevation data points in an SRTM1 file.
     *
     * See https://lpdaac.usgs.gov/documents/179/SRTM_User_Guide_V3.pdf
     */
    public static final int SRTM1_TILE_LENGTH = 3601;

    /**
     * The number of rows and columns of elevation data points in an SRTM3 file.
     *
     * See https://lpdaac.usgs.gov/documents/179/SRTM_User_Guide_V3.pdf
     */
    public static final int SRTM3_TILE_LENGTH = 1201;

    /**
     * Value indicating that the data point length of the tile is not known due to
     * inappropriate information on the SRTM type.
     */
    public static final int INVALID_TILE_LENGTH = -1;

    /**
     * The geographic vertical and horizontal dimensions of an SRTM tile: 1°.
     */
    public static final double SRTM_TILE_ARC_DEGREES = 1.0;

    /**
     * Regular expression pattern for matching the components of an SRTM tile ID
     * denoting the latitude and longitude of its south west corner.
     */
    public static final Pattern SRTM_TILE_ID_PATTERN = Pattern.compile("^(([NS])(\\d{2})([EW])(\\d{3}))");

    private final String id;
    private final int idLat;
    private final int idLon;
    private Type type;
    private short[][] elevationData;
    private Status status;
    private long accessTime;

    /**
     * SRTM file types (SRTM1, SRTM3).
     */
    public static enum Type {
        /**
         * SRTM1: elevation sampled at 1 arc-second
         */
        SRTM1("SRTM1"),

        /**
         * SRTM3: elevation sampled at 3 arc-seconds
         */
        SRTM3("SRTM3"),

        /**
         * Dummy type to be used as long as the actual SRTM type is not known.
         */
        UNKNOWN("unknown");

        private final String typeName;

        Type(String typeName) {
            this.typeName = typeName;
        }

        /**
         * Returns the name associated with this SRTM type.
         *
         * @return The name of the type.
         */
        @Override
        public String toString() {
            return typeName;
        }

        /**
         * Returns the SRTM type associated with a given name.
         *
         * @param name The name associated with the SRTM type.
         * @return The SRTM type associated with the name or {@code SRTM1} otherwise.
         */
        public static Type fromString(String name) {
            for (Type type : Type.values()) {
                if (type.toString().equals(name))
                    return type;
            }
            return SRTM1;
        }
    }

    /**
     * Status of SRTM tiles (loading, valid, missing, download scheduled,
     * downloading, download failed).
     */
    public static enum Status {
        /**
         * Status indicating that reading of the SRTM tile from disk has been scheduled.
         */
        READING_SCHEDULED("reading scheduled"),

        /**
         * Status indicating that the SRTM tile is currently being read from disk.
         */
        READING("reading"),

        /**
         * Status indicating that the SRTM tile holds valid elevation data.
         */
        VALID("valid"),

        /**
         * Status indicating that the SRTM file that should contain the elevation data
         * of the SRTM tile was considered for reading, but was found to be invalid.
         */
        FILE_INVALID("file invalid"),

        /**
         * Status indicating that the SRTM file that should contain the elevation data
         * of the SRTM tile is missing on disk and cannot be obtained (auto-download
         * disabled).
         */
        FILE_MISSING("file missing"),

        /**
         * Status indicating that downloading of the SRTM tile has been scheduled.
         */
        DOWNLOAD_SCHEDULED("download scheduled"),

        /**
         * Status indicating that the SRTM tile is currently being downloaded from the
         * SRTM server.
         */
        DOWNLOADING("downloading"),

        /**
         * Status indicating that downloading the SRTM tile failed.
         */
        DOWNLOAD_FAILED("download failed");

        private final String statusName;

        Status(String statusName) {
            this.statusName = statusName;
        }

        /**
         * Returns the name associated with this SRTM status.
         *
         * @return The name of the status.
         */
        @Override
        public String toString() {
            return statusName;
        }
    }

    /**
     * Type of elevation data interpolation.
     */
    public static enum Interpolation {
        /**
         * No interpolation.
         */
        NONE("None"),

        /**
         * Bilinear interpolation
         */
        BILINEAR("Bilinear");

        private final String typeName;

        Interpolation(String typeName) {
            this.typeName = typeName;
        }

        /**
         * Returns the name associated with this interpolation type.
         *
         * @return The name of the type.
         */
        @Override
        public String toString() {
            return typeName;
        }

        /**
         * Returns the interpolation type associated with a given name.
         *
         * @param name The name associated with the interpolation type.
         * @return The interpolation type associated with the name or {@code NONE}
         *         otherwise.
         */
        public static Interpolation fromString(String name) {
            for (Interpolation type : Interpolation.values()) {
                if (type.toString().equals(name))
                    return type;
            }
            return NONE;
        }
    }

    /**
     * Creates a new SRTM tile.
     *
     * @param id            The ID of the SRTM tile.
     * @param type          The type of the SRTM tile.
     * @param elevationData The elevation data points or {@code null} if no data is
     *                      available. The 1st dimension of the array of arrays is
     *                      the longitude ("x"), the 2nd dimension is the latitude
     *                      ("y").
     * @param status        The current status of the SRTM tile.
     */
    public SRTMTile(String id, Type type, short[][] elevationData, Status status) {
        this.id = id;
        int[] latLon = parseLatLonFromTileID(id);
        idLat = latLon[0];
        idLon = latLon[1];
        update(type, elevationData, status);
    }

    /**
     * Returns the tile ID.
     *
     * @return The tile ID.
     */
    public String getID() {
        return id;
    }

    /**
     * Returns the signed integer latitude of the tile ID.
     *
     * @return The latitude of the tile ID.
     */
    public int getLatID() {
        return idLat;
    }

    /**
     * Returns the signed integer longitude of the tile ID.
     *
     * @return The longitude of the tile ID.
     */
    public int getLonId() {
        return idLon;
    }

    /**
     * Returns the tile type.
     *
     * @return The type of the tile.
     */
    public synchronized Type getType() {
        return type;
    }

    /**
     * Returns the tile status.
     *
     * @return The status of the tile.
     */
    public synchronized Status getStatus() {
        return status;
    }

    /**
     * Returns the elevation data of this tile.
     *
     * @return The elevation data of this tile or {@code null} if the tile does not
     *         contain data.
     */
    protected synchronized short[][] getElevationData() {
        return elevationData;
    }

    /**
     * Updates type, data and status of this SRTM tile.
     *
     * @param type          The type of the SRTM tile.
     * @param elevationData The elevation data points or {@code null} if no data is
     *                      available. The 1st dimension of the array of arrays is
     *                      the longitude ("x"), the 2nd dimension is the latitude
     *                      ("y").
     * @param status        The current status of the SRTM tile.
     */
    public synchronized void update(Type type, short[][] elevationData, Status status) {
        this.type = type;
        this.elevationData = elevationData;
        this.status = status;
        this.accessTime = System.currentTimeMillis();
    }

    /**
     * Returns the time of last attempted access to the data of this SRTM tile.
     *
     * @return The access time stamp.
     */
    public synchronized long getAccessTime() {
        return accessTime;
    }

    /**
     * Returns the size of the tile's elevation data.
     *
     * @return Size of the elevation data in bytes.
     */
    public synchronized int getDataSize() {
        if (elevationData == null)
            return 0;
        // Data size in bytes
        return elevationData.length * elevationData[0].length * 2;
    }

    /**
     * Returns the number of data points in latitude respectively longitude
     * dimension of the tile.
     *
     * @return The number of data points in one tile dimension or
     *         {@code INVALID_TILE_LENGTH} if the SRTM type is inappropriate.
     */
    public synchronized int getTileLength() {
        if (type == Type.SRTM1)
            return SRTM1_TILE_LENGTH;
        if (type == Type.SRTM3)
            return SRTM3_TILE_LENGTH;
        return INVALID_TILE_LENGTH;
    }

    /**
     * Returns an interpolated elevation value ({@link Interpolation#BILINEAR
     * Interplation.BILINEAR}}) or the elevation value of the tile raster location
     * that is closest to the given location ({@link Interpolation#NONE
     * Interplation.BILINEAR}}) along with the coordinate the corresponds to the
     * interpolation coordinate or the raster coordinate where the elevation value
     * was obtained.
     *
     * <b>Data order of an uncompressed SRTM file</b>
     *
     * Source: https://lpdaac.usgs.gov/documents/179/SRTM_User_Guide_V3.pdf
     *
     * <i>The data are stored in row major order, meaning all the data for the
     * northernmost row, row 1, are followed by all the data for row 2, and so
     * on.</i>
     *
     * <b>Latitude and longitude</b>
     *
     * Source: https://en.wikipedia.org/wiki/Decimal_degrees
     *
     * Positive latitudes are north of the equator, negative latitudes are south of
     * the equator. Positive longitudes are east of the Prime Meridian; negative
     * longitudes are west of the Prime Meridian.
     *
     * <b>Schematic for four "symmetrical" tiles in quadrants defined by Equator and
     * Prime Meridian</b>
     *
     * <pre>{@code
     *                 Prime Meridian
     *            Q2        lat         Q1
     *                       ^
     *  ->N04W008---N04W007  | ->N04E007---N04E008
     *    |               |  |   |               |
     *    |   N3+ / W7+   |  |   |   N3+ / E7+   |
     *    |               |  |   |               |
     *   *N03W008---N03W007  |  *N03E007---N03E008
     *                       |
     *  <--------------------+---------------------> lon / Equator
     *                       |
     *  ->S03W008---S03W007  | ->S03E007---S03E008
     *    |               |  |   |               |
     *    |   S3+ / W7+   |  |   |   S3+ / E7+   |
     *    |               |  |   |               |
     *   *S04W008---S04W007  |  *S04E007---S04E008
     *                       v
     *           Q3                     Q4
     *
     *   -> 1st data row: northernmost row
     *    * tile name: south west (lower left) corner
     * }</pre>
     *
     * @param latLon        The coordinate for which to obtain the elevation.
     * @param interpolation The type of interpolation to use.
     * @return The elevation for the given location or {@code SRTM_DATA_VOID} if no
     *         data is available.
     */
    public synchronized LatLonEle getLatLonEle(ILatLon latLon, Interpolation interpolation) {
        // Update the access time
        accessTime = System.currentTimeMillis();
        int tileLength = getTileLength();
        if (status != Status.VALID || elevationData == null || tileLength == INVALID_TILE_LENGTH)
            return new LatLonEle(latLon, LatLonEle.NO_VALID_ELEVATION);

        // Determine the array index at which to retrieve the elevation at the given
        // location
        double lat = latLon.lat();
        double lon = latLon.lon();
        // Accept idLat <= lat < idLat + 1
        // For idLat + 1 next tile to the north is used
        if (lat < idLat || lat > idLat + 1)
            throw new IllegalArgumentException("Given latitude " + lat
                    + " is not within latitude range of SRTM tile from " + idLat + " to " + (idLat + 1));
        // Accept idLon <= lon < idLon + 1
        // For idLon + 1 next tile to the east is used
        if (lon < idLon || lon > idLon + 1)
            throw new IllegalArgumentException("Given longitude " + lon
                    + " is not within longitude range of SRTM tile from " + idLon + " to " + (idLon + 1));

        double latEleIndexD = (1.0 - (lat - idLat)) * (tileLength - 1);
        double lonEleIndexD = (lon - idLon) * (tileLength - 1);
        int latEleIndex = (int) Math.round(latEleIndexD);
        int lonEleIndex = (int) Math.round(lonEleIndexD);

        // Compute the tile raster coordinates of the elevation value
        if (interpolation == Interpolation.NONE) {
            short srtmEle = elevationData[latEleIndex][lonEleIndex];
            double ele;
            if (srtmEle == SRTM_DATA_VOID)
                ele = LatLonEle.NO_VALID_ELEVATION;
            else
                ele = srtmEle;
            return new LatLonEle(getRasterLatLon(latEleIndex, lonEleIndex), ele);
        }

        // Compute 4 indices of the raster cell the given coordinate is located in
        int indexSouth;
        int indexNorth;
        int indexWest;
        int indexEast;

        if (latEleIndex < latEleIndexD) {
            indexNorth = latEleIndex;
            indexSouth = latEleIndex + 1;
        } else {
            indexNorth = latEleIndex - 1;
            indexSouth = latEleIndex;
        }

        if (lonEleIndex <= lonEleIndexD) {
            indexWest = lonEleIndex;
            indexEast = lonEleIndex + 1;
        } else {
            indexWest = lonEleIndex - 1;
            indexEast = lonEleIndex;
        }

        LatLonEle northWest = new LatLonEle(getRasterLatLon(indexNorth, indexWest),
                elevationData[indexNorth][indexWest]);
        LatLonEle northEast = new LatLonEle(getRasterLatLon(indexNorth, indexEast),
                elevationData[indexNorth][indexEast]);
        LatLonEle southWest = new LatLonEle(getRasterLatLon(indexSouth, indexWest),
                elevationData[indexSouth][indexWest]);
        LatLonEle southEast = new LatLonEle(getRasterLatLon(indexSouth, indexEast),
                elevationData[indexSouth][indexEast]);

        double ele = BiLinearInterpolation.interpolate(southWest, northWest, southEast, northEast, latLon);
        return new LatLonEle(lat, lon, ele);
    }

    /**
     * Returns all elevation values from the tile raster which are within the given
     * indices. The values at the raster location of the indices are included.
     *
     * @param indexLatSouth The index of the southern most elevation value.
     *                      Constraint: {@code indexLatSouth >= indexLatNorth}.
     * @param indexLonWest  The index of the western most elevation value.
     * @param indexLatNorth The index of the northern most elevation value.
     * @param indexLonEast  The index of the eastern most elevation value.
     *                      Constraint: {@code indexLonEast >= indexLonWest}.
     * @return A 2D array "{@code short[latitude][longitude}}" containing the
     *         elevation values within the specified index bounds.
     */
    protected synchronized short[][] getEleValues(int indexLatSouth, int indexLonWest, int indexLatNorth,
            int indexLonEast) {
        if (indexLonWest > indexLonEast)
            throw new IllegalArgumentException("Index of western longitude (" + indexLonWest
                    + ") may not be greater than index of eastern longitude (" + indexLonEast + ")");
        if (indexLatNorth > indexLatSouth)
            throw new IllegalArgumentException("Index of northern latitude (" + indexLatNorth
                    + ") may not be greater than index of southern latitude value (" + indexLatSouth + ")");
        // Update the access time
        accessTime = System.currentTimeMillis();
        int tileLength = getTileLength();
        if (status != Status.VALID || elevationData == null || tileLength == INVALID_TILE_LENGTH)
            return null;

        short[][] elevationData = new short[indexLatSouth - indexLatNorth + 1][indexLonEast - indexLonWest + 1];
        for (int indexLat = indexLatNorth; indexLat <= indexLatSouth; indexLat++) {
            short[] src = this.elevationData[indexLat];
            int srcPos = indexLonWest;
            // The corresponding row in the array to be filled
            short[] dest = elevationData[indexLat - indexLatNorth];
            int destPos = 0;
            int length = indexLonEast - indexLonWest + 1;
            System.arraycopy(src, srcPos, dest, destPos, length);
        }
        return elevationData;
    }

    /**
     * Computes the coordinate of a tile raster location specified by its indices.
     *
     * @param latIndex The index in latitude dimension.
     * @param lonIndex The index in longitude dimension.
     * @return The latitude-longitude coordinate that corresponds with the given
     *         raster indices.
     */
    public LatLon getRasterLatLon(int latIndex, int lonIndex) {
        int tileLength = getTileLength();
        // Compute the tile raster coordinates of the elevation value
        // Signed latitude increases in opposite direction of data order
        double latEleRaster = idLat + (1.0 - Double.valueOf(latIndex) / (tileLength - 1));
        // Signed longitude increases in same direction as data order
        double lonEleRaster = idLon + Double.valueOf(lonIndex) / (tileLength - 1);

        return new LatLon(latEleRaster, lonEleRaster);
    }

    /**
     * Returns the indices of the elevation value at the tile raster coordinate that
     * is closest to the given coordinate.
     *
     * @param latLon The coordinate for which to determine the indices of the
     *               elevation value at the closest raster position.
     * @return An array of {@code length = 2} which contains the indices of the
     *         elevation value of interest. The value's index in latitude dimension
     *         is stored at array index {@code 0}. The value's index in longitude
     *         dimension is stored at array index {@code 1}.
     */
    protected int[] getIndices(ILatLon latLon) {
        // Determine the array index at which to retrieve the elevation at the given
        // location
        double lat = latLon.lat();
        double lon = latLon.lon();
        // Accept idLat <= lat <= idLat + 1
        // For idLat + 1 next tile to the north is used
        if (lat < idLat || lat > idLat + 1)
            throw new IllegalArgumentException("Given latitude " + lat
                    + " is not within latitude range of SRTM tile from " + idLat + " to " + (idLat + 1));
        // Accept idLon <= lon <= idLon + 1
        // For idLon + 1 next tile to the east is used
        if (lon < idLon || lon > idLon + 1)
            throw new IllegalArgumentException("Given longitude " + lon
                    + " is not within longitude range of SRTM tile from " + idLon + " to " + (idLon + 1));

        int tileLength = getTileLength();
        // Map lat and lon decimal parts = [0, 1] to lat and lon indices = [0,
        // tileLength - 1]
        int latEleIndex = (int) Math.round((1.0 - (lat - idLat)) * (tileLength - 1));
        int lonEleIndex = (int) Math.round((lon - idLon) * (tileLength - 1));
        return new int[] { latEleIndex, lonEleIndex };
    }

    /**
     * Returns the appropriate SRTM tile ID for the given coordinate. The format of
     * the ID is <code>[N|S]nn[W|E]mmm</code> where <code>nn</code> is the integral
     * latitude without decimal places and <code>mmm</code> is the integral
     * longitude of the south west (lower left) corner of the tile.
     *
     * @param latLon The coordinate to get the SRTM tile ID for.
     * @return The ID of the SRTM tile.
     */
    public static String getTileID(ILatLon latLon) {
        // Integer part of latitude and longitude
        // https://stackoverflow.com/questions/54629033/cast-to-int-vs-math-floor
        int integerLat = (int) Math.floor(latLon.lat());
        int integerLon = (int) Math.floor(latLon.lon());

        return getTileID(integerLat, integerLon);
    }

    /**
     * Returns the SRTM tile ID that corresponds to the given integer coordinates
     * (full degrees) that denote the south west (lower left) corner of the SRTM
     * tile.
     *
     * @param lat The integer latitude of the lower left corner of the SRTM tile.
     * @param lon The integer longitude of the lower left corner of the SRTM tile.
     * @return The ID of the SRTM tile.
     */
    public static String getTileID(int lat, int lon) {
        String latPrefix = "N";
        // Negative latitudes are south of the equator
        if (lat < 0) {
            latPrefix = "S";
            lat = -lat;
        }

        String lonPrefix = "E";
        // Negative longitudes are west of the Prime Meridian
        if (lon < 0) {
            lonPrefix = "W";
            lon = -lon;
        }

        return String.format("%s%02d%s%03d", latPrefix, lat, lonPrefix, lon);
    }

    /**
     * Parses the latitude and longitude values from an SRTM tile ID.
     *
     * @param tileID The SRTM tile ID to parse.
     * @return An array of length 2 with the latitude at index 0 and the longitude
     *         at index 1.
     */
    public static int[] parseLatLonFromTileID(String tileID) {
        Matcher matcher = SRTM_TILE_ID_PATTERN.matcher(tileID);
        if (!matcher.matches())
            throw new IllegalArgumentException("SRTM tile ID '" + tileID + "' is malformed");
        String latPrefix = matcher.group(2);
        int lat = Integer.parseInt(matcher.group(3));
        String lonPrefix = matcher.group(4);
        int lon = Integer.parseInt(matcher.group(5));
        if (latPrefix.equals("S"))
            lat = -lat;
        if (lonPrefix.equals("W"))
            lon = -lon;
        if (lat >= 90 || lat < -90 || lon >= 180 || lon < -180)
            throw new IllegalArgumentException("Latitude or longitude of SRTM tile ID '" + tileID
                    + "' are outside bounds -90 <= lat < 90, -180 <= lon < 180.");
        return new int[] { lat, lon };
    }
}
