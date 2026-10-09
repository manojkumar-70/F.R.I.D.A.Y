package com.friday;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        Label label = new Label("F.R.I.D.A.Y. System Online");

        StackPane root = new StackPane(label);
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root, 600, 400);

        primaryStage.setTitle("F.R.I.D.A.Y.");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

