import javax.swing.*;

import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;

import java.awt.*;

/**
 * PassThroughGlassPane
 *
 * A transparent glass pane that:
 * - stays visible on all screens
 * - does NOT block mouse events (pass-through)
 * - can be used to show watermark / signature text
 *
 * This version ensures the glass pane gets proper bounds and repositions label
 * after the frame is shown and on resize.
 */
public class PassThroughGlassPane extends JComponent {

    private final JLabel signatureLabel;

    public PassThroughGlassPane(JFrame frame, String signatureText) {
        setOpaque(false);
        setLayout(null); // absolute positioning

        // signature label (icon-based heart)
        String text = signatureText == null ? "" : signatureText.replace("❤️", "").replace("\u2764\uFE0F", "").trim();
        signatureLabel = new JLabel(text);

       
        String iconPath = "/icons/heart.png"; 
        java.net.URL iconUrl = getClass().getResource(iconPath);
        ImageIcon heartIcon = null;
        if (iconUrl != null) {
            heartIcon = new ImageIcon(iconUrl);
            // scale to match font height (optional). 14 px is a good small size.
            Image img = heartIcon.getImage().getScaledInstance(24, 24, Image.SCALE_SMOOTH);
            heartIcon = new ImageIcon(img);
        } else {
            // fallback: no resource found — leave text-only (optional log)
            System.err.println("heart icon not found at: " + iconPath + " — please check resource path.");
        }

        // apply label styles
        signatureLabel.setFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 15));
        signatureLabel.setForeground(new Color(255, 255, 255, 100));
        signatureLabel.setOpaque(false);

        // attach icon if loaded
        if (heartIcon != null) {
            signatureLabel.setIcon(heartIcon);
            signatureLabel.setIconTextGap(6);
        }

        add(signatureLabel);

        // Ensure glass pane bounds are set AFTER frame has been laid out
        SwingUtilities.invokeLater(() -> {
            // set glass pane to cover the whole root pane area
            Dimension rootSize = frame.getRootPane().getSize();
            setBounds(0, 0, rootSize.width, rootSize.height);
            reposition(frame);
            revalidate();
            repaint();
        });

        // reposition on frame resize/show
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                // update own bounds to match root pane
                Dimension rootSize = frame.getRootPane().getSize();
                setBounds(0, 0, rootSize.width, rootSize.height);
                reposition(frame);
                revalidate();
                repaint();
            }

            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                Dimension rootSize = frame.getRootPane().getSize();
                setBounds(0, 0, rootSize.width, rootSize.height);
                reposition(frame);
                revalidate();
                repaint();
            }
        });

        // Also watch the root pane's size (covers more cases)
        frame.getRootPane().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                Dimension rootSize = frame.getRootPane().getSize();
                setBounds(0, 0, rootSize.width, rootSize.height);
                reposition(frame);
                revalidate();
                repaint();
            }
        });
    }

    /**
     * Keep label at bottom-left corner with small margin, using root pane height
     */
    private void reposition(JFrame frame) {
        int marginX = 6;
        int marginY = 6;

        Dimension pref = signatureLabel.getPreferredSize();
        // use root pane height (content area) so label is inside window chrome
        int rootHeight = frame.getRootPane().getHeight();
        int x = marginX;
        int y = rootHeight - pref.height - marginY;

        if (y < marginY)
            y = marginY;

        signatureLabel.setBounds(x, y, pref.width, pref.height);
    }

    /**
     * IMPORTANT:
     * Returning false makes this glass pane ignore mouse events,
     * so clicks go to underlying UI components.
     */
    @Override
    public boolean contains(int x, int y) {
        return false;
    }
}
