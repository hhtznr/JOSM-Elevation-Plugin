package hhtznr.josm.plugins.elevation.data;

import org.openstreetmap.josm.data.coor.LatLon;

/**
 * Simple implementation of a line defined by its two endpoints in
 * latitude-longitude coordinate space.
 *
 * @author Harald Hetzner
 */
public class LatLonLine {
    private final LatLon latLon1;
    private final LatLon latLon2;

    /**
     * Creates a new line in latitude-longitude coordinate space.
     *
     * @param latLon1 The coordinate of the first end point.
     * @param latLon2 The coordinate of the second end point.
     */
    public LatLonLine(LatLon latLon1, LatLon latLon2) {
        this.latLon1 = latLon1;
        this.latLon2 = latLon2;
    }

    /**
     * Returns the coordinate of the first end point.
     *
     * @return The coordinate of the first end point.
     */
    public LatLon getLatLon1() {
        return latLon1;
    }

    /**
     * Returns the coordinate of the second end point.
     *
     * @return The coordinate of the second end point.
     */
    public LatLon getLatLon2() {
        return latLon2;
    }
}
