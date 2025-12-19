package hhtznr.josm.plugins.elevation.data;

/**
 * Exception class for use with {@link SRTMTileGrid}.
 *.
 * @author Harald Hetzner
 */
public class SRTMTileGridException extends Exception {

    private static final long serialVersionUID = 2375830355789609721L;

    /**
     * Creates a new SRTM tile grid exception.
     *
     * @param message The exception message.
     */
    public SRTMTileGridException(String message) {
        super(message);
    }

    /**
     * Creates a new SRTM tile grid exception.
     *
     * @param cause The {@code Throwable} having caused this exception.
     */
    public SRTMTileGridException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates a new SRTM tile grid exception.
     *
     * @param message The exception message.
     * @param cause The {@code Throwable} having caused this exception.
     */
    public SRTMTileGridException(String message, Throwable cause) {
        super(message, cause);
    }

}
