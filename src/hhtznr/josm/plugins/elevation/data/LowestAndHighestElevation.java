package hhtznr.josm.plugins.elevation.data;

import hhtznr.josm.plugins.elevation.data.coordinate.LatLonEle;

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
     * @param elevationGridView The elevation grid view for which this pair of
     *                          lowest and highest points is created.
     * @param lowestElevation   The value of the lowest elevation or
     *                          {@link LatLonEle#NO_VALID_ELEVATION} if the value is
     *                          not defined.
     * @param highestElevation  The value of the highest elevation or
     *                          {@link LatLonEle#NO_VALID_ELEVATION} if the value is
     *                          not defined.
     */
    public LowestAndHighestElevation(ElevationGridView elevationGridView, double lowestElevation,
            double highestElevation) {
        if (!elevationGridView.isNoDataValue(lowestElevation) && elevationGridView.isNoDataValue(highestElevation)
                && lowestElevation > highestElevation) {
            throw new IllegalArgumentException(
                    "Lowest elevation must not be greater than highest elevation. Provided: lowest elevation = "
                            + lowestElevation + ", highest elevation = " + highestElevation);
        }
        this.lowestElevation = lowestElevation;
        this.highestElevation = highestElevation;
    }

    /**
     * Returns the lowest elevation value.
     *
     * @return The lowest elevation or {@link LatLonEle#NO_VALID_ELEVATION} if the
     *         value is not defined
     */
    public double getLowestElevation() {
        return lowestElevation;
    }

    /**
     * Returns the highest elevation value.
     *
     * @return The highest elevation or {@link LatLonEle#NO_VALID_ELEVATION} if the
     *         value is not defined
     */
    public double getHighestElevation() {
        return highestElevation;
    }

    /**
     * Returns whether this pair of lowest and highest elevation is valid.
     *
     * @return {@code true} if both, lowest and highest elevation value or not
     *         {@link LatLonEle#NO_VALID_ELEVATION}; {@code false} otherwise.
     */
    public boolean isValid() {
        return LatLonEle.isValidEle(lowestElevation) && LatLonEle.isValidEle(highestElevation);
    }
}
