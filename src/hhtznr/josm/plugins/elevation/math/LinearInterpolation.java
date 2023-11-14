package hhtznr.josm.plugins.elevation.math;

/**
 * This class implements general linear interpolation of a function value in x-y
 * coordinate space.
 *
 * See https://en.wikipedia.org/wiki/Linear_interpolation
 *
 * @author Harald Hetzner
 */
public class LinearInterpolation {

    /**
     * Performs a bilinear interpolation based on generic x-y coordinates.
     *
     * @param x1 The 1st x coordinate where {@code x1 < x2}.
     * @param x2 The 2nd x coordinate where {@code x2 > x1}.
     * @param y1 The 1st y coordinate where {@code y1 < y2}.
     * @param y2 The 2nd y coordinate where {@code y2 > y1}.
     * @param x  The x coordinate where we would like to know the value of the
     *           unknown function.
     * @return The estimated value of {@code y} at the value {@code x}.
     */
    public static double interpolate(double x1, double x2, double y1, double y2, double x) {
        if (x1 >= x2)
            throw new IllegalArgumentException("Invalid interpolation bounds: x1 = " + x1 + " >= x2 = " + x2);
        if (x < x1)
            throw new IllegalArgumentException("Interpolation coordinate outside bounds: x = " + x + " < x1 = " + x1);
        if (x > x2)
            throw new IllegalArgumentException("Interpolation coordinate outside bounds: x = " + x + " > x2 = " + x2);
        // https://en.wikipedia.org/wiki/Linear_interpolation
        return (y1 * (x2 - x) + y2 * (x - x1)) / (x2 - x1);
    }

    private LinearInterpolation() {
    }
}
