import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Scanner;

public class ProjectView extends JPanel {

    private final App app;

    public CustomNode root;
    private JTree projectTree;
    private JScrollPane projectScrollPane;
    public String directoryPath;

    public ArrayList<CustomNode> projectFiles = new ArrayList<>();
    public String projectPath = null;

    private static Icon folderIcon;
    private static Icon javaFileIcon;
    private static Icon defaultFileIcon;

    // popup menu for create/rename/delete
    private JPopupMenu popupMenu;

    public ProjectView(App app) {
        this.app = app;
        this.setPreferredSize(new Dimension(300, 1200));

        setLayout(new BorderLayout());
    }

    public void init() {
        root = new CustomNode("Project", null, projectPath);
        root.isDirectory = true;
        projectTree = new JTree(root);
        projectTree.setFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 16));

        projectTree.setRowHeight(28);

        projectScrollPane = new JScrollPane(projectTree);

        folderIcon = new ImageIcon(Objects.requireNonNull(ProjectView.class.getResource("/icons/folder_icon_24.png")));
        // optional icons (make sure these files exist in resources/icons)
        try {
            javaFileIcon = new ImageIcon(Objects.requireNonNull(ProjectView.class.getResource("/icons/javaIcon.png")));
        } catch (Exception ex) {
            javaFileIcon = null;
        }
        try {
            defaultFileIcon = new ImageIcon(
                    Objects.requireNonNull(ProjectView.class.getResource("/icons/defaultIcon.png")));
        } catch (Exception ex) {
            defaultFileIcon = null;
        }

        refreshTree();

        // create popup menu (right-click)
        createPopupMenu();
    }

    public void initActionListeners() {
        projectTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                try {
                    DefaultMutableTreeNode sel = (DefaultMutableTreeNode) projectTree.getLastSelectedPathComponent();
                    if (sel == null)
                        return;
                    if (!(sel instanceof CustomNode))
                        return;
                    CustomNode node = (CustomNode) sel;

                    if (node.isDirectory) {
                        // Folder selected → keep editor in "No files selected"
                        app.editorView.showNoFileSelected();
                        app.setTitle("CodeLite");
                        return;
                    }

                    // File selected: load into editor
                    // If you prefer double-click-only open, check e.getClickCount() >= 2
                    setEditorContent(app.editorView, node);
                    app.editorView.showEditor();

                    CustomNode parentNode = (CustomNode) node.getParent();
                    if (parentNode != null) {
                        app.currentFileParentPath = parentNode.getFilePath();
                    }

                } catch (NullPointerException pointerException) {
                    System.out.println("No File Selected");
                } catch (ClassCastException classCastException) {
                    System.out.println("Exception");
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    int row = projectTree.getClosestRowForLocation(e.getX(), e.getY());
                    projectTree.setSelectionRow(row);
                    // show popup
                    if (popupMenu != null) {
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });
    }

    public void setEditorContent(EditorView editorView, CustomNode node) {
        editorView.setText(node.getContent());
        editorView.clearDirty();
        app.setTitle("CodeLite - " + node.getNodeName());
    }

    public void addComponent() {
        this.add(projectScrollPane, BorderLayout.CENTER);
    }

    public void openProject() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setApproveButtonText("Open Folder");

        int result = chooser.showOpenDialog(null);
        File file = chooser.getSelectedFile();

        if (result == JFileChooser.APPROVE_OPTION) {

            if (file == null || !file.isDirectory()) {
                JOptionPane.showMessageDialog(
                        null,
                        "Please select a folder (project directory)",
                        "Invalid Selection",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            projectPath = file.getAbsolutePath();

            String folderName = file.getName();
            root.setNodeName(folderName);
            root.setFilePath(projectPath);
            root.isDirectory = true;
            root.removeAllChildren();

            openDirectory(file);
        }

        // expand root for convenience
        SwingUtilities.invokeLater(() -> projectTree.expandRow(0));
    }

    public void openDirectory(File inputFile) {
        // clear previous children of root to avoid duplicates on multiple opens
        root.removeAllChildren();
        projectFiles.clear();
        openDirectoryRecursive(inputFile, root);
        DefaultTreeModel treeModel = (DefaultTreeModel) projectTree.getModel();
        treeModel.reload();

        // clear any selection that might have been created during tree reload/expansion
        projectTree.clearSelection();

        // ensure editor stays in "No files selected" state after opening a project
        if (app != null && app.editorView != null) {
            app.editorView.showNoFileSelected();
        }
    }

    private void openDirectoryRecursive(File inputFile, DefaultMutableTreeNode parentNode) {
        File[] files = inputFile.listFiles();
        if (files != null) {
            for (File file : files) {
                CustomNode node;
                if (file.isDirectory()) {
                    node = new CustomNode(file.getName(), "", file.getAbsolutePath());
                    node.isDirectory = true;
                    openDirectoryRecursive(file, node);
                } else {
                    try {
                        Scanner scanner = new Scanner(file);
                        StringBuilder data = new StringBuilder();
                        while (scanner.hasNextLine()) {
                            data.append(scanner.nextLine()).append("\n");
                        }
                        scanner.close();
                        node = new CustomNode(file.getName(), data.toString(), file.getAbsolutePath());
                        node.isDirectory = false;
                        projectFiles.add(node);

                    } catch (FileNotFoundException e) {
                        System.err.println("File not found: " + file.getAbsolutePath());
                        continue;
                    }
                }
                parentNode.add(node);
            }
        }
    }

    /**
     * Save current file (used by autosave). Keeps behavior same as before.
     */
    public void saveFile() {
        CustomNode node = (CustomNode) projectTree.getLastSelectedPathComponent();
        if (node == null)
            return;

        // ADD THIS CHECK
        if (app.editorView == null || !app.editorView.isDirty()) {
            return;
        }

        node.setContent(app.editorView.getText());

        File savedFile = new File(node.getFilePath());
        try (FileWriter writer = new FileWriter(savedFile)) {
            writer.write(node.getContent());
            app.editorView.clearDirty(); // ADD THIS
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Error saving file: " + savedFile.getName() + "\n" + e.getMessage(),
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Popup menu creation for New File / New Folder / Rename / Delete
     */
    private void createPopupMenu() {
        popupMenu = new JPopupMenu();

        JMenuItem addFileItem = new JMenuItem("New File");

        JMenuItem addFolderItem = new JMenuItem("New Folder");
        JMenuItem renameItem = new JMenuItem("Rename");
        JMenuItem deleteItem = new JMenuItem("Delete");

        addFileItem.setFont(new Font("JetBrains Mono", Font.PLAIN, 16));
        addFolderItem.setFont(new Font("JetBrains Mono", Font.PLAIN, 16));
        deleteItem.setFont(new Font("JetBrains Mono", Font.PLAIN, 16));
        renameItem.setFont(new Font("JetBrains Mono", Font.PLAIN, 16));

        addFileItem.addActionListener(e -> createFile(false));
        addFolderItem.addActionListener(e -> createFile(true));
        renameItem.addActionListener(e -> renameFile());
        deleteItem.addActionListener(e -> deleteFile());

        popupMenu.add(addFileItem);
        popupMenu.add(addFolderItem);
        popupMenu.addSeparator();
        popupMenu.add(renameItem);
        popupMenu.add(deleteItem);
    }

    /**
     * Create a new file or folder inside the selected folder (or project root if
     * nothing selected).
     */
    private void createFile(boolean isDirectory) {
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) projectTree.getLastSelectedPathComponent();
        CustomNode parentNode = null;

        if (selected instanceof CustomNode) {
            parentNode = (CustomNode) selected;
        } else {
            parentNode = root;
        }

        // if user selected a file, use its parent
        if (!parentNode.isDirectory) {
            parentNode = (CustomNode) parentNode.getParent();
            if (parentNode == null)
                parentNode = root;
        }

        String name = JOptionPane.showInputDialog(null, isDirectory ? "New folder name" : "New file name");

        if (name == null)
            return;
        name = name.trim();
        if (name.isEmpty())
            return;

        File parentFile = new File(parentNode.getFilePath() != null ? parentNode.getFilePath() : projectPath);
        if (!parentFile.exists())
            parentFile = new File(projectPath);

        File newFile = new File(parentFile, name);

        try {
            boolean ok;
            if (isDirectory) {
                ok = newFile.mkdir();
            } else {
                ok = newFile.createNewFile();
            }
            if (!ok) {
                JOptionPane.showMessageDialog(this, "Unable to create " + (isDirectory ? "folder" : "file"),
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Add to tree
            CustomNode newNode = new CustomNode(name, isDirectory ? null : "", newFile.getAbsolutePath());
            newNode.isDirectory = isDirectory;
            parentNode.add(newNode);

            DefaultTreeModel model = (DefaultTreeModel) projectTree.getModel();
            model.reload(parentNode);

            // Optional: expand parent to show new child
            TreePath path = new TreePath(parentNode.getPath());
            projectTree.expandPath(path);

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error creating file/folder: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Delete selected file or folder (asks confirmation). Deletes recursively for
     * folders.
     */
    private void deleteFile() {
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) projectTree.getLastSelectedPathComponent();
        if (!(selected instanceof CustomNode))
            return;

        CustomNode selNode = (CustomNode) selected;
        int choice = JOptionPane.showConfirmDialog(null,
                "Are you sure you want to delete \"" + selNode.getNodeName() + "\"?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION)
            return;

        File target = new File(selNode.getFilePath());
        if (!target.exists()) {
            JOptionPane.showMessageDialog(this, "File/folder does not exist on disk.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean deleted;
        if (target.isDirectory()) {
            deleted = deleteDirectory(target);
        } else {
            deleted = target.delete();
        }

        if (!deleted) {
            JOptionPane.showMessageDialog(this, "Could not delete the file/folder", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // If deleted node was the currently open file, close editor
        CustomNode current = (CustomNode) projectTree.getLastSelectedPathComponent();
        if (current != null && !current.isDirectory) {
            String openPath = app.editorView.getText() != null ? selNode.getFilePath() : null;
            // crude check: if paths match, close editor
            if (openPath != null && openPath.equals(selNode.getFilePath())) {
                app.editorView.showNoFileSelected();
            }
        }

        DefaultTreeModel model = (DefaultTreeModel) projectTree.getModel();
        model.removeNodeFromParent(selNode);
        model.reload();
    }

    private boolean deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        return directory.delete();
    }

    /**
     * Rename selected file (files only). For folders this shows an error (you can
     * enable if you want).
     */
    private void renameFile() {
        DefaultMutableTreeNode selected = (DefaultMutableTreeNode) projectTree.getLastSelectedPathComponent();
        if (!(selected instanceof CustomNode))
            return;
        CustomNode selNode = (CustomNode) selected;

        String newName = JOptionPane.showInputDialog(null, "Enter new name", selNode.getNodeName());
        if (newName == null)
            return;
        newName = newName.trim();
        if (newName.isEmpty())
            return;

        File currentFile = new File(selNode.getFilePath());
        File parent = currentFile.getParentFile();
        if (parent == null) {
            JOptionPane.showMessageDialog(this, "Cannot rename root", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        File newFile = new File(parent, newName);

        if (currentFile.renameTo(newFile)) {
            selNode.setNodeName(newName);
            selNode.setFilePath(newFile.getAbsolutePath());

            DefaultTreeModel model = (DefaultTreeModel) projectTree.getModel();
            model.nodeChanged(selNode);
            model.reload();

            // If renamed file was open, update editor title/path
            app.setTitle("CodeLite - " + newName);
        } else {
            JOptionPane.showMessageDialog(this, "Could not rename the file", "Rename Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void refreshTree() {
        DefaultTreeModel model = (DefaultTreeModel) projectTree.getModel();
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(
                    JTree tree, Object value, boolean sel,
                    boolean expanded, boolean leaf, int row, boolean hasFocus) {

                super.getTreeCellRendererComponent(
                        tree, value, sel, expanded, leaf, row, hasFocus);

                if (value instanceof CustomNode node) {

                    // Folder icon
                    if (node.isDirectory) {
                        setIcon(folderIcon);
                    }
                    // Java file icon
                    else if (node.getNodeName().endsWith(".java") && javaFileIcon != null) {
                        setIcon(javaFileIcon);
                    }
                    // Default file icon
                    else if (defaultFileIcon != null) {
                        setIcon(defaultFileIcon);
                    }
                }

                // Tailwind-like spacing
                setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

                return this;
            }
        };

        projectTree.setCellRenderer(renderer);
        model.reload();
    }

    public JTree getProjectTree() {
        return projectTree;
    }

    public CustomNode getSelectedCustomNode() {
        return (CustomNode) projectTree.getLastSelectedPathComponent();
    }

}
