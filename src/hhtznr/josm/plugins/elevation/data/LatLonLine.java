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

    /**
     * Returns the coordinates of the point on the line, which is closest to the
     * given point based on the euclidian distance (latitude-longitude coordinate
     * space, not great circle distance).
     *
     * @param coord The point for which to determine the closest point on the line.
     * @return The point on the line which is the closest to the given point based
     *         on the euclidian distance.
     */
    public LatLon getClosestPointTo(LatLon coord) {
        double deltaLatSegment = latLon2.lat() - latLon1.lat();
        double deltaLonSegment = latLon2.lon() - latLon1.lon();
        // First and last point of the segment are the same
        if (deltaLatSegment == 0.0 && deltaLonSegment == 0.0)
            return new LatLon(latLon1);
        double deltaLatPointToSegmentP1 = coord.lat() - latLon1.lat();
        double deltaLonPointToSegmentP1 = coord.lon() - latLon1.lon();
        double segmentLenSq = deltaLatSegment * deltaLatSegment + deltaLonSegment * deltaLonSegment;
        double t = (deltaLatPointToSegmentP1 * deltaLatSegment + deltaLonPointToSegmentP1 * deltaLonSegment)
                / segmentLenSq;
        t = Math.max(0, Math.min(1, t)); // clamp to segment
        return new LatLon(latLon1.lat() + t * deltaLatSegment, latLon1.lon() + t * deltaLonSegment);
    }
}
