package net.pytrial.ui;

import net.pytrial.util.PyTrialLog;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

public final class LoadingWindow {

    private static final Color BACKGROUND = new Color(0x1B1B1F);
    private static final Color TRACK = new Color(0x2A2A30);
    private static final Color TITLE = new Color(0xE6E6E6);
    private static final Color STATUS = new Color(0xA8A8B0);
    private static final Color ACCENT = new Color(0x4FA8FF);

    private static final int WIDTH = 520;
    private static final int HEIGHT = 132;

    private final String title;
    private JFrame frame;
    private JLabel statusLabel;
    private volatile boolean closed;

    public LoadingWindow(String title) {
        this.title = title;
    }

    public void open(String status) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    build(status);
                } catch (Throwable t) {
                    PyTrialLog.warn("Failed to create the loading window: " + t.getMessage());
                }
            });
        } catch (Throwable t) {
            PyTrialLog.warn("Failed to create the loading window: " + t.getMessage());
        }
    }

    private void build(String status) {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(18, 22, 14, 22));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(TITLE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        titleLabel.setAlignmentX(0f);
        root.add(titleLabel);
        root.add(Box.createVerticalStrut(12));

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setBorderPainted(false);
        bar.setForeground(ACCENT);
        bar.setBackground(TRACK);
        bar.setPreferredSize(new Dimension(WIDTH - 44, 6));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        bar.setAlignmentX(0f);
        root.add(bar);
        root.add(Box.createVerticalStrut(10));

        statusLabel = new JLabel(status);
        statusLabel.setForeground(STATUS);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        statusLabel.setAlignmentX(0f);
        root.add(statusLabel);

        frame = new JFrame(title);
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(root);
        frame.setSize(WIDTH, HEIGHT);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true);
        frame.setVisible(true);
    }

    public void setStatus(String status) {
        if (closed) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            JLabel label = statusLabel;
            if (label != null) {
                label.setText(status);
            }
        });
    }

    public void close() {
        closed = true;
        SwingUtilities.invokeLater(() -> {
            JFrame current = frame;
            frame = null;
            statusLabel = null;
            if (current != null) {
                current.setVisible(false);
                current.dispose();
            }
        });
    }
}
