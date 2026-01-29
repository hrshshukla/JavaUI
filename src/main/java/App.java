
// Updated App.java
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
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

    public JPanel rightSplitPanel;
    public JPanel toolPanel;

    public JButton runButton;
    private Process currentProcess = null;
    private javax.swing.SwingWorker<Void, String> runWorker = null;

    private ImageIcon playIcon;
    private ImageIcon pauseIcon;

    private JPanel runOutputPanel;
    private JTextArea runOutputArea;
    private JScrollPane runOutputScroll;
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
        setTitle("CodeLite");
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

        // create embedded run output panel and vertical split
        ensureRunOutputPanel(); // creates runOutputPanel & area if not present

        // split editor (top) and run output (bottom)
        editorRunSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                editorView.getContentPanel(), runOutputPanel);
        editorRunSplit.setResizeWeight(0.75); // editor gets 75% of space by default
        editorRunSplit.setDividerSize(6);

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

        // Create JavaRunManager and pass callbacks:
        // - appendToRunOutput: App::appendToRunOutput
        // - onFinish: restore play icon on UI thread
        javaRunManager = new JavaRunManager(
                (msg) -> appendToRunOutput(msg),
                () -> {
                    SwingUtilities.invokeLater(() -> {
                        if (runButton != null && playIcon != null) {
                            runButton.setIcon(playIcon);
                        }
                    });
                });

        // Run button action (toggle). Only switch to pause if run actually started.
        runButton.addActionListener(e -> {
            if (currentProcess != null) {
                // user clicked Pause: stop run + watcher
                stopRun();
                hotReloadManager.stopWatching();
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

        toolPanel.add(refreshIconLabel);

        toolPanel.add(runButton);

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

        exitItem.addActionListener(e -> System.exit(0));
    }

    public void addComponent() {
        // project view components
        projectView.addComponent();

        // simple menu bar
        JMenuBar menuBar = new JMenuBar();
        JMenu settingsMenu = new JMenu("Settings");
        settingsMenu.setFont(new Font(FlatInterFont.FAMILY, Font.PLAIN, 18));
        if (settingIcon != null) {
            Image img = settingIcon.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH);
            settingsMenu.setIcon(new ImageIcon(img));
        }
        settingsMenu.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JMenuItem newProjectItem = new JMenuItem("Open new Project");
        JMenuItem closeProjectItem = new JMenuItem("Close project");
        JMenuItem exitItem = new JMenuItem("Exit CodeLite");

        newProjectItem.setFont(new Font(FlatInterFont.FAMILY, Font.PLAIN, 15));
        closeProjectItem.setFont(new Font(FlatInterFont.FAMILY, Font.PLAIN, 15));
        exitItem.setFont(new Font(FlatInterFont.FAMILY, Font.PLAIN, 15));

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

        ensureRunOutputPanel();
        runOutputArea.setText("");

        // expand the split so run output becomes visible (do this on EDT)
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

    // -------------------- run / process helpers --------------------
    private void ensureRunOutputPanel() {
        if (runOutputPanel != null)
            return;

        runOutputArea = new JTextArea();
        runOutputArea.setEditable(false);
        runOutputArea.setFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 12));
        runOutputScroll = new JScrollPane(runOutputArea);
        runOutputScroll.setPreferredSize(new Dimension(800, 200));

        // small header for the run panel (optional) with a close/hide button
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JLabel title = new JLabel("Run Output");
        title.setFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 12));
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightButtons.setOpaque(false);

        JButton hideBtn = new JButton("Hide");
        hideBtn.setFocusable(false);
        hideBtn.addActionListener(e -> {
            // collapse/hide the run panel by moving divider to bottom
            if (editorRunSplit != null) {
                editorRunSplit.setDividerLocation(1.0); // nearly fully down
            }
        });

        rightButtons.add(hideBtn);

        header.add(title, BorderLayout.WEST);
        header.add(rightButtons, BorderLayout.EAST);

        runOutputPanel = new JPanel(new BorderLayout());
        runOutputPanel.add(header, BorderLayout.NORTH);
        runOutputPanel.add(runOutputScroll, BorderLayout.CENTER);
    }

    // __________________________________________

    private void startRun(String javaFilePath) {
        javaRunManager.startRun(javaFilePath);
    }

    private void stopRun() {
        if (javaRunManager != null) {
            javaRunManager.stopRun();
        }
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
