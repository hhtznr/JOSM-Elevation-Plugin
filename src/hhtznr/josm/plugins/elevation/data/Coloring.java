package hhtznr.josm.plugins.elevation.data;

import java.awt.Color;

/**
 * This class provides methods related to coloring.
 *
 * @author Harald Hetzner
 */
public class Coloring {

    /**
     * Coloring schemes: Constant color or false color.
     */
    public static enum Scheme {
        /**
         * Constant color.
         */
        CONSTANT_COLOR("Constant color"),

        /**
         * False color.
         */
        FALSE_COLOR("False color");

        private final String schemeName;

        Scheme(String schemeName) {
            this.schemeName = schemeName;
        }

        /**
         * Returns the name associated with this coloring scheme.
         *
         * @return The name of the scheme.
         */
        @Override
        public String toString() {
            return schemeName;
        }

        /**
         * Returns the coloring scheme associated with a given name.
         *
         * @param name The name associated with the coloring scheme.
         * @return The coloring scheme associated with the name.
         */
        public static Scheme fromString(String name) {
            for (Scheme scheme : Scheme.values()) {
                if (scheme.toString().equals(name))
                    return scheme;
            }
            return CONSTANT_COLOR;
        }
    }

    private Coloring() {
    }

    /**
     * Returns a rainbow color value from purple to red, which corresponds to the
     * relative position of the given value on a scale defined by the given minimum
     * and maximum values.
     *
     * @param value    The value on the scale.
     * @param minValue The minimum value of the scale.
     * @param maxValue The maximum value of the scale.
     * @return The color value.
     */
    public static Color getRainbowColor(double value, double minValue, double maxValue) {
        // 1. Normalize value to a 0.0 - 1.0 range
        float fraction = (float) ((value - minValue) / (maxValue - minValue));
        fraction = Math.max(0f, Math.min(1f, fraction)); // Clamp between 0 and 1

        // 2. Map fraction to Hue range
        // Start at 0.83 (purple) for minimum and go to 0.0 (red) for maximum
        float startHue = 0.83f;
        float endHue = 0.0f;
        float hue = startHue + (fraction * (endHue - startHue));

        // 3. Return the color with full saturation and brightness
        return Color.getHSBColor(hue, 1.0f, 1.0f);
    }

}
