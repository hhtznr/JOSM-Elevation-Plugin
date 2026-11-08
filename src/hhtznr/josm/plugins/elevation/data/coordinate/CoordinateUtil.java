package hhtznr.josm.plugins.elevation.data.coordinate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        if (factorLat == 0.0 && factorLon == 0.0) {
            return new Bounds(bounds);
        }

        double latRange = bounds.getHeight();
        double lonRange = bounds.getWidth();
        double halfDeltaLat = 0.5 * (latRange * factorLat - latRange);
        double halfDeltaLon = 0.5 * (lonRange * factorLon - lonRange);
        double minLat = Math.max(bounds.getMinLat() - halfDeltaLat, -90.0);
        double maxLat = Math.min(bounds.getMaxLat() + halfDeltaLat, 90.0);
        double minLon = bounds.getMinLon() - halfDeltaLon;
        // Across 180th meridian
        if (minLon < -180.0) {
            minLon = minLon + 360.0;
        }
        double maxLon = bounds.getMaxLon() + halfDeltaLon;
        // Across 180th meridian
        if (maxLon > 180.0) {
            maxLon = maxLon - 360.0;
        }
        return new Bounds(minLat, minLon, maxLat, maxLon);
    }

    /**
     * Checks if bounds contain other bounds if these are extended by additional
     * space in each direction. This method is meant to trigger recreation of map
     * view paintables when the map view is moved prior to the edges of the map view
     * reaching the edges of the map view paintables. For this,
     * {@code latLonExtraSpace} should be positive multiples of the
     * latitude-longitude step between two adjacent elevation raster points.
     *
     * @param bounds           The bounds for which to check whether
     *                         {@code otherBounds} are contained under consideration
     *                         of extra space.
     * @param otherBounds      The other bounds for which to check if the are
     *                         contained within the specified bounds under
     *                         consideration of the required extra space.
     * @param latLonExtraSpace The extra space in latitude and longitude direction
     *                         to be considered.
     * @return {@code true} if {@code otherBounds} extended by
     *         {@code latLonExtraSpace} in each direction is contained in
     *         {@code bounds}.
     */
    public static boolean doBoundsContainWithExtraSpace(Bounds bounds, Bounds otherBounds, double latLonExtraSpace) {
        // The corner coordinates of the other bounds under consideration of the
        // required extra space, clamped to the edges of the map
        double minLat = Math.max(otherBounds.getMinLat() - latLonExtraSpace, -90.0);
        double minLon = Math.max(otherBounds.getMinLon() - latLonExtraSpace, -180.0);
        double maxLat = Math.min(otherBounds.getMaxLat() + latLonExtraSpace, 90.0);
        double maxLon = Math.min(otherBounds.getMaxLon() + latLonExtraSpace, 180.0);

        // Return whether the corner coordinates of the other bounds under consideration
        // of the required extra space are within the bounds
        return bounds.getMinLat() <= minLat && bounds.getMinLon() <= minLon && bounds.getMaxLat() >= maxLat
                && bounds.getMaxLon() >= maxLon;
    }

    /**
     * Computes the intersection of two bounds, if they intersect and do not cross
     * the 180th meridian.
     *
     * @param a The first bounds.
     * @param b The second bounds.
     * @return The intersection of the two bounds, or {@code null} if they do not
     *         intersect or cross the 180th meridian.
     */
    public static Bounds trivialIntersection(Bounds a, Bounds b) {
        if (a.crosses180thMeridian() || b.crosses180thMeridian()) {
            return null;
        }
        // a below b
        if (a.getMinLat() > b.getMaxLat()) {
            return null;
        }
        // a above b
        if (a.getMaxLat() < b.getMinLat()) {
            return null;
        }
        // a right of b
        if (a.getMinLon() > b.getMaxLon()) {
            return null;
        }
        // a left of b
        if (a.getMaxLon() < b.getMinLon()) {
            return null;
        }

        Bounds intersection = new Bounds(Math.max(a.getMinLat(), b.getMinLat()), Math.max(a.getMinLon(), b.getMinLon()),
                Math.min(a.getMaxLat(), b.getMaxLat()), Math.min(a.getMaxLon(), b.getMaxLon()), false);
        return intersection;
    }

    /**
     * Computes the intersection of two bounds, if they intersect and do not cross
     * the 180th meridian.
     *
     * @param a The first bounds.
     * @param b The second bounds.
     * @return The intersection of the two bounds, or {@code null} if they do not
     *         intersect or cross the 180th meridian. The intersection may consist
     *         of multiple bounds if the two bounds cross the 180th meridian.
     */
    public static List<Bounds> intersection(Bounds a, Bounds b) {
        double minLat = Math.max(a.getMinLat(), b.getMinLat());
        double maxLat = Math.min(a.getMaxLat(), b.getMaxLat());

        if (minLat > maxLat) {
            return List.of();
        }

        List<double[]> aSegments = longitudeSegments(a);
        List<double[]> bSegments = longitudeSegments(b);
        List<double[]> intersections = new ArrayList<>();

        for (double[] aSegment : aSegments) {
            for (double[] bSegment : bSegments) {
                double minLon = Math.max(aSegment[0], bSegment[0]);
                double maxLon = Math.min(aSegment[1], bSegment[1]);

                if (minLon <= maxLon) {
                    intersections.add(new double[] { minLon, maxLon });
                }
            }
        }

        if (intersections.isEmpty()) {
            return List.of();
        }

        intersections.sort(Comparator.comparingDouble(segment -> segment[0]));

        List<double[]> merged = new ArrayList<>();
        for (double[] segment : intersections) {
            if (merged.isEmpty() || segment[0] > merged.get(merged.size() - 1)[1]) {
                merged.add(segment.clone());
            } else {
                double[] previous = merged.get(merged.size() - 1);
                previous[1] = Math.max(previous[1], segment[1]);
            }
        }

        List<Bounds> result = new ArrayList<>();
        boolean joinsAcrossMeridian = merged.size() > 1 && merged.get(0)[0] == 0.0
                && merged.get(merged.size() - 1)[1] == 360.0;

        if (joinsAcrossMeridian) {
            double[] first = merged.get(0);
            double[] last = merged.get(merged.size() - 1);
            result.add(new Bounds(minLat, last[0] - 180.0, maxLat, first[1] - 180.0, false));

            for (int i = 1; i < merged.size() - 1; i++) {
                addNonCrossingBounds(result, merged.get(i), minLat, maxLat);
            }
        } else {
            for (double[] segment : merged) {
                addNonCrossingBounds(result, segment, minLat, maxLat);
            }
        }

        return result;
    }

    private static List<double[]> longitudeSegments(Bounds bounds) {
        List<double[]> segments = new ArrayList<>();

        if (bounds.crosses180thMeridian()) {
            segments.add(new double[] { bounds.getMinLon() + 180.0, 360.0 });
            segments.add(new double[] { 0.0, bounds.getMaxLon() + 180.0 });
        } else {
            segments.add(new double[] { bounds.getMinLon() + 180.0, bounds.getMaxLon() + 180.0 });
        }

        return segments;
    }

    private static void addNonCrossingBounds(List<Bounds> result, double[] segment, double minLat, double maxLat) {
        result.add(new Bounds(minLat, segment[0] - 180.0, maxLat, segment[1] - 180.0, false));
    }
}
