package com.friday.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * Right-side HUD Notes Panel displaying system and project notes.
 */
public final class NotesPanel extends VBox {

    private static final String[] DEFAULT_NOTES = {
            "InternConnect deployment",
            "MuleGuard AI improvements",
            "DBMS revision",
            "Java practice",
            "Plan next 10 days"
    };

    public NotesPanel() {
        getStyleClass().addAll("glass-panel", "notes-panel");
        setPadding(new Insets(12, 16, 12, 16));
        setSpacing(8);
        setMinWidth(220);
        setMaxWidth(260);

        Label header = new Label("Notes");
        header.getStyleClass().add("panel-section-title");

        VBox notesList = new VBox(6);
        for (String note : DEFAULT_NOTES) {
            notesList.getChildren().add(createNoteRow(note));
        }

        getChildren().addAll(header, notesList);
    }

    private HBox createNoteRow(String text) {
        Circle dot = new Circle(3);
        dot.getStyleClass().add("note-bullet-dot");

        Label label = new Label(text);
        label.getStyleClass().add("note-text-label");

        HBox row = new HBox(8, dot, label);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}

