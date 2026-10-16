package hhtznr.josm.plugins.elevation.data;

/**
 * Exception class for classes implementing {@link ElevationGrid}.
 *
 * @author Harald Hetzner
 */
public class ElevationGridException extends Exception {

    private static final long serialVersionUID = -6622597162143549590L;

    /**
     * Creates a new elevation grid exception.
     *
     * @param message The exception message.
     */
    public ElevationGridException(String message) {
        super(message);
    }

    /**
     * Creates a new elevation grid exception.
     *
     * @param cause The {@code Throwable} having caused this exception.
     */
    public ElevationGridException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates a new elevation grid exception.
     *
     * @param message The exception message.
     * @param cause   The {@code Throwable} having caused this exception.
     */
    public ElevationGridException(String message, Throwable cause) {
        super(message, cause);
    }
}
