package com.friday.ui.dock;

import java.awt.Desktop;
import java.net.URI;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

/**
 * Futuristic Bottom Glass HUD Application Dock with launchable shortcuts.
 */
public final class BottomDock extends HBox {

    private static final String[][] DOCK_APPS = {
            {"Windows", "⊞", "shell:StartMenuImageCache"},
            {"Explorer", "📁", "explorer"},
            {"VS Code", "◬", "vscode"},
            {"Browser", "🌐", "https://google.com"},
            {"Spotify", "🎵", "https://open.spotify.com"},
            {"Discord", "💬", "https://discord.com/app"},
            {"Notion", "📝", "https://notion.so"},
            {"Recycle Bin", "🗑", "recycle"}
    };

    public BottomDock() {
        getStyleClass().addAll("glass-panel", "bottom-dock-bar");
        setAlignment(Pos.CENTER);
        setSpacing(10);
        setPadding(new Insets(6, 16, 6, 16));
        setMaxWidth(420);

        for (String[] app : DOCK_APPS) {
            String name = app[0];
            String icon = app[1];
            String action = app[2];

            Button btn = createDockButton(name, icon, action);
            getChildren().add(btn);
        }
    }

    private Button createDockButton(String name, String icon, String action) {
        Button btn = new Button(icon);
        btn.getStyleClass().add("dock-icon-btn");
        btn.setFocusTraversable(false);
        Tooltip.install(btn, new Tooltip(name));

        btn.setOnAction(e -> launchApp(name, action));
        return btn;
    }

    private void launchApp(String name, String action) {
        try {
            if (action.startsWith("http")) {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(action));
                }
            } else if ("explorer".equals(action)) {
                new ProcessBuilder("explorer.exe").start();
            } else if ("vscode".equals(action)) {
                new ProcessBuilder("cmd.exe", "/c", "code .").start();
            } else if ("recycle".equals(action)) {
                new ProcessBuilder("explorer.exe", "shell:RecycleBinFolder").start();
            } else {
                showToast(name, "Shortcut triggered for " + name);
            }
        } catch (Exception ex) {
            showToast("Launch Failed", "Could not launch " + name + ": " + ex.getMessage());
        }
    }

    private void showToast(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("F.R.I.D.A.Y. — " + title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }
}

