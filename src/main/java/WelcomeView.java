import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

public class WelcomeView extends JPanel implements ComponentListener {

    private final App app;

    private final JLabel titleLabel;
    private final JLabel mottoLabel;
    public JButton openProjectButton;

    // NEW: logo label
    private final JLabel logoLabel;

    GradientPaint gradient;

    int titleWidth = 400, titleHeight = 200, mottoWidth = 400, mottoHeight = 100;

    public WelcomeView(App app) {
        this.app = app;
        this.addComponentListener(this);
        this.setBounds(0, 0, app.getWidth(), app.getHeight());
        this.setLayout(null);
        this.setOpaque(false);

        // logo
        logoLabel = new JLabel();
        // attempt to load and scale the logo; if missing, label remains empty
        // (graceful)
        try {
            ImageIcon icon = new ImageIcon(WelcomeView.class.getResource("/icons/logo.png"));
            int logoWidth = 120; // tweak as needed
            int logoHeight = 120;
            Image img = icon.getImage().getScaledInstance(logoWidth, logoHeight, Image.SCALE_SMOOTH);
            logoLabel.setIcon(new ImageIcon(img));
        } catch (Exception ex) {
            // resource missing -> ignore (no crash)
            System.err.println("logo.png not found in /icons/: " + ex.getMessage());
        }
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);

        titleLabel = new JLabel("{Java•UI}");
        titleLabel.setFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.BOLD, 52));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        mottoLabel = new JLabel("You code, We watch!!");
        mottoLabel.setFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 20));
        mottoLabel.setForeground(Color.GRAY);
        mottoLabel.setHorizontalAlignment(SwingConstants.CENTER);

        openProjectButton = new JButton("Open Project");
        openProjectButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        openProjectButton.setForeground(Color.WHITE);
        openProjectButton.setFont(new Font(FlatJetBrainsMonoFont.FAMILY, Font.PLAIN, 18));

        openProjectButton.setBackground(new Color(12, 100, 181));

        openProjectButton.addActionListener(e -> {
            // remember if frame is currently maximized
            boolean wasMaximized = (app.getExtendedState()
                    & java.awt.Frame.MAXIMIZED_BOTH) == java.awt.Frame.MAXIMIZED_BOTH;

            // openProject() returns true only if user APPROVED and folder was loaded
            boolean opened = app.projectView.openProject();

            if (!opened) {
                // user cancelled or no folder selected — do nothing, stay on Welcome
                return;
            }

            // run UI switch on EDT after chooser and tree population settled
            SwingUtilities.invokeLater(() -> {
                app.launch();

                // If app was maximized before chooser, ensure it stays maximized and validate
                // layout
                if (wasMaximized) {
                    // toggle extended state back to maximized — helps some LAFs/WM to layout
                    // children correctly
                    app.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
                }

                // Force layout recalculation to make sure JSplitPane and children get correct
                // size
                app.revalidate();
                app.repaint();

                // Also ensure project tree is visible/expanded
                try {
                    if (app.projectView != null && app.projectView.getProjectTree() != null) {
                        SwingUtilities.invokeLater(() -> {
                            app.projectView.getProjectTree().updateUI();
                            app.projectView.getProjectTree().revalidate();
                            app.projectView.getProjectTree().repaint();
                        });
                    }
                } catch (Exception ignored) {
                }
            });
        });

        // add logo first so it sits above title
        this.add(logoLabel);
        this.add(titleLabel);
        this.add(mottoLabel);
        this.add(openProjectButton);
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2D = (Graphics2D) g;

        GradientPaint gradient = new GradientPaint(
                0, 0, new Color(32, 32, 32),
                getWidth(), getHeight(), new Color(40, 40, 40));

        g2D.setPaint(gradient);
        g2D.fillRect(0, 0, getWidth(), getHeight());
    }

    @Override
    public void componentResized(ComponentEvent e) {

        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

        int spacing = 16;

        // --- logo size ---
        int logoW = 0, logoH = 0;
        if (logoLabel.getIcon() != null) {
            logoW = logoLabel.getIcon().getIconWidth();
            logoH = logoLabel.getIcon().getIconHeight();
        }

        // --- heights ---
        int titleH = titleHeight / 2;
        int mottoH = mottoHeight;
        int buttonH = mottoHeight / 2;

        // --- total block height ---
        int totalHeight = logoH +
                spacing +
                titleH +
                spacing +
                mottoH +
                spacing +
                buttonH;

        // --- start Y so that block is vertically centered ---
        int startY = centerY - totalHeight / 2;

        // --- LOGO (top) ---
        if (logoLabel.getIcon() != null) {
            logoLabel.setBounds(
                    centerX - logoW / 2,
                    startY + 50,
                    logoW,
                    logoH);
            startY += logoH + spacing;
        }

        // --- TITLE ---
        titleLabel.setBounds(
                centerX - titleWidth / 2,
                startY,
                titleWidth,
                titleH);
        startY += titleH + spacing;

        // --- MOTTO ---
        mottoLabel.setBounds(
                centerX - mottoWidth / 2,
                startY - 70,
                mottoWidth,
                mottoH);
        startY += mottoH + spacing;

        // --- BUTTON ---
        openProjectButton.setBounds(
                centerX - titleWidth / 4,
                startY - 70,
                titleWidth / 2,
                buttonH);
    }

    @Override
    public void componentMoved(ComponentEvent e) {

    }

    @Override
    public void componentShown(ComponentEvent e) {

    }

    @Override
    public void componentHidden(ComponentEvent e) {

    }
}
