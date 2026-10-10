package com.friday.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Right-side HUD Tasks Panel with interactive checkboxes and scheduled time tags.
 */
public final class TasksPanel extends VBox {

    private static final String[][] DEFAULT_TASKS = {
            {"Finish project module", "1:00 AM"},
            {"Read research paper", "2:00 AM"},
            {"Push code to GitHub", "3:00 AM"},
            {"Prepare for exam", "4:00 AM"},
            {"Workout", "6:00 AM"}
    };

    public TasksPanel() {
        getStyleClass().addAll("glass-panel", "tasks-panel");
        setPadding(new Insets(12, 16, 12, 16));
        setSpacing(8);
        setMinWidth(220);
        setMaxWidth(260);

        Label header = new Label("Tasks");
        header.getStyleClass().add("panel-section-title");

        VBox taskList = new VBox(6);
        for (String[] task : DEFAULT_TASKS) {
            taskList.getChildren().add(createTaskRow(task[0], task[1]));
        }

        getChildren().addAll(header, taskList);
    }

    private HBox createTaskRow(String text, String time) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.getStyleClass().add("hud-checkbox");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label timeLabel = new Label(time);
        timeLabel.getStyleClass().add("task-time-label");

        checkBox.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected) {
                checkBox.getStyleClass().add("task-completed");
            } else {
                checkBox.getStyleClass().remove("task-completed");
            }
        });

        HBox row = new HBox(6, checkBox, spacer, timeLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}

