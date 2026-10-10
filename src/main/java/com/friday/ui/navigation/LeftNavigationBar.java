package com.friday.ui.navigation;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

/**
 * Futuristic left vertical HUD navigation bar with illuminated active pills.
 */
public final class LeftNavigationBar extends VBox {

    private Button selectedButton = null;
    private Consumer<String> onNavSelected = null;

    private static final String[][] NAV_ITEMS = {
            {"Home", "⌂"},
            {"Chat", "💬"},
            {"Apps", "▦"},
            {"System", "⚙"},
            {"Camera", "📷"},
            {"Web", "🌐"},
            {"Notes", "📝"},
            {"Settings", "⚡"}
    };

    public LeftNavigationBar() {
        getStyleClass().add("left-nav-bar");
        setAlignment(Pos.TOP_CENTER);
        setSpacing(10);
        setPadding(new Insets(14, 8, 14, 8));
        setMinWidth(110);
        setMaxWidth(130);

        for (String[] item : NAV_ITEMS) {
            String title = item[0];
            String icon = item[1];
            Button navBtn = createNavButton(title, icon);
            getChildren().add(navBtn);

            if ("Home".equals(title)) {
                setSelected(navBtn);
            }
        }
    }

    private Button createNavButton(String title, String icon) {
        Button btn = new Button(icon + "  " + title);
        btn.getStyleClass().add("nav-pill-btn");
        btn.setFocusTraversable(false);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        Tooltip.install(btn, new Tooltip("Navigate to " + title));

        btn.setOnAction(e -> {
            setSelected(btn);
            if (onNavSelected != null) {
                onNavSelected.accept(title);
            }
        });

        return btn;
    }

    public void setOnNavSelected(Consumer<String> consumer) {
        this.onNavSelected = consumer;
    }

    private void setSelected(Button btn) {
        if (selectedButton != null) {
            selectedButton.getStyleClass().remove("nav-pill-btn-active");
        }
        selectedButton = btn;
        if (selectedButton != null && !selectedButton.getStyleClass().contains("nav-pill-btn-active")) {
            selectedButton.getStyleClass().add("nav-pill-btn-active");
        }
    }
}

