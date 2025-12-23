package hhtznr.josm.plugins.elevation.util;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * This class implements a wrapper for {@code ExecutorService} which allows
 * canceling all active tasks of the executor.
 *
 * @param <T> The type of the result objects of the tasks submitted to the
 *            {@code ExecutorService}.
 * @author Harald Hetzner
 */
public class CancelableExecutor<T> {
    private final ExecutorService executor;
    private final Set<CompletableFuture<T>> activeTasks = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new cancelable executor wrapping the provided
     * {@code ExecutorService}.
     *
     * @param executor The {@code ExecutorService} to be wrapped.
     */
    public CancelableExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    /**
     * Submits a task to the {@code ExecutorService} and registers it for
     * cancellation.
     *
     * @param task The task to submit.
     * @return A {@code Future} from which the result of the task can be obtained.
     */
    public CompletableFuture<T> submit(Callable<T> task) {
        // 1. Create the proxy immediately so it can be captured by the lambda
        CompletableFuture<T> proxy = new CompletableFuture<>();

        // 2. Add the proxy to the tracking set
        activeTasks.add(proxy);

        // 3. Submit the actual work to the executor
        executor.submit(() -> {
            try {
                // If the proxy was cancelled before we started, don't even run the task
                if (proxy.isCancelled())
                    return;

                T result = task.call();
                // This triggers all .get() or .thenAccept() calls
                proxy.complete(result);
            } catch (Exception e) {
                proxy.completeExceptionally(e);
            } finally {
                // 4. Remove the proxy from the set when finished
                activeTasks.remove(proxy);
            }
        });

        return proxy;
    }

    /**
     * Cancels all queued and running tasks of the {@code ExecutorService}.
     */
    public void cancelAllTasks() {
        // Cancel every proxy in the set
        for (CompletableFuture<T> proxy : activeTasks) {
            // mayInterruptIfRunning = true attempts to stop the thread
            proxy.cancel(true);
        }
        activeTasks.clear();

        // Also clear the internal executor queue
        if (executor instanceof ThreadPoolExecutor) {
            ((ThreadPoolExecutor) executor).getQueue().clear();
        }
    }
}
