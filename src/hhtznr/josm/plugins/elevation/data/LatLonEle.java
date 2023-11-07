package hhtznr.josm.plugins.elevation.data;

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
     * Creates a new LatLon coordinate with assigned elevation value.
     *
     * @param latLon The LatLon coordinate.
     * @param ele    The elevation in meters.
     */
    public LatLonEle(ILatLon latLon, double ele) {
        this(latLon.lat(), latLon.lon(), ele);
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
}
