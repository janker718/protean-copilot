package com.protean.copilot.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.protean.copilot.bridge.ToolWindowBridge;
import com.protean.copilot.bridge.ToolWindowBridgeView;

import javax.swing.*;
import java.awt.*;

public class ProteanToolWindowPanel extends JBPanel<ProteanToolWindowPanel> implements ToolWindowBridgeView {

    private final Project project;
    private ToolWindowBridge bridge = null;

    private final JBLabel statusLabel;
    private final JBTextArea outputArea;
    private final JBTextArea promptArea;

    public ProteanToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;

        statusLabel = new JBLabel("Ready");

        outputArea = new JBTextArea();
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.setText(
            """
            Protean Copilot is ready.

            Use the prompt box below, or select code and run Tools | Protean Copilot | Explain Selection.
            """
        );

        promptArea = new JBTextArea();
        promptArea.getEmptyText().setText("Ask Protean Copilot about this project...");
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setRows(5);

        // header
        JBLabel header = new JBLabel("Protean Copilot");
        header.setBorder(BorderFactory.createEmptyBorder(8, 8, 2, 8));

        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(header, BorderLayout.NORTH);
        topPanel.add(statusLabel, BorderLayout.SOUTH);

        // buttons
        JButton contextButton = new JButton("Refresh Context");
        contextButton.addActionListener(e -> {
            if (bridge != null) {
                bridge.refreshContext();
            }
        });

        JButton sendButton = new JButton("Send");
        sendButton.addActionListener(e -> {
            if (bridge != null) {
                bridge.submitPrompt(promptArea.getText());
            }
        });

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        footer.add(contextButton, BorderLayout.WEST);
        footer.add(sendButton, BorderLayout.EAST);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JBScrollPane(promptArea), BorderLayout.CENTER);
        bottomPanel.add(footer, BorderLayout.SOUTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JBScrollPane(outputArea), bottomPanel);
        splitPane.setResizeWeight(0.74);
        splitPane.setMinimumSize(new Dimension(240, 320));
        splitPane.setBorder(null);

        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> {
            splitPane.setDividerLocation(0.74);
        });
    }

    public void bindBridge(ToolWindowBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void setStatus(String message) {
        statusLabel.setText(message);
    }

    @Override
    public void showOutput(String text) {
        outputArea.setText(text);
        outputArea.setCaretPosition(0);
    }

    @Override
    public void clearPrompt() {
        promptArea.setText("");
    }
}
