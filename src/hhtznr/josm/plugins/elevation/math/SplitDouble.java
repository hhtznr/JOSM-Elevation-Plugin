package hhtznr.josm.plugins.elevation.math;

import java.math.BigDecimal;

/**
 * Class for splitting a double into its the integer part and the decimal part
 * {@code [0, 1[}.
 *
 * @author Harald Hetzner
 */
public class SplitDouble {

    private final long integerPart;
    private final double decimalPart;

    /**
     * Creates a new split double.
     *
     * @param d The double to split into integer and decimal part.
     */
    public SplitDouble(double d) {
        // https://www.baeldung.com/java-separate-double-into-integer-decimal-parts
        BigDecimal bd = new BigDecimal(String.valueOf(d));
        integerPart = bd.longValue();
        decimalPart = bd.subtract(new BigDecimal(integerPart)).abs().doubleValue();
    }

    /**
     * Returns the integer part.
     *
     * @return The signed integer part.
     */
    public long getIntegerPart() {
        return integerPart;
    }

    /**
     * Returns the decimal part.
     *
     * @return The unsigned decimal part.
     */
    public double getDecimalPart() {
        return decimalPart;
    }
}
