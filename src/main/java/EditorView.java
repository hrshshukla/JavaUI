
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import org.fife.ui.rsyntaxtextarea.*;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStream;

public class EditorView extends RSyntaxTextArea implements KeyListener {
    private JPanel containerPanel;
    private CardLayout cardLayout;
    private boolean dirty = false;

    private final RTextScrollPane editorScrollPane;

    InputStream monokaiStream;
    Theme monokaiTheme;
    private final App app;

    // placeholder label shown when a folder (not a file) is selected
    private final JLabel placeholderLabel;

    public EditorView(App app) {
        super();
        this.app = app;
        this.addKeyListener(this);

        monokaiStream = EditorView.class.getResourceAsStream("/themes/monokai.xml");

        try {
            if (monokaiStream != null) {
                monokaiTheme = Theme.load(monokaiStream);
            } else {
                monokaiTheme = null;
            }
        } catch (IOException e) {
            System.err.println("Failed loading Monokai theme: " + e.getMessage());
            monokaiTheme = null;
        }

        // default to Java syntax
        this.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        this.setCodeFoldingEnabled(true);
        this.setRoundedSelectionEdges(true);

        // font default
        this.setFont(app != null && app.editorFont != null ? app.editorFont
                : new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 12));

        this.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                dirty = true;
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                dirty = true;
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                dirty = true;
            }
        });

        // scroll pane wrapping the text area
        editorScrollPane = new RTextScrollPane(this);
        // Increase line number font size
        editorScrollPane.getGutter().setLineNumberFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 24));

        // placeholder components (logo above text)
        placeholderLabel = new JLabel("No files selected", SwingConstants.CENTER);
        placeholderLabel.setFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 18));
        placeholderLabel.setForeground(new Color(255, 255, 255, 100));
        placeholderLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        placeholderLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // try common resource paths for the logo
        ImageIcon logoIcon = null;
        java.net.URL logoUrl = EditorView.class.getResource("/resources/icons/bw-logo.png");
        if (logoUrl == null)
            logoUrl = EditorView.class.getResource("/icons/bw-logo.png");
        if (logoUrl != null) {
            Image img = new ImageIcon(logoUrl).getImage();
            
            Image scaled = img.getScaledInstance(128, 128, Image.SCALE_SMOOTH);
            logoIcon = new ImageIcon(scaled);
        }

        JLabel logoLabel = new JLabel();
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (logoIcon != null) {
            logoLabel.setIcon(logoIcon);
        } else {
            // fallback text if resource not found (optional)
            logoLabel.setText("");
            logoLabel.setPreferredSize(new Dimension(128, 128));
            
        }

        // vertical panel to center logo above the text
        JPanel placeholderPanel = new JPanel();
        placeholderPanel.setLayout(new BoxLayout(placeholderPanel, BoxLayout.Y_AXIS));
        placeholderPanel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        placeholderPanel.add(logoLabel);
        placeholderPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        placeholderPanel.add(placeholderLabel);

        cardLayout = new CardLayout();
        containerPanel = new JPanel(cardLayout);

        containerPanel.add(editorScrollPane, "EDITOR");
        JPanel emptyWrapper = new JPanel(new GridBagLayout());
        emptyWrapper.add(placeholderPanel); // auto center (horizontal + vertical)

        containerPanel.add(emptyWrapper, "EMPTY");

        cardLayout.show(containerPanel, "EMPTY");

        updateLineNumberFont(this.getFont().getSize());
    }

    private void updateLineNumberFont(int size) {
        if (editorScrollPane != null) {
            editorScrollPane.getGutter().setLineNumberFont(
                    new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, size));
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public void setColorScheme(String colorScheme) {
        // Only Monokai supported; ignore other names and apply monokai if loaded.
        if (monokaiTheme != null) {
            monokaiTheme.apply(this);
        }
        this.setFont(app.editorFont);
        updateLineNumberFont(this.getFont().getSize());
    }

    public void showNoFileSelected() {
        cardLayout.show(containerPanel, "EMPTY");
        this.setEditable(false);

    }

    public void showEditor() {
        cardLayout.show(containerPanel, "EDITOR");
        this.setEditable(true);

    }

    public JComponent getContentPanel() {
        return containerPanel;
    }

    private int fontSize = 12;
    private static final int MIN_FONT_SIZE = 12;
    private static final int MAX_FONT_SIZE = 72;

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.isControlDown()) {
            if (e.getKeyCode() == KeyEvent.VK_PLUS || e.getKeyCode() == KeyEvent.VK_EQUALS) {
                if (fontSize < MAX_FONT_SIZE) {
                    fontSize++;
                }
            } else if (e.getKeyCode() == KeyEvent.VK_MINUS) {
                if (fontSize > MIN_FONT_SIZE) {
                    fontSize--;
                }
            }

            Font newFont = new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, fontSize);
            this.setFont(newFont);

            updateLineNumberFont(fontSize);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

}
