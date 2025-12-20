package hhtznr.josm.plugins.elevation.gui;

import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * This class implements a factory for creation of GUI components.
 *
 * @author Harald Hetzner
 */
public class UIFactory {

    private UIFactory() {
    }

    /**
     * Creates a {@code JSpinner} for integers ensuring that {@code value} is
     * clamped to {@code minimum} and {@code maximum}.
     *
     * @param value    The initial value of the spinner.
     * @param minimum  The minimum value of the spinner.
     * @param maximum  The maximum value of the spinner.
     * @param stepSize The value increment/decrement.
     * @return A {@code JSpinner}, which is set up with a
     *         {@code SpinnerNumberModel}.
     */
    public static JSpinner createSpinner(int value, int minimum, int maximum, int stepSize) {
        if (value < minimum)
            value = minimum;
        else if (value > maximum)
            value = maximum;
        return new JSpinner(new SpinnerNumberModel(value, minimum, maximum, stepSize));
    }

    /**
     * Creates a {@code JSpinner} for doubles ensuring that {@code value} is clamped
     * to {@code minimum} and {@code maximum}.
     *
     * @param value    The initial value of the spinner.
     * @param minimum  The minimum value of the spinner.
     * @param maximum  The maximum value of the spinner.
     * @param stepSize The value increment/decrement.
     * @return A {@code JSpinner}, which is set up with a
     *         {@code SpinnerNumberModel}.
     */
    public static JSpinner createSpinner(double value, double minimum, double maximum, double stepSize) {
        if (value < minimum)
            value = minimum;
        else if (value > maximum)
            value = maximum;
        return new JSpinner(new SpinnerNumberModel(value, minimum, maximum, stepSize));
    }
}
