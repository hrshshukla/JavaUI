
// Updated App.java
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class App extends JFrame {

    public WelcomeView welcomeView;
    public JSplitPane rootPanel;
    public ProjectView projectView;
    public EditorView editorView;

    public JPanel rightSplitPanel;
    public JPanel toolPanel;

    public JButton runButton;
    private Process currentProcess = null;
    private javax.swing.SwingWorker<Void, String> runWorker = null;
    private JDialog runOutputDialog;
    private JTextArea runOutputArea;
    private ImageIcon playIcon;
    private ImageIcon pauseIcon;

    public String os = System.getProperty("os.name").toLowerCase();
    public String currentFileParentPath;
    public ProcessBuilder pb;

    public JMenuBar menuBar;
    public JMenu settingsMenu;
    public JMenuItem closeProjectItem, newProjectItem,
            pythonItem, autoSaveItem,
            exitItem;

    public boolean autoSave = true;
    public Timer autoSaveTimer;

    // file watcher (hot-reload)
    private ScheduledExecutorService watcherExecutor = null;
    private ScheduledFuture<?> watcherTask = null;
    private ScheduledFuture<?> pendingRestartTask = null;
    private volatile File watchedFile = null;
    private volatile long watchedLastModified = 0L;
    private final long WATCH_POLL_INTERVAL_MS = 1000L; // 1s
    private final long RESTART_DEBOUNCE_MS = 600L; // 600ms debounce

    public boolean darkTheme = true;
    public Font editorFont;

    public App() {
        setSize(1000, 650);
        setTitle("CodeLite");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null);
    }

    public void init() {
        editorFont = new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 18);

        welcomeView = new WelcomeView(this);

        projectView = new ProjectView(this);
        projectView.setMinimumSize(new Dimension(200, 0));
        projectView.init();
        projectView.initActionListeners();

        editorView = new EditorView(this);

        rightSplitPanel = new JPanel();

        toolPanel = new JPanel();
        rootPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, projectView, rightSplitPanel);

        rightSplitPanel.setLayout(new BorderLayout());
        toolPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 0, 0)); // RIGHT alignment
        toolPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        rightSplitPanel.add(editorView.getContentPanel(), BorderLayout.CENTER);
        rightSplitPanel.add(toolPanel, BorderLayout.NORTH);

        currentFileParentPath = projectView.projectPath;

        playIcon = new ImageIcon(App.class.getResource("/icons/playIcon.png"));
        pauseIcon = new ImageIcon(App.class.getResource("/icons/pauseIcon.png"));

        runButton = new JButton();
        runButton.setIcon(playIcon);
        runButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        runButton.setFocusable(false);
        runButton.setToolTipText("Run / Stop");
        runButton.setContentAreaFilled(false);
        runButton.setBorderPainted(false);
        runButton.setFocusPainted(false);
        runButton.setOpaque(false);

        // Run button action (toggle). Only switch to pause if run actually started.
        runButton.addActionListener(e -> {
            if (currentProcess != null) {
                // user clicked Pause: stop run + watcher
                stopRun();
                stopFileWatcher();
                if (playIcon != null)
                    runButton.setIcon(playIcon);
            } else {
                // attempt to start run - method returns true only if run started
                boolean started = startRunForSelectedFile_andStartWatcher();
                if (started) {
                    if (pauseIcon != null)
                        runButton.setIcon(pauseIcon);
                } else {
                    if (playIcon != null)
                        runButton.setIcon(playIcon);
                }
            }
        });

        toolPanel.add(runButton);

        menuBar = new JMenuBar();
        settingsMenu = new JMenu("Settings", true);

        newProjectItem = new JMenuItem("Open new Project");
        closeProjectItem = new JMenuItem("Close project");

        exitItem = new JMenuItem("Exit CodeLite");

        newProjectItem.addActionListener(e -> {
            projectView.getProjectTree().removeAll();
            projectView.root.removeAllChildren();
            projectView.openProject();
        });

        closeProjectItem.addActionListener(e -> {
            projectView.getProjectTree().removeAll();
            projectView.root.removeAllChildren();
            projectView.projectFiles.clear();

            setContentPane(welcomeView);
            this.setSize(800, 500);
            this.setLocationRelativeTo(null);
        });

        autoSave = true;

        autoSaveTimer = new Timer(1000, e -> {
            if (projectView != null && projectView.getSelectedCustomNode() != null) {
                CustomNode node = projectView.getSelectedCustomNode();

                if (!node.isDirectory && editorView != null && editorView.isDirty()) {
                    projectView.saveFile();
                }
            }
        });

        autoSaveTimer.start();

        exitItem.addActionListener(e -> System.exit(0));
    }

    public void addComponent() {
        // project view components
        projectView.addComponent();

        // simple menu bar
        JMenuBar menuBar = new JMenuBar();
        JMenu settingsMenu = new JMenu("Settings");
        JMenuItem newProjectItem = new JMenuItem("Open new Project");
        JMenuItem closeProjectItem = new JMenuItem("Close project");
        JMenuItem exitItem = new JMenuItem("Exit CodeLite");

        newProjectItem.addActionListener(e -> {
            projectView.getProjectTree().removeAll();
            if (projectView.root != null)
                projectView.root.removeAllChildren();
            projectView.openProject();
        });

        closeProjectItem.addActionListener(e -> {
            projectView.getProjectTree().removeAll();
            if (projectView.root != null)
                projectView.root.removeAllChildren();
            projectView.projectFiles.clear();

            setContentPane(welcomeView);
            this.setSize(800, 500);
            this.setLocationRelativeTo(null);
        });

        exitItem.addActionListener(e -> System.exit(0));

        settingsMenu.add(newProjectItem);
        settingsMenu.add(closeProjectItem);
        settingsMenu.addSeparator();
        settingsMenu.add(exitItem);
        menuBar.add(settingsMenu);
        setJMenuBar(menuBar);

        // add main split pane
        this.add(rootPanel, BorderLayout.CENTER);

        // show welcome first (optional). Call launch() to show project + editor.
        setContentPane(welcomeView);

        // ensure editor placeholder state is set
        editorView.showNoFileSelected();

        revalidate();
        repaint();
        setVisible(true);
    }

    public void launch() {
        setContentPane(rootPanel);

        this.setExtendedState(MAXIMIZED_BOTH);
        editorView.setColorScheme("Monokai");
        editorView.showNoFileSelected();
    }

    /*
     * Starts run for selected Java file and starts the file watcher for hot-reload.
     * Returns true if run started, false otherwise.
     */

    private boolean startRunForSelectedFile_andStartWatcher() {
        CustomNode node = projectView.getSelectedCustomNode();
        if (node == null || node.isDirectory || node.getFilePath() == null || !node.getFilePath().endsWith(".java")) {
            JOptionPane.showMessageDialog(this, "Select a Java file to run", "No File Selected",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }

        // save current editor text before running

        try (FileWriter fw = new FileWriter(new File(node.getFilePath()))) {
            fw.write(editorView.getText());
            if (editorView != null)
                editorView.clearDirty();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save file before running:\n" + ex.getMessage(), "Save Error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        ensureRunOutputDialog();
        runOutputArea.setText("");
        runOutputDialog.setVisible(true);

        // start run
        startRun(node.getFilePath());

        // start watcher for hot-reload (keep watching while running)
        startFileWatcher(new File(node.getFilePath()));

        return true;
    }

    // -------------------- file watcher (poll + debounce) --------------------
    private synchronized void startFileWatcher(File file) {
        // if same file already watched, just refresh lastModified and return
        if (file == null)
            return;

        if (watchedFile != null && watchedFile.getAbsolutePath().equals(file.getAbsolutePath())) {
            watchedLastModified = file.lastModified();
            return;
        }

        stopFileWatcher(); // cleanup any existing watcher

        watchedFile = file;
        watchedLastModified = file.exists() ? file.lastModified() : 0L;
        watcherExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "file-watcher-thread");
            t.setDaemon(true);
            return t;
        });

        watcherTask = watcherExecutor.scheduleAtFixedRate(() -> {
            try {
                if (watchedFile == null)
                    return;
                long lm = watchedFile.exists() ? watchedFile.lastModified() : 0L;
                if (lm == 0L)
                    return; // file missing
                if (lm > watchedLastModified) {
                    watchedLastModified = lm;
                    // debounce restart a little: schedule restart after RESTART_DEBOUNCE_MS,
                    // cancel previous pending restart if present.
                    if (pendingRestartTask != null && !pendingRestartTask.isDone()) {
                        pendingRestartTask.cancel(false);
                    }
                    pendingRestartTask = watcherExecutor.schedule(() -> {
                        // on file change: stop current process and start a fresh run
                        // ensure UI updates happen on EDT for icons
                        try {
                            stopRun(); // stops current process & worker
                            // startRun will spawn a worker and run; it is safe to call from this thread
                            startRun(watchedFile.getAbsolutePath());
                            SwingUtilities.invokeLater(() -> {
                                if (pauseIcon != null)
                                    runButton.setIcon(pauseIcon);
                            });
                        } catch (Exception ex) {
                            appendToRunOutput("Hot-reload failed: " + ex.getMessage() + "\n");
                        }
                    }, RESTART_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
                }
            } catch (Exception ex) {
                // swallow and log to output
                appendToRunOutput("Watcher error: " + ex.getMessage() + "\n");
            }
        }, WATCH_POLL_INTERVAL_MS, WATCH_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopFileWatcher() {
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
            try {
                watcherExecutor.shutdownNow();
            } catch (Exception ignored) {
            }
            watcherExecutor = null;
        }
    }

    // -------------------- run / process helpers --------------------
    private void ensureRunOutputDialog() {
        if (runOutputDialog != null)
            return;

        runOutputArea = new JTextArea();
        runOutputArea.setEditable(false);
        runOutputArea.setFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(runOutputArea);
        sp.setPreferredSize(new Dimension(800, 400));

        runOutputDialog = new JDialog(this, "Run Output", false);
        runOutputDialog.getContentPane().setLayout(new BorderLayout());
        runOutputDialog.getContentPane().add(sp, BorderLayout.CENTER);
        runOutputDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        runOutputDialog.pack();
        runOutputDialog.setLocationRelativeTo(this);
    }

    private void startRun(String javaFilePath) {
        stopRun(); // ensure previous stopped

        runWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                File javaFile = new File(javaFilePath);
                String parentDir = javaFile.getParentFile().getAbsolutePath();

                publish("Compiling: " + javaFile.getName() + "\n");
                ProcessBuilder compilePb = new ProcessBuilder("javac", javaFile.getName());
                compilePb.directory(new File(parentDir));
                compilePb.redirectErrorStream(true);
                Process compileProcess = compilePb.start();

                currentProcess = compileProcess;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        publish(line + "\n");
                    }
                }
                int compileExit = compileProcess.waitFor();
                currentProcess = null;

                if (compileExit != 0) {
                    publish("Compilation failed (exit " + compileExit + ")\n");
                    return null;
                }
                publish("Compilation succeeded.\n");

                // detect package if present
                String className = javaFile.getName().replaceAll("\\.java$", "");
                String packageName = null;
                try (Scanner sc = new Scanner(javaFile)) {
                    while (sc.hasNextLine()) {
                        String l = sc.nextLine().trim();
                        if (l.startsWith("package ")) {
                            packageName = l.substring(8).replace(";", "").trim();
                            break;
                        }
                    }
                } catch (Exception ignored) {
                }
                String fqName = (packageName != null && !packageName.isEmpty()) ? (packageName + "." + className)
                        : className;

                publish("Running: java -cp " + parentDir + " " + fqName + "\n");
                ProcessBuilder runPb = new ProcessBuilder("java", "-cp", parentDir, fqName);
                runPb.directory(new File(parentDir));
                runPb.redirectErrorStream(true);
                Process runProcess = runPb.start();

                currentProcess = runProcess;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(runProcess.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        publish(line + "\n");
                    }
                }
                int runExit = runProcess.waitFor();
                currentProcess = null;
                publish("Process exited with code " + runExit + "\n");
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String s : chunks)
                    appendToRunOutput(s);
            }

            @Override
            protected void done() {
                if (runButton != null && playIcon != null)
                    runButton.setIcon(playIcon);
                currentProcess = null;
                runWorker = null;
            }
        };

        runWorker.execute();
    }

    private void stopRun() {
        if (runWorker != null) {
            runWorker.cancel(true);
            runWorker = null;
        }
        if (currentProcess != null) {
            try {
                currentProcess.destroyForcibly();
            } catch (Exception ignored) {
            } finally {
                currentProcess = null;
            }
        }
        appendToRunOutput("\nProcess stopped by user.\n");
    }

    private void appendToRunOutput(String s) {
        if (runOutputArea == null)
            return;
        SwingUtilities.invokeLater(() -> {
            runOutputArea.append(s);
            runOutputArea.setCaretPosition(runOutputArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        FlatMacDarkLaf.setup();

        UIManager.put("Component.focusColor", new Color(120, 120, 120)); // main focus ring
        UIManager.put("Component.focusedBorderColor", new Color(120, 120, 120));
        UIManager.put("Component.focusedBorderWidth", 1);

        UIManager.put("TextComponent.focusedBorderColor", new Color(120, 120, 120));

        UIManager.put("Tree.selectionBorderColor", new Color(120, 120, 120));

        FlatJetBrainsMonoFont.install();
        FlatInterFont.install();

        UIManager.put("defaultFont", new Font(FlatInterFont.FAMILY, Font.PLAIN, 13));

        SwingUtilities.invokeLater(() -> {

            App app = new App();
            app.init();
            app.addComponent();
        });
    }
}
