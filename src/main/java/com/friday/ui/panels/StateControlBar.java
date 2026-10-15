package com.friday.ui.panels;

import com.friday.assistant.AssistantState;
import com.friday.assistant.AssistantStateController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Compact horizontal bar of state-switching buttons centred at the bottom of
 * the hologram panel.  Each button drives the {@link AssistantStateController}
 * to the corresponding {@link AssistantState}.
 *
 * The active state button is highlighted with the accent-primary style.
 * This component is purely a development/demo convenience — production code
 * would drive the state from voice/AI logic.
 */
public final class StateControlBar extends StackPane {

    private final AssistantStateController controller;
    private final Button[] buttons = new Button[AssistantState.values().length];

    private static final String[][] STATE_META = {
        // { label,   tooltip }
        { "⬡  IDLE",      "Idle — slow rotating rings"               },
        { "🎤  LISTEN",   "Listening — mic waveform (real audio)"    },
        { "◈  THINK",    "Thinking — scanning arcs"                  },
        { "▶  SPEAK",    "Speaking — speech-synced waveform"         },
        { "⚠  ALERT",    "Alert — red warning indicators"            },
    };

    public StateControlBar(AssistantStateController controller) {
        this.controller = controller;
        getStyleClass().add("state-control-bar");

        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(5, 12, 5, 12));

        // Frosted background pill
        Rectangle bg = new Rectangle();
        bg.setArcWidth(20);
        bg.setArcHeight(20);
        bg.setFill(Color.rgb(6, 14, 32, 0.82));
        bg.setStroke(Color.rgb(0, 229, 255, 0.35));
        bg.setStrokeWidth(1.0);
        bg.widthProperty().bind(row.widthProperty().add(0));
        bg.heightProperty().bind(row.heightProperty().add(0));

        AssistantState[] states = AssistantState.values();
        for (int i = 0; i < states.length; i++) {
            AssistantState state = states[i];
            Button btn = new Button(STATE_META[i][0]);
            btn.getStyleClass().addAll("state-btn");
            Tooltip.install(btn, new Tooltip(STATE_META[i][1]));
            btn.setFocusTraversable(false);
            btn.setOnAction(e -> controller.setState(state));
            buttons[i] = btn;
            row.getChildren().add(btn);
        }

        // Observe state changes to update highlight
        controller.stateProperty().addListener((obs, old, nw) -> highlightActive(nw));
        highlightActive(controller.getState());

        getChildren().addAll(bg, row);
    }

    private void highlightActive(AssistantState active) {
        AssistantState[] states = AssistantState.values();
        for (int i = 0; i < states.length; i++) {
            if (states[i] == active) {
                buttons[i].getStyleClass().remove("state-btn-inactive");
                if (!buttons[i].getStyleClass().contains("state-btn-active")) {
                    buttons[i].getStyleClass().add("state-btn-active");
                }
            } else {
                buttons[i].getStyleClass().remove("state-btn-active");
                if (!buttons[i].getStyleClass().contains("state-btn-inactive")) {
                    buttons[i].getStyleClass().add("state-btn-inactive");
                }
            }
        }
    }
}
