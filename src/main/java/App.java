
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

    public boolean darkTheme = true;
    public Font editorFont;

    public App() {
        setSize(800, 500);
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

        // toggle play / pause
        // toggle play / pause — only switch to pause if run actually started
        runButton.addActionListener(e -> {
            if (currentProcess != null) {
                stopRun();
                if (playIcon != null)
                    runButton.setIcon(playIcon);
            } else {
                boolean started = startRunForSelectedFile();
                if (started) {
                    if (pauseIcon != null)
                        runButton.setIcon(pauseIcon);
                } else {
                    // ensure it remains play icon on failure/warning
                    if (playIcon != null)
                        runButton.setIcon(playIcon);
                }
            }
        });

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
        toolPanel.add(runButton);

        projectView.addComponent();

        menuBar.add(settingsMenu);
        settingsMenu.add(newProjectItem);
        settingsMenu.add(closeProjectItem);
        settingsMenu.add(exitItem);

        this.add(rootPanel, BorderLayout.CENTER);
        this.add(welcomeView);
        setJMenuBar(menuBar);

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

    private void ensureRunOutputDialog() {
        if (runOutputDialog != null)
            return;

        runOutputArea = new JTextArea();
        runOutputArea.setEditable(false);
        runOutputArea.setFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(runOutputArea);
        scrollPane.setPreferredSize(new Dimension(800, 400));

        runOutputDialog = new JDialog(this, "Run Output", false);
        runOutputDialog.add(scrollPane);
        runOutputDialog.pack();
        runOutputDialog.setLocationRelativeTo(this);
    }

    private boolean startRunForSelectedFile() {
        CustomNode node = projectView.getSelectedCustomNode();

        if (node == null || node.isDirectory || node.getFilePath() == null || !node.getFilePath().endsWith(".java")) {
            JOptionPane.showMessageDialog(this, "Select a Java file to run");
            // do NOT change the run button icon here — caller will keep it as play
            return false;
        }

        try (FileWriter fw = new FileWriter(node.getFilePath())) {
            fw.write(editorView.getText());
            // clear dirty after explicit save
            if (editorView != null)
                editorView.clearDirty();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to save file before running:\n" + e.getMessage(), "Save Error",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        ensureRunOutputDialog();
        runOutputArea.setText("");
        runOutputDialog.setVisible(true);

        startRun(node.getFilePath());
        return true; // started
    }

    private void startRun(String javaFilePath) {
        stopRun();

        runWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                File file = new File(javaFilePath);
                String dir = file.getParent();

                publish("Compiling...\n");
                Process compile = new ProcessBuilder("javac", file.getName())
                        .directory(new File(dir))
                        .redirectErrorStream(true)
                        .start();

                currentProcess = compile;
                compile.waitFor();

                if (compile.exitValue() != 0) {
                    publish("Compilation failed\n");
                    return null;
                }

                String className = file.getName().replace(".java", "");
                publish("Running...\n");

                Process run = new ProcessBuilder("java", className)
                        .directory(new File(dir))
                        .redirectErrorStream(true)
                        .start();

                currentProcess = run;

                try (BufferedReader br = new BufferedReader(new InputStreamReader(run.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        publish(line + "\n");
                    }
                }

                run.waitFor();
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String s : chunks)
                    runOutputArea.append(s);
            }

            @Override
            protected void done() {
                runButton.setIcon(playIcon);
                currentProcess = null;
            }
        };

        runWorker.execute();
    }

    private void stopRun() {
        if (currentProcess != null) {
            currentProcess.destroyForcibly();
            currentProcess = null;
        }
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
