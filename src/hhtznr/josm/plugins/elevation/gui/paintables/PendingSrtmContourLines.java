package hhtznr.josm.plugins.elevation.gui.paintables;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;

import org.openstreetmap.josm.data.Bounds;

import hhtznr.josm.plugins.elevation.ElevationPlugin;
import hhtznr.josm.plugins.elevation.concurrent.AsyncOperationException;
import hhtznr.josm.plugins.elevation.data.ElevationDataConsumer;
import hhtznr.josm.plugins.elevation.data.ElevationGridException;
import hhtznr.josm.plugins.elevation.data.srtm.SrtmTileGrid;
import hhtznr.josm.plugins.elevation.data.srtm.SrtmTileGridView;
import hhtznr.josm.plugins.elevation.gui.layers.ElevationLayer;
import hhtznr.josm.plugins.elevation.util.IncrementalNumberedNameCreator;

/**
 * This class implements a pending contour lines task which computes requested
 * SRTM contour lines and executes a provided task after successful contour line
 * computation. This task should be used to initiate a repaint of the map view
 * when contour lines have been computed. The pending contour lines task keeps
 * track of a number of parameters used as input for contour line computation.
 * This way, a caller can decide whether the result of the task is still needed,
 * if the map view has changed.
 *
 * @author Harald Hetzner
 */
public class PendingSrtmContourLines extends ElevationDataConsumer {

    private static final IncrementalNumberedNameCreator namer = new IncrementalNumberedNameCreator(
            "Pending SRTM contour lines");

    private final Bounds requestBounds;
    private final Bounds renderingBounds;
    private final int isostep;
    private final int requestPixelWidth;
    private final int requestPixelHeight;
    private final int gridViewPixelWidth;
    private final int gridViewPixelHeight;
    private final int lowerCutoffElevation;
    private final int upperCutoffElevation;
    private final Runnable repaintTask;

    private Future<ContourLines> contourLinesFuture = null;
    private final Object contourLinesFutureLock = new Object();

    private ContourLines unclaimedContourLines;
    private boolean discarded;
    private boolean computationUsingGrid;

    /**
     * Creates a new pending contour lines task.
     *
     * @param srtmTileGrid         The SRTM tile grid from which the contour lines
     *                             shall be computed.
     * @param requestBounds        The geographical bounds for which the contour
     *                             lines are requested (the effective bounds of the
     *                             contour lines are slightly larger).
     * @param renderingBounds      The effective bounds of the contour lines (these
     *                             are slightly larger than the request bounds)
     * @param requestPixelWidth    The width in screen pixels corresponding to the
     *                             length of the request bounds in longitude
     *                             direction.
     * @param requestPixelHeight   The height in screen pixels corresponding to the
     *                             length of the request bounds in latitude
     *                             direction.
     * @param gridViewPixelWidth   The width in screen pixels corresponding to the
     *                             longitude length of the grid view providing the
     *                             elevation grid points for contour line
     *                             computation.
     * @param gridViewPixelHeight  The height in screen pixels corresponding to the
     *                             latitude length of the grid view providing the
     *                             elevation grid points for contour line
     *                             computation.
     * @param isostep              The step in meters between adjacent contour
     *                             lines.
     * @param lowerCutoffElevation The elevation value below which contour lines
     *                             will not be generated.
     * @param upperCutoffElevation The elevation value above which contour lines
     *                             will not be generated.
     * @param repaintTask          A task executed after successful computation of
     *                             the contour lines. This task should be used to
     *                             initiate a repaint of the map view.
     */
    public PendingSrtmContourLines(SrtmTileGrid srtmTileGrid, Bounds requestBounds, Bounds renderingBounds,
            int requestPixelWidth, int requestPixelHeight, int gridViewPixelWidth, int gridViewPixelHeight, int isostep,
            int lowerCutoffElevation, int upperCutoffElevation, Runnable repaintTask) {
        super(namer.nextName(), srtmTileGrid);
        this.requestBounds = requestBounds;
        this.renderingBounds = renderingBounds;
        this.isostep = isostep;
        this.requestPixelWidth = requestPixelWidth;
        this.requestPixelHeight = requestPixelHeight;
        this.gridViewPixelWidth = gridViewPixelWidth;
        this.gridViewPixelHeight = gridViewPixelHeight;
        this.lowerCutoffElevation = lowerCutoffElevation;
        this.upperCutoffElevation = upperCutoffElevation;
        this.repaintTask = repaintTask;
    }

    /**
     * Returns the bounds for which contour lines were requested.
     *
     * @return The geographical bounds for which the contour lines are requested
     *         (the effective bounds of the contour lines are slightly larger).
     */
    public Bounds getRequestBounds() {
        return requestBounds;
    }

    /**
     * Returns the isostep of the requested contour lines.
     *
     * @return The step in meters between adjacent contour lines.
     */
    public int getIsostep() {
        return isostep;
    }

    /**
     * Returns the screen pixel width of the request bounds at the time of the
     * request.
     *
     * @return The width in screen pixels corresponding to the length of the request
     *         bounds in longitude direction.
     */
    public int getRequestPixelWidth() {
        return requestPixelWidth;
    }

