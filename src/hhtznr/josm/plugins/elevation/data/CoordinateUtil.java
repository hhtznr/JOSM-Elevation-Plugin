package hhtznr.josm.plugins.elevation.data;

import org.openstreetmap.gui.jmapviewer.OsmMercator;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;

/**
 * This class implements helper methods for latitude-longitude coordinates.
 *
 * @author Harald Hetzner
 */
public class CoordinateUtil {

    private CoordinateUtil() {
    }

    /**
     * Computes the destination point given a start point, distance, and azimuth.
     * <p>
     * Uses a spherical Earth approximation. The distance is measured along the
     * Earth's surface, and the azimuth is measured clockwise from true north.
     *
     * @param point      The starting point
     * @param distance   The distance to travel in meters.
     * @param azimuthDeg The azimuth in degrees (0° = north, 90° = east, 180° =
     *                   south, 270° = west)
     * @return The destination point on the Earth's surface
     */
    public static LatLon destination(LatLon point, double distance, double azimuthDeg) {
        // Convert latitude, longitude and azimuth to radians
        double latRad = Math.toRadians(point.lat());
        double lonRad = Math.toRadians(point.lon());
        double azimuthRad = Math.toRadians(azimuthDeg);

        // Compute new latitude
        double destLatRad = Math.asin(Math.sin(latRad) * Math.cos(distance / OsmMercator.EARTH_RADIUS)
                + Math.cos(latRad) * Math.sin(distance / OsmMercator.EARTH_RADIUS) * Math.cos(azimuthRad));

        // Compute new longitude
        double destLonRad = lonRad
                + Math.atan2(Math.sin(azimuthRad) * Math.sin(distance / OsmMercator.EARTH_RADIUS) * Math.cos(latRad),
                        Math.cos(distance / OsmMercator.EARTH_RADIUS) - Math.sin(latRad) * Math.sin(destLatRad));

        // Convert back to degrees
        return new LatLon(Math.toDegrees(destLatRad), Math.toDegrees(destLonRad));
    }

    /**
     * Uniformly scales the bounds by a factor. A factor {@code > 0} expands the
     * bounds. A factor {@code < 0} shrinks the bounds. A factor equal to {@code 0}
     * has no scale effect.
     *
     * @param bounds The bounds.
     * @param factor The scale factor.
     * @return The scaled bounds clamped to the poles, if necessary.
     */
    public static Bounds getScaledBounds(Bounds bounds, double factor) {
        return getScaledBounds(bounds, factor, factor);
    }

    /**
     * Scales the bounds by provided factory in latitude and longitude direction. A
     * factor {@code > 0} expands the bounds in the respective direcion. A factor
     * {@code < 0} shrinks the bounds in the respective direction. A factor equal to
     * {@code 0} has no scale effect.
     *
     * @param bounds    The bounds.
     * @param factorLat The scale factor in latitude direction.
     * @param factorLon The scale factor in longitude direction.
     * @return The scaled bounds clamped to the poles, if necessary.
     */
    public static Bounds getScaledBounds(Bounds bounds, double factorLat, double factorLon) {
        if (factorLat == 0.0 && factorLon == 0.0)
            return new Bounds(bounds);

        double latRange = bounds.getHeight();
        double lonRange = bounds.getWidth();
        double halfDeltaLat = 0.5 * (latRange * factorLat - latRange);
        double halfDeltaLon = 0.5 * (lonRange * factorLon - lonRange);
        double minLat = Math.max(bounds.getMinLat() - halfDeltaLat, -90.0);
        double maxLat = Math.min(bounds.getMaxLat() + halfDeltaLat, 90.0);
        double minLon = bounds.getMinLon() - halfDeltaLon;
        // Across 180th meridian
        if (minLon < -180.0)
            minLon = minLon + 360.0;
        double maxLon = bounds.getMaxLon() + halfDeltaLon;
        // Across 180th meridian
        if (maxLon > 180.0)
            maxLon = maxLon - 360.0;
        return new Bounds(minLat, minLon, maxLat, maxLon);
    }
}
