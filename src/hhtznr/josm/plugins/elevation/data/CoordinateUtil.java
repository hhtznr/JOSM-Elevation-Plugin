package hhtznr.josm.plugins.elevation.data;

import org.openstreetmap.gui.jmapviewer.OsmMercator;
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
}
