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

    private final short ele;

    /**
     * Creates a new LatLon coordinate with assigned elevation value.
     *
     * @param lat The latitude in degrees.
     * @param lon The longitude in degrees.
     * @param ele The elevation in meters.
     */
    public LatLonEle(double lat, double lon, short ele) {
        super(lat, lon);
        this.ele = ele;
    }

    /**
     * Creates a new LatLon coordinate with assigned elevation value.
     *
     * @param latLon The LatLon coordinate.
     * @param ele    The elevation in meters.
     */
    public LatLonEle(ILatLon latLon, short ele) {
        this(latLon.lat(), latLon.lon(), ele);
    }

    /**
     * Returns the elevation.
     *
     * @return The elevation in meters.
     */
    public short ele() {
        return ele;
    }
}
