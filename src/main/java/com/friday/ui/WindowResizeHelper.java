package com.friday.ui;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Utility to support resizing of undecorated/frameless JavaFX stages by dragging window borders.
 */
public final class WindowResizeHelper {

    private static final int RESIZE_MARGIN = 8;

    private enum ResizeDirection {
        NONE, TOP, BOTTOM, LEFT, RIGHT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    private final Stage stage;
    private final Scene scene;

    private ResizeDirection currentDirection = ResizeDirection.NONE;
    private double startMouseX;
    private double startMouseY;
    private double startStageX;
    private double startStageY;
    private double startStageWidth;
    private double startStageHeight;

    private WindowResizeHelper(Stage stage, Scene scene) {
        this.stage = stage;
        this.scene = scene;
        registerHandlers();
    }

    public static void attach(Stage stage, Scene scene) {
        new WindowResizeHelper(stage, scene);
    }

    private void registerHandlers() {
        scene.setOnMouseMoved(event -> {
            if (stage.isMaximized() || stage.isIconified()) {
                scene.setCursor(Cursor.DEFAULT);
                return;
            }

            double x = event.getSceneX();
            double y = event.getSceneY();
            double width = scene.getWidth();
            double height = scene.getHeight();

            ResizeDirection direction = determineDirection(x, y, width, height);
            updateCursor(direction);
        });

        scene.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                double x = event.getSceneX();
                double y = event.getSceneY();
                double width = scene.getWidth();
                double height = scene.getHeight();

                currentDirection = determineDirection(x, y, width, height);
                if (currentDirection != ResizeDirection.NONE) {
                    startMouseX = event.getScreenX();
                    startMouseY = event.getScreenY();
                    startStageX = stage.getX();
                    startStageY = stage.getY();
                    startStageWidth = stage.getWidth();
                    startStageHeight = stage.getHeight();
                }
            }
        });

        scene.setOnMouseDragged(event -> {
            if (currentDirection == ResizeDirection.NONE) {
                return;
            }

            double deltaX = event.getScreenX() - startMouseX;
            double deltaY = event.getScreenY() - startMouseY;

            double minWidth = Math.max(stage.getMinWidth(), 400);
            double minHeight = Math.max(stage.getMinHeight(), 300);

            switch (currentDirection) {
                case RIGHT -> {
                    double newWidth = Math.max(minWidth, startStageWidth + deltaX);
                    stage.setWidth(newWidth);
                }
                case BOTTOM -> {
                    double newHeight = Math.max(minHeight, startStageHeight + deltaY);
                    stage.setHeight(newHeight);
                }
                case BOTTOM_RIGHT -> {
                    double newWidth = Math.max(minWidth, startStageWidth + deltaX);
                    double newHeight = Math.max(minHeight, startStageHeight + deltaY);
                    stage.setWidth(newWidth);
                    stage.setHeight(newHeight);
                }
                case LEFT -> {
                    double newWidth = Math.max(minWidth, startStageWidth - deltaX);
                    if (newWidth > minWidth) {
                        stage.setX(startStageX + deltaX);
                        stage.setWidth(newWidth);
                    }
                }
                case TOP -> {
                    double newHeight = Math.max(minHeight, startStageHeight - deltaY);
                    if (newHeight > minHeight) {
                        stage.setY(startStageY + deltaY);
                        stage.setHeight(newHeight);
                    }
                }
                case TOP_LEFT -> {
                    double newWidth = Math.max(minWidth, startStageWidth - deltaX);
                    double newHeight = Math.max(minHeight, startStageHeight - deltaY);
                    if (newWidth > minWidth) {
                        stage.setX(startStageX + deltaX);
                        stage.setWidth(newWidth);
                    }
                    if (newHeight > minHeight) {
                        stage.setY(startStageY + deltaY);
                        stage.setHeight(newHeight);
                    }
                }
                case TOP_RIGHT -> {
                    double newWidth = Math.max(minWidth, startStageWidth + deltaX);
                    double newHeight = Math.max(minHeight, startStageHeight - deltaY);
                    stage.setWidth(newWidth);
                    if (newHeight > minHeight) {
                        stage.setY(startStageY + deltaY);
                        stage.setHeight(newHeight);
                    }
                }
                case BOTTOM_LEFT -> {
                    double newWidth = Math.max(minWidth, startStageWidth - deltaX);
                    double newHeight = Math.max(minHeight, startStageHeight + deltaY);
                    if (newWidth > minWidth) {
                        stage.setX(startStageX + deltaX);
                        stage.setWidth(newWidth);
                    }
                    stage.setHeight(newHeight);
                }
                default -> {}
            }
        });

        scene.setOnMouseReleased(event -> {
            currentDirection = ResizeDirection.NONE;
            scene.setCursor(Cursor.DEFAULT);
        });
    }

    private ResizeDirection determineDirection(double x, double y, double width, double height) {
        boolean left = x <= RESIZE_MARGIN;
        boolean right = x >= width - RESIZE_MARGIN;
        boolean top = y <= RESIZE_MARGIN;
        boolean bottom = y >= height - RESIZE_MARGIN;

        if (top && left) return ResizeDirection.TOP_LEFT;
        if (top && right) return ResizeDirection.TOP_RIGHT;
        if (bottom && left) return ResizeDirection.BOTTOM_LEFT;
        if (bottom && right) return ResizeDirection.BOTTOM_RIGHT;
        if (left) return ResizeDirection.LEFT;
        if (right) return ResizeDirection.RIGHT;
        if (top) return ResizeDirection.TOP;
        if (bottom) return ResizeDirection.BOTTOM;

        return ResizeDirection.NONE;
    }

    private void updateCursor(ResizeDirection direction) {
        Cursor cursor = switch (direction) {
            case TOP -> Cursor.N_RESIZE;
            case BOTTOM -> Cursor.S_RESIZE;
            case LEFT -> Cursor.W_RESIZE;
            case RIGHT -> Cursor.E_RESIZE;
            case TOP_LEFT -> Cursor.NW_RESIZE;
            case TOP_RIGHT -> Cursor.NE_RESIZE;
            case BOTTOM_LEFT -> Cursor.SW_RESIZE;
            case BOTTOM_RIGHT -> Cursor.SE_RESIZE;
            case NONE -> Cursor.DEFAULT;
        };
        scene.setCursor(cursor);
    }
}