    /**
     * Returns the screen pixel height of the request bounds at the time of the
     * request.
     *
     * @return The height in screen pixels corresponding to the length of the
     *         request bounds in latitude direction.
     */
    public int getRequestPixelHeight() {
        return requestPixelHeight;
    }

    /**
     * Returns the lower cutoff elevation of the requested contour lines.
     *
     * @return The elevation value below which contour lines will not be generated.
     */
    public int getLowerCutoffElevation() {
        return lowerCutoffElevation;
    }

    /**
     * Returns the upper cutoff elevation of the requested contour lines.
     *
     * @return The elevation value above which contour lines will not be generated.
     */
    public int getUpperCutoffElevation() {
        return upperCutoffElevation;
    }

    /**
     * Submits the contour line execution task if it has not been submitted yet and
     * returns a future from which the computed contour lines can be retrieved as
     * soon as these were computed.
     *
     * @return A future from which the computed contour lines can be retrieved.
     */
    public Future<ContourLines> getContourLines() {
        synchronized (contourLinesFutureLock) {
            if (contourLinesFuture != null) {
                return contourLinesFuture;
            }

            if (isDisposed()) {
                throw new IllegalStateException("Already disposed");
            }

            // Runs the repaint task after completion of the future
            FutureTask<ContourLines> task = new FutureTask<>(this::computeContourLines) {
                @Override
                protected void done() {
                    if (!isCancelled()) {
                        repaintTask.run();
                    }
                }
            };

            contourLinesFuture = task;
            try {
                ElevationPlugin.getParallelExecutor().submit(task);
            } catch (RejectedExecutionException e) {
                dispose();
                throw e;
            }
            return contourLinesFuture;
        }
    }

    // Could in theory return null
    private ContourLines computeContourLines()
            throws AsyncOperationException, ElevationGridException, InterruptedException {
        synchronized (contourLinesFutureLock) {
            if (discarded) {
                return null;
            }
            computationUsingGrid = true;
        }

        try {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException(getName() + " interrupted prior to task execution");
            }

            SrtmTileGrid tileGrid = (SrtmTileGrid) getElevationGrid();

            tileGrid.waitForTilesCached();
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException(getName() + " interrupted while waiting for SRTM tiles to be cached");
            }
            SrtmTileGridView gridView = tileGrid.getView(renderingBounds);

            // Determine resolution of elevation grid relative to screen pixels
            int gridViewWidth = gridView.getWidth();
            int gridViewHeight = gridView.getHeight();
            // Compute grid steps to skip grid points
            // Skip more grid points if further zoomed out from the map
            int gridStepLon = (int) Math.round((double) gridViewWidth / (double) gridViewPixelWidth
                    * ElevationLayer.PIXELS_PER_GRID_POINT);
            int gridStepLat = (int) Math.round((double) gridViewHeight / (double) gridViewPixelHeight
                    * ElevationLayer.PIXELS_PER_GRID_POINT);
            // Ensure that the steps are always at least 1
            gridStepLon = Math.max(gridStepLon, 1);
            gridStepLat = Math.max(gridStepLat, 1);

            ContourLines result = gridView.getContourLines(isostep, gridStepLat, gridStepLon, lowerCutoffElevation,
                    upperCutoffElevation);
            return publishContourLines(result);
        } finally {
            synchronized (contourLinesFutureLock) {
                computationUsingGrid = false;
            }
            // Release grid registration only after the worker stops using the grid.
            super.dispose();
        }
    }

    private ContourLines publishContourLines(ContourLines result) {
        boolean disposeResult;
        synchronized (contourLinesFutureLock) {
            disposeResult = discarded;
            if (!disposeResult) {
                unclaimedContourLines = result;
            }
        }
        if (disposeResult && result != null) {
            result.dispose();
        }
        return result;
    }

    /**
     * Claims the contour lines. After successfully claiming the contour lines,
     * their disposal must be handled externally. If computed contour lines were not
     * claimed, they will be disposed if this pending contour lines task is
     * disposed.
     *
     * @param result The contour lines to claim.
     * @return The provided contour lines if these are identical to the unclaimed
     *         contour line result of this pending contour lines task. {@code null}
     *         if the provided contour lines are not equal to the unclaimed computed
     *         contour lines or if this task was discarded
     */
    public ContourLines claimContourLines(ContourLines result) {
        synchronized (contourLinesFutureLock) {
            if (discarded || unclaimedContourLines != result) {
                return null;
            }
            unclaimedContourLines = null;
            return result;
        }
    }

    /**
     * Cancels this request, releases its grid registration when safe, and disposes
     * any computed contour lines that have not been claimed.
     */
    @Override
    public void dispose() {
        ContourLines toDispose;
        Future<ContourLines> future;
        boolean releaseGrid;
        synchronized (contourLinesFutureLock) {
            discarded = true;
            toDispose = unclaimedContourLines;
            unclaimedContourLines = null;
            future = contourLinesFuture;
            releaseGrid = !computationUsingGrid;
        }

        if (future != null && !future.isDone()) {
            future.cancel(true);
        }

        if (toDispose != null) {
            toDispose.dispose();
        }

        if (releaseGrid) {
            super.dispose();
        }
    }
}
