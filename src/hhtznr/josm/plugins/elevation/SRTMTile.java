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

    private String id;
    private Type type;
    private short[] elevationData;
    private Status status;

    /**
     * SRTM file types (SRTM1, SRTM3).
     */
    enum Type {
        SRTM1, SRTM3
    }

    /**
     * Status of SRTM tiles (loading, valid, missing).
     */
    enum Status {
        LOADING, VALID, MISSING
    }

    /**
     * Creates a new SRTM tile.
     *
     * @param id            The ID of the SRTM tile.
     * @param type          The type of the SRTM tile.
     * @param elevationData The elevation data points or {@code null} if no data is
     *                      available.
     * @param status        The current status of the SRTM tile.
     */
    public SRTMTile(String id, Type type, short[] elevationData, Status status) {
        this.id = id;
        this.type = type;
        this.elevationData = elevationData;
        this.status = status;
    }

    /**
     * Returns the tile ID.
     *
     * @return The tile ID, see {@link SRTMFileReader#getSRTMTileID
     *         SRTMFileReader.getSRTMTileID}.
     */
    public String getID() {
        return id;
    }

    /**
     * Returns the tile status.
     *
     * @return The status of the tile.
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Returns the size of the tile's elevation data.
     *
     * @return Size of the elevation data in bytes.
     */
    public int getDataSize() {
        if (elevationData == null)
            return 0;
        // Data size in bytes
        return elevationData.length * 2;
    }

    /**
     * Returns the number of data points in latitude respectively longitude
     * dimension of the tile.
     *
     * @return The number of data points in one tile dimension or
     *         {@code INVALID_TILE_LENGTH} if the SRTM type is inappropriate.
     */
    public int getTileLength() {
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
     * @param latLon The location.
     * @return The elevation for the given location or {@code SRTM_DATA_VOID} if no
     *         data is available.
     */
    public short getElevation(ILatLon latLon) {
        int tileLength = getTileLength();
        if (status != Status.VALID || elevationData == null || tileLength == INVALID_TILE_LENGTH)
            return SRTM_DATA_VOID;

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
        int index = latIndex * tileLength + lonIndex;

        try {
            return elevationData[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            // Should not happen
            Logging.error("Elevation: Error reading elevation data from SRTM tile " + id + ": " + e.toString());
            return SRTM_DATA_VOID;
        }
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
}
