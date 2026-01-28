import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

public class RotatingIconLabel extends JLabel {

    private double angle = 0;          
    private Timer rotationTimer;

    public RotatingIconLabel(Icon icon) {
        super(icon);
        setOpaque(false);
    }

    public void rotateOnce() {
        if (rotationTimer != null && rotationTimer.isRunning()) return;

        angle = 0;

        rotationTimer = new Timer(15, e -> {
            angle += Math.toRadians(15);   // speed

            if (angle >= Math.toRadians(360)) {
                angle = 0;
                rotationTimer.stop();
            }
            repaint();
        });

        rotationTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Icon icon = getIcon();
        if (icon == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = icon.getIconWidth();
        int h = icon.getIconHeight();

        AffineTransform at = new AffineTransform();
        at.translate(getWidth() / 2.0, getHeight() / 2.0);
        at.rotate(angle);
        at.translate(-w / 2.0, -h / 2.0);

        g2.setTransform(at);
        icon.paintIcon(this, g2, 0, 0);
        g2.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        Icon icon = getIcon();
        return icon != null
                ? new Dimension(icon.getIconWidth() + 4, icon.getIconHeight() + 4)
                : super.getPreferredSize();
    }
}
