import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/**
 * RunOutputPanel
 *
 * Encapsulates the Run Output UI:
 *  - header with title and a hide button (hide action provided by caller)
 *  - a JTextArea inside a JScrollPane
 *  - public API: getPanel(), append(String), clear(), setHideCallback(Runnable)
 *
 * Designed to be used inside a JSplitPane as the bottom component.
 */
public class RunOutputPanel {

    private final JPanel panel;
    private final JTextArea outputArea;
    private final JScrollPane scrollPane;
    private Runnable hideCallback = null;

    public RunOutputPanel() {
        panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        // text area
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 12)); // will match your FlatJetBrainsMonoFont if installed
        scrollPane = new JScrollPane(outputArea);
        scrollPane.setPreferredSize(new Dimension(800, 200));

        // header
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        header.setOpaque(false);

        JLabel title = new JLabel("Run Output");
        title.setFont(new Font("JetBrains Mono", Font.PLAIN, 18));
        
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightButtons.setOpaque(false);
        
        JButton hideBtn = new JButton("Hide");
        hideBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        hideBtn.setFont(new Font("JetBrains Mono", Font.PLAIN, 16));
        hideBtn.setFocusable(false);
        hideBtn.addActionListener(e -> {
            if (hideCallback != null) {
                try {
                    hideCallback.run();
                } catch (Exception ex) {
                    // swallow
                }
            }
        });

        rightButtons.add(hideBtn);

        header.add(title, BorderLayout.WEST);
        header.add(rightButtons, BorderLayout.EAST);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
    }

    /** Return the panel to be inserted into layouts / split pane. */
    public JPanel getPanel() {
        return panel;
    }

    /** Clear the output text. */
    public void clear() {
        SwingUtilities.invokeLater(() -> {
            outputArea.setText("");
            outputArea.setCaretPosition(0);
        });
    }

    /** Append text to output area (EDT-safe). */
    public void append(String s) {
        if (s == null) return;
        SwingUtilities.invokeLater(() -> {
            outputArea.append(s);
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    /** Set a hide callback (called when user clicks "Hide"). */
    public void setHideCallback(Runnable r) {
        this.hideCallback = r;
    }
}
