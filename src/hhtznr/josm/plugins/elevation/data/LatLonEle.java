package hhtznr.josm.plugins.elevation.data;

import java.util.Objects;

import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;

/**
 * Extensions of JOSM coordinates in LatLon space adding the elevation.
 *
 * @author Harald Hetzner
 */
public class LatLonEle extends LatLon {

    private static final long serialVersionUID = 1L;

    /**
     * {@code Double.NaN} to indicate that no valid elevation is provided.
     */
    public static final double NO_VALID_ELEVATION = Double.NaN;

    private final double ele;

    /**
     * Creates a new LatLon coordinate with assigned elevation value.
     *
     * @param lat The latitude in degrees.
     * @param lon The longitude in degrees.
     * @param ele The elevation in meters.
     */
    public LatLonEle(double lat, double lon, double ele) {
        super(lat, lon);
        this.ele = ele;
    }

    /**
     * Creates a new LatLon coordinate with assigned elevation value. Takes the
     * elevation value as a short and sets the internal elevation value to
     * {@code NO_VALID_ELEVATION} if the provided elevation has the special value
     * {@link SRTMTile#SRTM_DATA_VOID}.
     *
     * @param lat The latitude in degrees.
     * @param lon The longitude in degrees.
     * @param ele The elevation in meters.
     */
    public LatLonEle(double lat, double lon, short ele) {
        this(lat, lon, ele == SRTMTile.SRTM_DATA_VOID ? NO_VALID_ELEVATION : ele);
    }

    /**
     * Creates a new LatLon coordinate with assigned elevation value.
     *
     * @param latLon The LatLon coordinate.
     * @param ele    The elevation in meters.
     */
    public LatLonEle(ILatLon latLon, double ele) {
        this(latLon.lat(), latLon.lon(), ele);
    }

    /**
     * Creates a new LatLon coordinate with assigned elevation value. Takes the
     * elevation value as a short and sets the internal elevation value to
     * {@code NO_VALID_ELEVATION} if the provided elevation has the special value
     * {@link SRTMTile#SRTM_DATA_VOID}.
     *
     * @param latLon The LatLon coordinate.
     * @param ele    The elevation in meters.
     */
    public LatLonEle(ILatLon latLon, short ele) {
        this(latLon, ele == SRTMTile.SRTM_DATA_VOID ? NO_VALID_ELEVATION : ele);
    }

    /**
     * Returns the elevation.
     *
     * @return The elevation in meters.
     */
    public double ele() {
        return ele;
    }

    /**
     * Returns whether this {@code LatLonEle} provides a valid elevation value.
     *
     * @return {@code true} if a valid elevation value is provided, {@code false}
     *         otherwise.
     */
    public boolean hasValidEle() {
        return !Double.isNaN(ele);
    }

    @Override
    public String toString() {
        return "LatLonEle[lat=" + lat() + ",lon=" + lon() + ",ele=" + ele() + ']';
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, ele);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        LatLonEle that = (LatLonEle) obj;
        return Double.compare(that.x, x) == 0 && Double.compare(that.y, y) == 0 && Double.compare(that.ele, ele) == 0;
    }
}
