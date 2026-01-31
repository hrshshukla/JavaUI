
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;

public class App extends JFrame {

    public WelcomeView welcomeView;
    public JSplitPane rootPanel;
    public ProjectView projectView;
    public EditorView editorView;

    private HotReloadManager hotReloadManager;
    private JavaRunManager javaRunManager;
    public WatcherController watcherController;

    public JPanel rightSplitPanel;
    public JPanel toolPanel;

    public JButton runButton;
    private Process currentProcess = null;
    private javax.swing.SwingWorker<Void, String> runWorker = null;

    private ImageIcon playIcon;
    private ImageIcon pauseIcon;

    private RunOutputPanel runOutputPanelComponent;
    private JSplitPane editorRunSplit;

    private RotatingIconLabel refreshIconLabel;
    private ImageIcon refreshIcon;
    private ImageIcon settingIcon;

    public String os = System.getProperty("os.name").toLowerCase();
    public String currentFileParentPath;
    public ProcessBuilder pb;

    public JMenuItem closeProjectItem, newProjectItem,
            pythonItem, autoSaveItem,
            exitItem;

    public boolean autoSave = true;
    public Timer autoSaveTimer;

    public boolean darkTheme = true;
    public Font editorFont;

    public App() {
        setSize(1000, 650);
        setTitle("Java•UI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setLocationRelativeTo(null);
    }

    public void init() {

        playIcon = new ImageIcon(App.class.getResource("/icons/playIcon.png"));
        pauseIcon = new ImageIcon(App.class.getResource("/icons/pauseIcon.png"));
        refreshIcon = new ImageIcon(App.class.getResource("/icons/refreshIcon.png"));
        settingIcon = new ImageIcon(App.class.getResource("/icons/settingIcon.png"));
        refreshIconLabel = new RotatingIconLabel(refreshIcon);
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

        runOutputPanelComponent = new RunOutputPanel();

        // split editor (top) and run output (bottom)
        editorRunSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                editorView.getContentPanel(), runOutputPanelComponent.getPanel());
        editorRunSplit.setResizeWeight(0.75); // editor gets 75% of space by default
        editorRunSplit.setDividerSize(6);

        // wire the hide button inside RunOutputPanel to collapse the split
        runOutputPanelComponent.setHideCallback(() -> {
            if (editorRunSplit != null) {
                // move divider almost all the way down (hide the output)
                editorRunSplit.setDividerLocation(1.0);
            }
        });

        rightSplitPanel.add(editorRunSplit, BorderLayout.CENTER);

        rightSplitPanel.add(toolPanel, BorderLayout.NORTH);

        currentFileParentPath = projectView.projectPath;

        runButton = new JButton();
        runButton.setIcon(playIcon);
        runButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        runButton.setFocusable(false);
        runButton.setToolTipText("Run / Stop");
        runButton.setContentAreaFilled(false);
        runButton.setBorderPainted(false);
        runButton.setFocusPainted(false);
        runButton.setOpaque(false);

        // create hotReloadManager with callbacks that use App's methods

        hotReloadManager = new HotReloadManager(
                (filePath) -> startRun(filePath), // startRun(String)
                () -> stopRun(), // stopRun()
                (msg) -> appendToRunOutput(msg), // appendToRunOutput(String)
                () -> {
                    if (pauseIcon != null)
                        runButton.setIcon(pauseIcon);
                }, // set pause icon
                1000L, // watch poll interval (ms) - same as before
                600L // debounce (ms)
        );

        javaRunManager = new JavaRunManager(
                (msg) -> appendToRunOutput(msg),
                () -> {
                    SwingUtilities.invokeLater(() -> {
                        // Only set play icon when watcher is NOT enabled
                        if (watcherController == null || !watcherController.isEnabled()) {
                            if (runButton != null && playIcon != null) {
                                runButton.setIcon(playIcon);
                            }
                        }
                    });
                });

        ImageIcon eyeOpenIcon = new ImageIcon(App.class.getResource("/icons/eyeOpen.png"));
        ImageIcon eyeCloseIcon = new ImageIcon(App.class.getResource("/icons/eyeClose.png"));

        watcherController = new WatcherController(
                this,
                hotReloadManager,
                javaRunManager,
                runOutputPanelComponent,
                runButton,
                eyeOpenIcon,
                eyeCloseIcon,
                pauseIcon,
                playIcon);

        runButton.addActionListener(e -> {
            // If watcher is ON, clicking run should TURN OFF watcher first
            if (watcherController != null && watcherController.isEnabled()) {
                watcherController.disableIfEnabled();
                // continue after disabling watcher
            }

            // Now do normal run toggle (watcher is definitely OFF here)
            if (javaRunManager.isRunning()) {
                javaRunManager.stopRun();
                if (playIcon != null)
                    runButton.setIcon(playIcon);
            } else {
                // Start run for selected file (but DO NOT start watcher)
                CustomNode node = projectView.getSelectedCustomNode();
                if (node == null || node.isDirectory || node.getFilePath() == null
                        || !node.getFilePath().endsWith(".java")) {
                    JOptionPane.showMessageDialog(this, "Select a Java file to run", "No File Selected",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }

                // save
                try (FileWriter fw = new FileWriter(new File(node.getFilePath()))) {
                    fw.write(editorView.getText());
                    editorView.clearDirty();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Failed to save file before running:\n" + ex.getMessage(),
                            "Save Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (runOutputPanelComponent == null)
                    runOutputPanelComponent = new RunOutputPanel();
                runOutputPanelComponent.clear();

                javaRunManager.startRun(node.getFilePath());
                // set pause icon
                if (pauseIcon != null)
                    runButton.setIcon(pauseIcon);
            }
        });

        toolPanel.add(watcherController.getButton());
        toolPanel.add(refreshIconLabel);
        toolPanel.add(runButton);

        autoSave = true;

        autoSaveTimer = new Timer(2000, e -> {
            if (projectView != null && projectView.getSelectedCustomNode() != null) {
                CustomNode node = projectView.getSelectedCustomNode();

                if (!node.isDirectory && editorView != null && editorView.isDirty()) {
                    projectView.saveFile();

                    // 🔄 rotate refresh icon once on autosave
                    if (refreshIconLabel != null) {
                        refreshIconLabel.rotateOnce();
                    }
                }
            }
        });

        autoSaveTimer.start();
    }

    public void addComponent() {
        // project view components
        projectView.addComponent();

        // menu bar
        JMenuBar menuBar = new JMenuBar();
        JMenu settingsMenu = new JMenu("Settings");
        settingsMenu.setCursor(new Cursor(Cursor.HAND_CURSOR));
        settingsMenu.setFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 18));

        if (settingIcon != null) {
            Image img = settingIcon.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH);
            settingsMenu.setIcon(new ImageIcon(img));
        }
        settingsMenu.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JMenuItem newProjectItem = new JMenuItem("Open new Project");
        JMenuItem closeProjectItem = new JMenuItem("Close project");
        JMenuItem exitItem = new JMenuItem("Exit Java•UI");

