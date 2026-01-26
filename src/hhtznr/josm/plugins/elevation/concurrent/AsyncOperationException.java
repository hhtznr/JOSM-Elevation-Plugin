package hhtznr.josm.plugins.elevation.concurrent;

/**
 * This class implements an exception for wrapping other exceptions like
 * {@code CancellationException}, {@code ExecutionException} or
 * {@code InterruptedException} that may intentionally or unintentionally occur
 * during asynchronous execution of tasks.
 *
 * @author Harald Hetzner
 */
public class AsyncOperationException extends Exception {

    private static final long serialVersionUID = 1684867797524862202L;

    /**
     * Creates a new asynchronous operation exception.
     *
     * @param cause The throwable that caused asynchronous operation to fail.
     */
    public AsyncOperationException(Throwable cause) {
        super(cause);
    }
}
