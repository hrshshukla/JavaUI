import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;

/**
 * WatcherController
 *
 * Encapsulates the watcher (eye) toggle button logic:
 * - toggles watcher ON/OFF
 * - when ON and a Java file is selected it saves, starts a run and instructs
 * HotReloadManager to watch the file
 * - when OFF it stops the HotReloadManager and updates icons
 *
 * This component is UI-light: it exposes the watcher JButton via getButton() so
 * App can add it to toolPanel.
 *
 * Dependencies (passed in constructor):
 * - App app: to access projectView/editorView for saving and selection
 * - HotReloadManager hotReloadManager
 * - JavaRunManager javaRunManager
 * - RunOutputPanel runOutputPanel (to clear output on run)
 * - JButton runButton (so watcher can set run icon to pause when enabled)
 * - icons: eyeOpen, eyeClose, pauseIcon, playIcon
 */
public class WatcherController {

    private final App app;
    private final HotReloadManager hotReloadManager;
    private final JavaRunManager javaRunManager;
    private final RunOutputPanel runOutputPanel;
    private final JButton runButton;

    private final ImageIcon eyeOpen;
    private final ImageIcon eyeClose;
    private final ImageIcon pauseIcon;
    private final ImageIcon playIcon;

    private final JButton watcherButton;
    private volatile boolean enabled = false;

    public WatcherController(App app,
            HotReloadManager hotReloadManager,
            JavaRunManager javaRunManager,
            RunOutputPanel runOutputPanel,
            JButton runButton,
            ImageIcon eyeOpenIcon,
            ImageIcon eyeCloseIcon,
            ImageIcon pauseIcon,
            ImageIcon playIcon) {
        this.app = app;
        this.hotReloadManager = hotReloadManager;
        this.javaRunManager = javaRunManager;
        this.runOutputPanel = runOutputPanel;
        this.runButton = runButton;
        this.eyeOpen = eyeOpenIcon;
        this.eyeClose = eyeCloseIcon;
        this.pauseIcon = pauseIcon;
        this.playIcon = playIcon;

        watcherButton = new JButton();
        watcherButton.setIcon(eyeClose != null ? eyeClose : null); // default OFF
        watcherButton.setFocusable(false);
        watcherButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        watcherButton.setContentAreaFilled(false);
        watcherButton.setBorderPainted(false);
        watcherButton.setFocusPainted(false);
        watcherButton.setOpaque(false);
        watcherButton.setToolTipText("Toggle Watcher (Hot-reload)");

        watcherButton.addActionListener(e -> {
            // If currently OFF and user is trying to turn it ON -> validate selection
            if (!enabled) {
                CustomNode node = null;
                try {
                    node = app.projectView.getSelectedCustomNode();
                } catch (Exception ex) {
                }

                if (node == null || node.isDirectory || node.getFilePath() == null
                        || !node.getFilePath().toLowerCase().endsWith(".java")) {
                    // same style warning as Run button
                    JOptionPane.showMessageDialog(app,
                            "Select a Java file to start watcher",
                            "No File Selected",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }

            // Toggle watcher (will call setEnabled which handles icons and start/stop)
            setEnabled(!enabled);
        });

    }

    /** Return the button to add to tool panel */
    public JButton getButton() {
        return watcherButton;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enable / disable watcher.
     * When enabling: if a .java file is selected, save -> run ->
     * startWatching(file).
     * When disabling: stopWatching().
     */
    public void setEnabled(boolean on) {
        if (on == this.enabled)
            return; // no-op
        this.enabled = on;

        SwingUtilities.invokeLater(() -> {
            // update watcher icon
            if (enabled) {
                if (eyeOpen != null)
                    watcherButton.setIcon(eyeOpen);
                // show continuous mode: run button should look like pause
                if (pauseIcon != null && runButton != null)
                    runButton.setIcon(pauseIcon);
            } else {
                if (eyeClose != null)
                    watcherButton.setIcon(eyeClose);
                // if nothing running, show play icon
                if (!javaRunManager.isRunning() && playIcon != null && runButton != null) {
                    runButton.setIcon(playIcon);
                }
            }
        });

        if (enabled) {
            // If a java file is selected, save and start run + watching
            CustomNode node = app.projectView.getSelectedCustomNode();
            if (node != null && !node.isDirectory && node.getFilePath() != null
                    && node.getFilePath().endsWith(".java")) {
                try {
                    // Save editor content into the file (same semantics as before)
                    try (FileWriter fw = new FileWriter(node.getFilePath())) {
                        fw.write(app.editorView.getText());
                        app.editorView.clearDirty();
                    } catch (Exception ex) {
                        javaRunManager.safeAppend(
                                "Watcher: failed saving file before starting watcher: " + ex.getMessage() + "\n");
                    }

                    // clear output panel
                    if (runOutputPanel != null)
                        runOutputPanel.clear();

                    // start run then watcher
                    javaRunManager.startRun(node.getFilePath());
                    // start watching the same file; HotReloadManager will call startRun(...) on
                    // changes
                    hotReloadManager.startWatching(new File(node.getFilePath()));

                } catch (Exception ex) {
                    javaRunManager.safeAppend("Watcher start error: " + ex.getMessage() + "\n");
                }
            } else {
                // no file selected — watcher will be ready; when user selects a file,
                // App/ProjectView will call onFileSelected(...)
            }
        } else {
            // disable watcher
            try {
                hotReloadManager.stopWatching();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Called by App/ProjectView when a file is selected in the project tree.
     * If watcher is enabled, begin watching & run the file immediately.
     */
    public void onFileSelected(CustomNode node) {
        if (!enabled || node == null)
            return;
        if (node.isDirectory)
            return;
        String path = node.getFilePath();
        if (path == null || !path.endsWith(".java"))
            return;

        // save content (same semantics)
        try {
            try (FileWriter fw = new FileWriter(path)) {
                fw.write(app.editorView.getText());
                app.editorView.clearDirty();
            } catch (Exception ex) {
                javaRunManager.safeAppend("Watcher: failed saving file on selection: " + ex.getMessage() + "\n");
            }

            if (runOutputPanel != null)
                runOutputPanel.clear();

            // start run and watch
            javaRunManager.startRun(path);
            hotReloadManager.startWatching(new File(path));

            // ensure run button shows pause (continuous)
            SwingUtilities.invokeLater(() -> {
                if (pauseIcon != null && runButton != null)
                    runButton.setIcon(pauseIcon);
            });

        } catch (Exception ex) {
            javaRunManager.safeAppend("Watcher onFileSelected error: " + ex.getMessage() + "\n");
        }
    }

    /**
     * Force-disable watcher (used by App when run button is clicked while watcher
     * ON).
     */
    public void disableIfEnabled() {
        if (this.enabled) {
            setEnabled(false);
        }
    }
}
