package hhtznr.josm.plugins.elevation.data.geotiff;

/**
 * This class implements an exception which is intended to be used with
 * {@link GeoTiffImage}.
 *
 * @author Harald Hetzner
 */
public class GeoTiffImageException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception which is intended to be used with
     * {@link GeoTiffImage} if creating the image is not possible due to invalid or
     * inconsistent data.
     *
     * @param message The exception message.
     */
    public GeoTiffImageException(String message) {
        super(message);
    }

    /**
     * Creates a new exception which is intended to be used with
     * {@link GeoTiffImage} if creating the image is not possible due to invalid or
     * inconsistent data.
     *
     * @param message The exception message.
     * @param cause   The exception having caused this exception.
     */
    public GeoTiffImageException(String message, Throwable cause) {
        super(message, cause);
    }

}
