package hhtznr.josm.plugins.elevation;

import java.math.BigDecimal;

import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.tools.Logging;

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
    public static final int SRTM_DATA_VOID = -32768;

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
    public static int INVALID_TILE_LENGTH = -1;

    /**
     * The geographic vertical and horizontal dimensions of an SRTM tile: 1°.
     */
    public static double SRTM_TILE_ARC_DEGREES = 1.0;

    private final String id;
    private Type type;
    private short[][] elevationData;
    private Status status;
    private long accessTime;

    /**
     * SRTM file types (SRTM1, SRTM3).
     */
    public enum Type {
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
        public String getName() {
            return typeName;
        }

        /**
         * Returns the SRTM type associated with a given name.
         *
         * @param name The name associated with the SRTM type.
         * @return The SRTM type associated with the name or {@code SRTM1} otherwise.
         */
        public static Type fromName(String name) {
            for (Type type : Type.values()) {
                if (type.getName().equals(name))
                    return type;
            }
            return SRTM1;
        }
    }

    /**
     * Status of SRTM tiles (loading, valid, missing, download scheduled,
     * downloading, download failed).
     */
    public enum Status {
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
        public String getName() {
            return statusName;
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
     * Returns the tile status.
     *
     * @return The status of the tile.
     */
    public synchronized Status getStatus() {
        return status;
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
     * Returns the elevation value of the tile raster location that is closest to
     * the given location.
     *
     * @param latLon The location.
     * @return The elevation for the given location or {@code SRTM_DATA_VOID} if no
     *         data is available.
     */
    public synchronized short getElevation(ILatLon latLon) {
        // Update the access time
        accessTime = System.currentTimeMillis();
        int tileLength = getTileLength();
        if (status != Status.VALID || elevationData == null || tileLength == INVALID_TILE_LENGTH)
            return SRTM_DATA_VOID;

        LonLatIndices indices = getIndices(latLon, tileLength);

        try {
            return elevationData[indices.lonIndex][indices.latIndex];
        } catch (ArrayIndexOutOfBoundsException e) {
            // Should not happen
            Logging.error("Elevation: Error reading elevation data from SRTM tile " + id + ": " + e.toString());
            return SRTM_DATA_VOID;
        }
    }

    /**
     * Returns the array indices of the elevation value of the tile raster location
     * that is closest to the given location.
     *
     * Note: This method only uses the sign of latitude and longitude as well as
     * their decimal part to identify and return the appropriate elevation value. It
     * does not check whether this tile is actually meant to provide elevation
     * information for the given location.
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
     * @param latLon     The location.
     * @param tileLength The number of data points in latitude respectively
     *                   longitude dimension of the tile.
     * @return The array indices of the elevation value where the raster location is
     *         closest to the given location.
     */
    private static LonLatIndices getIndices(ILatLon latLon, int tileLength) {
        // Determine the array index at which to retrieve the elevation at the given
        // location
        double lat = latLon.lat();
        double lon = latLon.lon();
        double latDecimalPart = getDecimalPart(lat);
        double lonDecimalPart = getDecimalPart(lon);

        // Q1 or Q2 -> N: Latitude increases in opposite direction of data order
        if (lat >= 0)
            latDecimalPart = 1.0 - latDecimalPart;
        // Q2 or Q3 -> W: (Absolute) longitude increases in opposite direction of data
        // order
        if (lon < 0)
            lonDecimalPart = 1.0 - lonDecimalPart;

        // Map latDecimalPart and lonDecimalPart = [0, 1] to latIndex and lonIndex = [0,
        // tileLength - 1]
        int latIndex = (int) Math.round(latDecimalPart * (tileLength - 1));
        int lonIndex = (int) Math.round(lonDecimalPart * (tileLength - 1));
        return new LonLatIndices(lonIndex, latIndex);
    }

    /**
     * Returns the decimal part [0, 1[ of the given double as positive number.
     *
     * @param d The double.
     * @return The decimal part of the double.
     */
    private static double getDecimalPart(double d) {
        // https://www.baeldung.com/java-separate-double-into-integer-decimal-parts
        BigDecimal bd = new BigDecimal(String.valueOf(d)).abs();
        long intPart = bd.longValue();
        return bd.subtract(new BigDecimal(intPart)).doubleValue();
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

    private static class LonLatIndices {
        public final int lonIndex;
        public final int latIndex;

        public LonLatIndices(int lonIndex, int latIndex) {
            this.lonIndex = lonIndex;
            this.latIndex = latIndex;
        }
    }
}
