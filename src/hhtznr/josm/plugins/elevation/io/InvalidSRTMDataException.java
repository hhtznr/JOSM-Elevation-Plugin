package hhtznr.josm.plugins.elevation.io;

/**
 * This class implements an exception to be thrown if an SRTM file is read and
 * it contains more bytes or less bytes than expected.
 *
 * @author Harald Hetzner
 */
public class InvalidSRTMDataException extends Exception {

    private static final long serialVersionUID = 3824874362059457494L;

    /**
     * Creates a new exception regarding invalid SRTM data in an SRTM file.
     *
     * @param message    The exception message.
     */
    public InvalidSRTMDataException(String message) {
        super(message);
    }

}
