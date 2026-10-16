package com.friday.assistant;

/**
 * Focused automated verification for AssistantStateController.
 * Validates initial state is IDLE, amplitude is safely clamped to 0.0 in IDLE,
 * state transitions work correctly, and shutdown cleanly stops services.
 */
public class AssistantStateControllerTest {

    public void testInitialStateIsIdle() {
        AssistantStateController controller = new AssistantStateController();
        if (controller.getState() != AssistantState.IDLE) {
            throw new AssertionError("Expected initial state to be IDLE but was: " + controller.getState());
        }
    }

    public void testIdleAmplitudeIsZero() {
        AssistantStateController controller = new AssistantStateController();
        double amp = controller.getAmplitude();
        if (amp != 0.0) {
            throw new AssertionError("Expected IDLE amplitude to be 0.0 but was: " + amp);
        }
    }

    public void testStateTransitionToAndFromIdle() {
        AssistantStateController controller = new AssistantStateController();
        controller.setState(AssistantState.THINKING);
        if (controller.getState() != AssistantState.THINKING) {
            throw new AssertionError("Expected state to be THINKING but was: " + controller.getState());
        }

        controller.setState(AssistantState.IDLE);
        if (controller.getState() != AssistantState.IDLE) {
            throw new AssertionError("Expected state to return to IDLE but was: " + controller.getState());
        }
    }

    public void testCleanShutdownInIdle() {
        AssistantStateController controller = new AssistantStateController();
        controller.shutdown();
        if (controller.getState() != AssistantState.IDLE) {
            throw new AssertionError("State changed unexpectedly after shutdown: " + controller.getState());
        }
    }
}

