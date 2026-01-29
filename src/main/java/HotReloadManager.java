import javax.swing.*;
import java.io.File;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * HotReloadManager
 *
 * Polling-based file watcher with debounce. On file change it:
 *  - calls stopRunCallback.run()
 *  - calls startRunCallback.accept(filePath)
 *  - calls setPauseIconRunnable on EDT to update UI
 * Errors and log messages are forwarded to appendOutputConsumer.
 *
 * This class intentionally does not depend on App internals; it uses callbacks.
 */
public class HotReloadManager {

    private final Consumer<String> startRunCallback; // startRun(filePath)
    private final Runnable stopRunCallback;         // stopRun()
    private final Consumer<String> appendOutput;    // appendToRunOutput(msg)
    private final Runnable setPauseIconRunnable;    // update UI icon to pause

    private ScheduledExecutorService watcherExecutor = null;
    private ScheduledFuture<?> watcherTask = null;
    private ScheduledFuture<?> pendingRestartTask = null;
    private volatile File watchedFile = null;
    private volatile long watchedLastModified = 0L;

    private final long WATCH_POLL_INTERVAL_MS;
    private final long RESTART_DEBOUNCE_MS;

    public HotReloadManager(Consumer<String> startRunCallback,
                            Runnable stopRunCallback,
                            Consumer<String> appendOutput,
                            Runnable setPauseIconRunnable,
                            long watchPollIntervalMs,
                            long restartDebounceMs) {
        this.startRunCallback = startRunCallback;
        this.stopRunCallback = stopRunCallback;
        this.appendOutput = appendOutput;
        this.setPauseIconRunnable = setPauseIconRunnable;
        this.WATCH_POLL_INTERVAL_MS = watchPollIntervalMs;
        this.RESTART_DEBOUNCE_MS = restartDebounceMs;
    }

    /** Start watching the given file. If the same file is already watched, refresh lastModified. */
    public synchronized void startWatching(File file) {
        if (file == null) return;

        if (watchedFile != null && watchedFile.getAbsolutePath().equals(file.getAbsolutePath())) {
            watchedLastModified = file.exists() ? file.lastModified() : watchedLastModified;
            return;
        }

        stopWatching(); // cleanup any existing watcher

        watchedFile = file;
        watchedLastModified = file.exists() ? file.lastModified() : 0L;

        watcherExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hotreload-watcher");
            t.setDaemon(true);
            return t;
        });

        watcherTask = watcherExecutor.scheduleAtFixedRate(() -> {
            try {
                if (watchedFile == null) return;
                long lm = watchedFile.exists() ? watchedFile.lastModified() : 0L;
                if (lm == 0L) return; // file missing
                if (lm > watchedLastModified) {
                    watchedLastModified = lm;

                    // cancel previous pending restart and schedule a debounced restart
                    if (pendingRestartTask != null && !pendingRestartTask.isDone()) {
                        pendingRestartTask.cancel(false);
                    }

                    pendingRestartTask = watcherExecutor.schedule(() -> {
                        try {
                            // stop current run
                            stopRunCallback.run();
                            // start new run with same file
                            startRunCallback.accept(watchedFile.getAbsolutePath());
                            // set pause icon on EDT
                            SwingUtilities.invokeLater(() -> {
                                try {
                                    setPauseIconRunnable.run();
                                } catch (Exception ex) {
                                    // swallow
                                }
                            });
                        } catch (Exception ex) {
                            appendOutput.accept("Hot-reload failed: " + ex.getMessage() + "\n");
                        }
                    }, RESTART_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
                }
            } catch (Exception ex) {
                appendOutput.accept("Watcher error: " + ex.getMessage() + "\n");
            }
        }, WATCH_POLL_INTERVAL_MS, WATCH_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /** Stop watching and cancel pending restarts. */
    public synchronized void stopWatching() {
        watchedFile = null;
        watchedLastModified = 0L;
        if (pendingRestartTask != null) {
            pendingRestartTask.cancel(true);
            pendingRestartTask = null;
        }
        if (watcherTask != null) {
            watcherTask.cancel(true);
            watcherTask = null;
        }
        if (watcherExecutor != null) {
            try { watcherExecutor.shutdownNow(); } catch (Exception ignored) {}
            watcherExecutor = null;
        }
    }

    /** Fully stop everything and free resources. */
    public synchronized void shutdown() {
        stopWatching();
    }
}
