package net.pytrial.bootstrap.ui;

import net.pytrial.bootstrap.install.ClientInstaller;
import net.pytrial.bootstrap.install.Installer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Swing front end of the client installer. */
public final class InstallWindow {

    private static final String TITLE = "pyTrial Client Installer";
    private static final String LATEST = "latest release";

    private final JFrame frame = new JFrame(TITLE);
    private final JTextField gameDirField = new JTextField(32);
    private final JComboBox<String> versionBox = new JComboBox<>();
    private final JTextField modsField = new JTextField(32);
    private final JTextArea log = new JTextArea(12, 72);
    private final JProgressBar progress = new JProgressBar();
    private final JLabel status = new JLabel("Ready.");
    private final JButton installButton = new JButton("Install");
    private final JButton openButton = new JButton("Open install folder");
    private final JCheckBox scriptsBox = new JCheckBox(
            "Also write launch.cmd / launch.sh (not needed when your launcher lists the profile)");

    private Path installDir;

    public static void open() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new InstallWindow().show());
    }

    private void show() {
        captureConsole();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(10, 10));
        frame.getRootPane().setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        frame.add(buildForm(), BorderLayout.NORTH);
        frame.add(buildLog(), BorderLayout.CENTER);
        frame.add(buildFooter(), BorderLayout.SOUTH);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        progress.setIndeterminate(false);
        openButton.setEnabled(false);
        openButton.addActionListener(event -> openInstallDir());
        installButton.addActionListener(event -> startInstall());

        Path gameDir = ClientInstaller.detectGameDir();
        gameDirField.setText(gameDir.toString());
        modsField.setText(gameDir.resolve(ClientInstaller.DIRECTORY_NAME).resolve("mods").toString());
        gameDirField.addActionListener(event -> refreshVersions());
        refreshVersions();

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.toFront();
    }

    private JPanel buildForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Installation target"));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;

        JButton browseGameDir = new JButton("Browse...");
        browseGameDir.addActionListener(event -> chooseDirectory(gameDirField));
        JButton browseMods = new JButton("Browse...");
        browseMods.addActionListener(event -> chooseDirectory(modsField));

        row(panel, constraints, 0, "Game directory", gameDirField, browseGameDir);
        row(panel, constraints, 1, "Minecraft version", versionBox, null);
        row(panel, constraints, 2, "Mods directory", modsField, browseMods);
        row(panel, constraints, 3, "Launch scripts", scriptsBox, null);
        return panel;
    }

    private void row(JPanel panel, GridBagConstraints constraints, int row,
                     String label, java.awt.Component field, JButton button) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, constraints);

        constraints.gridx = 2;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        if (button != null) {
            panel.add(button, constraints);
        } else {
            panel.add(Box.createHorizontalStrut(1), constraints);
        }
    }

    private JScrollPane buildLog() {
        JScrollPane pane = new JScrollPane(log);
        pane.setBorder(BorderFactory.createTitledBorder("Installation log"));
        pane.setPreferredSize(new Dimension(680, 260));
        return pane;
    }

    private JPanel buildFooter() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        progress.setStringPainted(false);
        JPanel progressRow = new JPanel(new BorderLayout(8, 0));
        progressRow.add(progress, BorderLayout.CENTER);
        progressRow.add(status, BorderLayout.SOUTH);
        panel.add(progressRow);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(openButton);
        buttons.add(installButton);
        panel.add(Box.createVerticalStrut(8));
        panel.add(buttons);
        return panel;
    }

    private void chooseDirectory(JTextField field) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select a directory");
        Path current = Path.of(field.getText());
        if (Files.isDirectory(current)) {
            chooser.setCurrentDirectory(current.toFile());
        }
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
            if (field == gameDirField) {
                modsField.setText(Path.of(field.getText())
                        .resolve(ClientInstaller.DIRECTORY_NAME).resolve("mods").toString());
                refreshVersions();
            }
        }
    }

    private void refreshVersions() {
        Path gameDir = Path.of(gameDirField.getText());
        versionBox.setModel(new DefaultComboBoxModel<>(new String[]{LATEST, "scanning..."}));
        versionBox.setSelectedIndex(0);
        runBackground(() -> {
            List<String> versions = ClientInstaller.listVersions(gameDir);
            String latest = ClientInstaller.latestRelease(gameDir);
            SwingUtilities.invokeLater(() -> {
                DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                model.addElement(latest != null ? LATEST + " (" + latest + ")" : LATEST);
                for (String version : versions) {
                    model.addElement(version);
                }
                versionBox.setModel(model);
                versionBox.setSelectedIndex(0);
            });
        });
    }

    private void startInstall() {
        Path gameDir = Path.of(gameDirField.getText().trim());
        Path modsDir = modsField.getText().isBlank() ? null : Path.of(modsField.getText().trim());
        String selected = (String) versionBox.getSelectedItem();
        String versionId = selected == null || selected.startsWith(LATEST) ? null : selected;

        installButton.setEnabled(false);
        openButton.setEnabled(false);
        progress.setIndeterminate(true);
        status.setText("Installing...");
        append("=== pyTrial client installation started ===");

        runBackground(() -> {
            try {
                boolean withScripts = scriptsBox.isSelected();
                Installer.Result result = ClientInstaller.install(gameDir, versionId, modsDir, null,
                        withScripts, new ClientInstaller.Listener() {
                            @Override
                            public void step(String message) {
                                append(message);
                            }
                        });
                SwingUtilities.invokeLater(() -> {
                    installDir = result.installDir;
                    progress.setIndeterminate(false);
                    progress.setValue(progress.getMaximum());
                    status.setText(result.profileId != null
                            ? "Installed. Pick '" + result.profileId + "' in your launcher."
                            : "Installed into " + result.installDir);
                    openButton.setEnabled(true);
                    installButton.setEnabled(true);
                });
            } catch (Throwable error) {
                append("ERROR " + error);
                SwingUtilities.invokeLater(() -> {
                    progress.setIndeterminate(false);
                    status.setText("Installation failed: " + error.getMessage());
                    installButton.setEnabled(true);
                });
            }
        });
    }

    private void openInstallDir() {
        if (installDir == null) {
            return;
        }
        try {
            Desktop.getDesktop().open(installDir.toFile());
        } catch (Exception error) {
            append("Cannot open " + installDir + ": " + error.getMessage());
        }
    }

    private void runBackground(Runnable task) {
        Thread thread = new Thread(task, "pytrial-installer");
        thread.setDaemon(true);
        thread.start();
    }

    private void append(String line) {
        SwingUtilities.invokeLater(() -> {
            log.append(line + System.lineSeparator());
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    /** Sends everything the bootstrap writes to stdout/stderr into the log area. */
    private void captureConsole() {
        PrintStream stream = new PrintStream(new OutputStream() {
            private final StringBuilder pending = new StringBuilder();

            @Override
            public void write(int value) {
                write(new byte[]{(byte) value}, 0, 1);
            }

            @Override
            public void write(byte[] bytes, int offset, int length) {
                String text = new String(bytes, offset, length, StandardCharsets.UTF_8);
                pending.append(text);
                int end;
                while ((end = pending.indexOf("\n")) >= 0) {
                    String line = pending.substring(0, end).stripTrailing();
                    pending.delete(0, end + 1);
                    if (!line.isBlank()) {
                        append(line);
                    }
                }
            }
        }, true, StandardCharsets.UTF_8);
        System.setOut(stream);
        System.setErr(stream);
    }
}