        newProjectItem.setFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 15));
        closeProjectItem.setFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 15));
        exitItem.setFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 15));

        newProjectItem.addActionListener(e -> {
            boolean opened = projectView.openProject();
            if (opened) {
                SwingUtilities.invokeLater(() -> {
                    launch();
                    revalidate();
                    repaint();
                });
            }
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

        // split panel
        this.add(rootPanel, BorderLayout.CENTER);

        // show welcome
        setContentPane(welcomeView);

        // editor placeholder
        editorView.showNoFileSelected();

        revalidate();
        repaint();
        setVisible(true);

        PassThroughGlassPane glassPane = new PassThroughGlassPane(this, " by Harsh Shukla");
        setGlassPane(glassPane);
        glassPane.setVisible(true);

        // optional extra safety (not strictly required with new class)
        SwingUtilities.invokeLater(() -> {
            glassPane.setBounds(0, 0, getRootPane().getWidth(), getRootPane().getHeight());
            glassPane.revalidate();
            glassPane.repaint();
        });

    }

    public void launch() {
        setContentPane(rootPanel);

        this.setExtendedState(MAXIMIZED_BOTH);
        editorView.setColorScheme("Monokai");
        editorView.showNoFileSelected();
    }

    private boolean startRunForSelectedFile_andStartWatcher() {
        CustomNode node = projectView.getSelectedCustomNode();
        if (node == null || node.isDirectory || node.getFilePath() == null || !node.getFilePath().endsWith(".java")) {
            JOptionPane.showMessageDialog(this, "Select a Java file to run", "No File Selected",
                    JOptionPane.WARNING_MESSAGE);
            return false;
        }

        try (FileWriter fw = new FileWriter(new File(node.getFilePath()))) {
            fw.write(editorView.getText());
            if (editorView != null)
                editorView.clearDirty();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save file before running:\n" + ex.getMessage(), "Save Error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (runOutputPanelComponent == null) {
            runOutputPanelComponent = new RunOutputPanel();
        }
        runOutputPanelComponent.clear();

        SwingUtilities.invokeLater(() -> {
            if (editorRunSplit != null) {
                int h = editorRunSplit.getHeight() > 0 ? editorRunSplit.getHeight() : App.this.getHeight();
                int loc = (int) (h * 0.65); // editor 65% / output 35%
                editorRunSplit.setDividerLocation(loc);
            }
        });

        // start run
        startRun(node.getFilePath());

        // start watcher for hot-reload (keep watching while running)
        hotReloadManager.startWatching(new File(node.getFilePath()));

        return true;
    }

    private void startRun(String javaFilePath) {
        javaRunManager.startRun(javaFilePath);
    }

    private void stopRun() {
        if (javaRunManager != null) {
            javaRunManager.stopRun();
        }
    }

    private void appendToRunOutput(String s) {
        if (runOutputPanelComponent == null)
            return;
        runOutputPanelComponent.append(s);
    }

    public static void main(String[] args) {
        FlatMacDarkLaf.setup();

        UIManager.put("Component.focusColor", new Color(90, 90, 90)); // main focus ring
        UIManager.put("Component.focusedBorderColor", new Color(90, 90, 90));
        UIManager.put("Component.focusedBorderWidth", 1);

        UIManager.put("TextComponent.focusedBorderColor", new Color(90, 90, 90));

        UIManager.put("Tree.selectionBorderColor", new Color(90, 90, 90));

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
