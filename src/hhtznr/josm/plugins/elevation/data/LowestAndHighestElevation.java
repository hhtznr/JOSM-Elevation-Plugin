package hhtznr.josm.plugins.elevation.data;

/**
 * A small helper class to carry a pair of lowest and highest elevation values.
 *
 * @author Harald Hetzner
 */
public class LowestAndHighestElevation {

    private final double lowestElevation;
    private final double highestElevation;

    /**
     * Creates a pair of lowest and highest elevation values.
     *
     * @param lowestElevation  The value of the lowest elevation or
     *                         {@code Double.NaN} if the value is not defined.
     * @param highestElevation The value of the highest elevation or
     *                         {@code Double.NaN} if the value is not defined.
     */
    public LowestAndHighestElevation(double lowestElevation, double highestElevation) {
        this.lowestElevation = lowestElevation;
        this.highestElevation = highestElevation;
    }

    /**
     * Returns the lowest elevation value.
     *
     * @return The lowest elevation or {@code Double.NaN} if the value is not
     *         defined
     */
    public double getLowestElevation() {
        return lowestElevation;
    }

    /**
     * Returns the highest elevation value.
     *
     * @return The highest elevation or {@code Double.NaN} if the value is not
     *         defined
     */
    public double getHighestElevation() {
        return highestElevation;
    }

    /**
     * Returns whether this pair of lowest and highest elevation is valid.
     *
     * @return {@code true} if both, lowest and highest elevation value or not
     *         {@code Double.NaN}; {@code false} otherwise.
     */
    public boolean isValid() {
        return !Double.isNaN(lowestElevation) && !Double.isNaN(highestElevation);
    }
}
