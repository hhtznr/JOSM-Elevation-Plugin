package hhtznr.josm.plugins.elevation.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * This class provides a thread executor service which uses threads for all
 * available CPU cores or two threads in case of a single-core CPU.
 *
 * @author Harald Hetzner
 */
public class ParallelExecutorService {

    private final ExecutorService executorService;

    /**
     * Creates a new parallel executor.
     */
    public ParallelExecutorService() {
        int numberOfThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
        executorService = Executors.newFixedThreadPool(numberOfThreads);
    }

    /**
     * Submits a task to the executor service.
     *
     * @param <T>  The type of the task result.
     * @param task The task.
     * @return A future from which the result can be obtained as soon as it has been
     *         computed.
     * @throws RejectedExecutionException If the underlying fixed thread pool
     *                                    rejects execution of the task.
     */
    public <T> Future<T> submit(Callable<T> task) throws RejectedExecutionException {
        return executorService.submit(task);
    }

    /**
     * Submits a list of tasks to the executor service. If one of the tasks should
     * be rejected, the already submitted tasks will be cancelled.
     *
     * @param <T>   The type of the task result.
     * @param tasks The list of tasks.
     * @return A list of futures from which the results can be obtained as soon as
     *         these have been computed.
     * @throws RejectedExecutionException If the underlying fixed thread pool
     *                                    rejects execution of a task.
     */
    public <T> List<Future<T>> submit(List<Callable<T>> tasks) throws RejectedExecutionException {
        List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        try {
            for (Callable<T> task : tasks) {
                Future<T> future = executorService.submit(task);
                futures.add(future);
            }
        } catch (RejectedExecutionException e) {
            // Cancel already submitted tasks if not all tasks can be submitted
            for (Future<T> future : futures) {
                future.cancel(true);
            }
            throw e;
        }
        return futures;
    }
}
