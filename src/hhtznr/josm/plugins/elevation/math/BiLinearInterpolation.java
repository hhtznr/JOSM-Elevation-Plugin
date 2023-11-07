package hhtznr.josm.plugins.elevation.math;

import org.openstreetmap.josm.data.coor.ILatLon;

import hhtznr.josm.plugins.elevation.data.LatLonEle;
import hhtznr.josm.plugins.elevation.data.SRTMTile;

/**
 * This class implements general bilinear interpolation of a function value in
 * x-y coordinate space and a specialized variant to be used for
 * latitude-longitude coordinate space and elevation values.
 *
 * See https://en.wikipedia.org/wiki/Bilinear_interpolation
 *
 * @author Harald Hetzner
 */
public class BiLinearInterpolation {

    /**
     * Performs a bilinear interpolation based on generic x-y coordinates.
     *
     * @param x1  The 1st x coordinate where {@code x1 < x2}.
     * @param x2  The 2nd x coordinate where {@code x2 > x1}.
     * @param y1  The 1st y coordinate where {@code y1 < y2}.
     * @param y2  The 2nd y coordinate where {@code y2 > y1}.
     * @param f11 The known value of the unknown function {@code f} at point
     *            {@code (x1, y1)}.
     * @param f12 The known value of the unknown function {@code f} at point
     *            {@code (x1, y2)}.
     * @param f21 The known value of the unknown function {@code f} at point
     *            {@code (x2, y1)}.
     * @param f22 The known value of the unknown function {@code f} at point
     *            {@code (x2, y2)}.
     * @param x   The x coordinate where we would like to know the value of the
     *            unknown function.
     * @param y   The y coordinate where we would like to know the value of the
     *            unknown function.
     * @return The estimated value of the unknown function {@code f} at point
     *         {@code (x, y)}.
     */
    public static double interpolate(double x1, double x2, double y1, double y2, double f11, double f12, double f21,
            double f22, double x, double y) {
        if (x1 >= x2)
            throw new IllegalArgumentException("Invalid interpolation bounds: x1 = " + x1 + " >= x2 = " + x2);
        if (y1 >= y2)
            throw new IllegalArgumentException("Invalid interpolation bounds: y1 = " + y1 + " >= y2 = " + y2);
        if (x < x1)
            throw new IllegalArgumentException("Interpolation coordinate outside bounds: x = " + x + " < x1 = " + x1);
        if (x > x2)
            throw new IllegalArgumentException("Interpolation coordinate outside bounds: x = " + x + " > x2 = " + x2);
        if (y < y1)
            throw new IllegalArgumentException("Interpolation coordinate outside bounds: y = " + y + " < y1 = " + y1);
        if (y > y2)
            throw new IllegalArgumentException("Interpolation coordinate outside bounds: y = " + y + " > y2 = " + y2);
        // https://en.wikipedia.org/wiki/Bilinear_interpolation
        return 1.0 / ((x2 - x1) * (y2 - y1)) * (f11 * (x2 - x) * (y2 - y) + f21 * (x - x1) * (y2 - y)
                + f12 * (x2 - x) * (y - y1) + f22 * (x - x1) * (y - y1));
    }

    /**
     * Performs a bilinear interpolation based on latitude-longitude coordinates and
     * their elevation values. The coordinates where the elevation value is known
     * may not be identical.
     *
     * @param southWest The south west coordinate and its elevation value.
     * @param northWest The north west coordinate and its elevation value.
     * @param southEast The south east coordinate and its elevation value.
     * @param northEast The north east coordinate and its elevation value.
     * @param latLon    The coordinate where we would like to know the elevation
     *                  value.
     * @return The estimated elevation value at the coordinate of interest.
     */
    public static double interpolate(LatLonEle southWest, LatLonEle northWest, LatLonEle southEast, LatLonEle northEast,
            ILatLon latLon) {
        if (southWest.ele() == SRTMTile.SRTM_DATA_VOID || northWest.ele() == SRTMTile.SRTM_DATA_VOID
                || southEast.ele() == SRTMTile.SRTM_DATA_VOID || northEast.ele() == SRTMTile.SRTM_DATA_VOID)
            throw new IllegalArgumentException("Cannot interpolate with elevation data voids.");
        double x1 = southWest.lon();
        double x2 = southEast.lon();
        double y1 = southWest.lat();
        double y2 = northWest.lat();
        double f11 = southWest.ele();
        double f12 = northWest.ele();
        double f21 = southEast.ele();
        double f22 = northEast.ele();
        double x = latLon.lon();
        double y = latLon.lat();
        return interpolate(x1, x2, y1, y2, f11, f12, f21, f22, x, y);
    }

    private BiLinearInterpolation() {
    }
}
