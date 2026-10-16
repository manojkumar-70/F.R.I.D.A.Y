package com.friday.assistant;

import com.friday.audio.MicrophoneAudioService;
import com.friday.audio.SpeechAudioService;

/**
 * Focused automated verification for AssistantStateController.
 * Validates the complete 14-point test specification:
 * 1. Initial state
 * 2. Permitted transitions
 * 3. Forbidden transitions matrix
 * 4. State preservation on rejected transitions
 * 5. Amplitude clamping
 * 6. Correct amplitude behavior across states
 * 7. Microphone startup on entering LISTENING
 * 8. Microphone shutdown on leaving LISTENING
 * 9. Microphone startup failure and state recovery
 * 10. Idempotent shutdown
 * 11. State changes after shutdown
 * 12. Error recovery behavior
 * 13. Repeated transitions without duplicate sessions
 * 14. Task execution progress behavior
 */
public class AssistantStateControllerTest {

    // ── Test doubles for isolated, hardware-free unit tests ────────────────────

    private static final class TestMicrophoneService implements MicrophoneAudioService {
        int startCalls = 0;
        int stopCalls = 0;
        boolean failOnStart = false;
        boolean available = true;
        boolean running = false;
        double amplitude = 0.55;

        @Override
        public boolean start() {
            startCalls++;
            if (failOnStart || !available) {
                running = false;
                return false;
            }
            running = true;
            return true;
        }

        @Override
        public void stop() {
            stopCalls++;
            running = false;
        }

        @Override
        public double getAmplitude() {
            return amplitude;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean isRunning() {
            return running;
        }
    }

    private static final class TestSpeechService implements SpeechAudioService {
        double amplitude = 0.75;
        boolean available = true;

        @Override
        public double getAmplitude() {
            return amplitude;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }
    }

    // ── 1. Initial State ───────────────────────────────────────────────────────

    public void testInitialState() {
        AssistantStateController controller = new AssistantStateController(
                new TestMicrophoneService(), new TestSpeechService());

        if (controller.getState() != AssistantState.IDLE) {
            throw new AssertionError("Expected initial state to be IDLE but was: " + controller.getState());
        }
        if (controller.getLastErrorMessage() != null) {
            throw new AssertionError("Expected initial error message to be null");
        }
        if (controller.isShutdown()) {
            throw new AssertionError("Controller should not be shutdown initially");
        }
    }

    // ── 2. Permitted Transitions ───────────────────────────────────────────────

    public void testPermittedTransitions() {
        TestMicrophoneService mic = new TestMicrophoneService();
        TestSpeechService speech = new TestSpeechService();
        AssistantStateController controller = new AssistantStateController(mic, speech);

        // IDLE -> LISTENING -> THINKING -> EXECUTING -> SPEAKING -> IDLE
        assertTrue(controller.transitionTo(AssistantState.LISTENING), "IDLE -> LISTENING should be permitted");
        assertEquals(AssistantState.LISTENING, controller.getState());

        assertTrue(controller.transitionTo(AssistantState.THINKING), "LISTENING -> THINKING should be permitted");
        assertEquals(AssistantState.THINKING, controller.getState());

        assertTrue(controller.transitionTo(AssistantState.EXECUTING), "THINKING -> EXECUTING should be permitted");
        assertEquals(AssistantState.EXECUTING, controller.getState());

        assertTrue(controller.transitionTo(AssistantState.SPEAKING), "EXECUTING -> SPEAKING should be permitted");
        assertEquals(AssistantState.SPEAKING, controller.getState());

        assertTrue(controller.transitionTo(AssistantState.IDLE), "SPEAKING -> IDLE should be permitted");
        assertEquals(AssistantState.IDLE, controller.getState());

        // IDLE -> ERROR -> IDLE
        assertTrue(controller.transitionTo(AssistantState.ERROR), "IDLE -> ERROR should be permitted");
        assertEquals(AssistantState.ERROR, controller.getState());

        assertTrue(controller.transitionTo(AssistantState.IDLE), "ERROR -> IDLE should be permitted");
        assertEquals(AssistantState.IDLE, controller.getState());

        // EXECUTING -> THINKING
        assertTrue(controller.transitionTo(AssistantState.EXECUTING), "IDLE -> EXECUTING should be permitted");
        assertTrue(controller.transitionTo(AssistantState.THINKING), "EXECUTING -> THINKING should be permitted");
        assertEquals(AssistantState.THINKING, controller.getState());

        // THINKING -> IDLE
        assertTrue(controller.transitionTo(AssistantState.IDLE), "THINKING -> IDLE should be permitted");
        assertEquals(AssistantState.IDLE, controller.getState());
    }

    // ── 3. Forbidden Transitions Matrix ────────────────────────────────────────

    public void testForbiddenTransitionsMatrix() {
        AssistantStateController controller = new AssistantStateController(
                new TestMicrophoneService(), new TestSpeechService());

        // IDLE cannot go directly to SPEAKING
        assertFalse(controller.transitionTo(AssistantState.SPEAKING), "IDLE -> SPEAKING must be rejected");

        // Set to LISTENING
        controller.transitionTo(AssistantState.LISTENING);
        assertFalse(controller.transitionTo(AssistantState.EXECUTING), "LISTENING -> EXECUTING must be rejected");
        assertFalse(controller.transitionTo(AssistantState.SPEAKING), "LISTENING -> SPEAKING must be rejected");

        // Move to ERROR
        controller.transitionTo(AssistantState.ERROR);
        assertFalse(controller.transitionTo(AssistantState.LISTENING), "ERROR -> LISTENING must be rejected");
        assertFalse(controller.transitionTo(AssistantState.THINKING), "ERROR -> THINKING must be rejected");
        assertFalse(controller.transitionTo(AssistantState.EXECUTING), "ERROR -> EXECUTING must be rejected");
        assertFalse(controller.transitionTo(AssistantState.SPEAKING), "ERROR -> SPEAKING must be rejected");

        // Recover to IDLE
        controller.transitionTo(AssistantState.IDLE);

        // Move to SPEAKING via valid path
        controller.transitionTo(AssistantState.THINKING);
        controller.transitionTo(AssistantState.SPEAKING);

        assertFalse(controller.transitionTo(AssistantState.THINKING), "SPEAKING -> THINKING must be rejected");
        assertFalse(controller.transitionTo(AssistantState.EXECUTING), "SPEAKING -> EXECUTING must be rejected");
    }

    // ── 4. State Preservation on Rejected Transitions ─────────────────────────

    public void testStatePreservationOnRejectedTransitions() {
        AssistantStateController controller = new AssistantStateController(
                new TestMicrophoneService(), new TestSpeechService());

        controller.transitionTo(AssistantState.LISTENING);
        boolean rejected = controller.transitionTo(AssistantState.EXECUTING);

        assertFalse(rejected, "Transition should be rejected");
        assertEquals(AssistantState.LISTENING, controller.getState(),
                "State must remain LISTENING after rejected transition");
    }

    // ── 5. Amplitude Clamping ──────────────────────────────────────────────────

    public void testAmplitudeClamping() {
        TestMicrophoneService mic = new TestMicrophoneService();
        AssistantStateController controller = new AssistantStateController(mic, new TestSpeechService());

        controller.transitionTo(AssistantState.LISTENING);

        mic.amplitude = -0.5;
        assertEquals(0.0, controller.getAmplitude(), 0.001);

        mic.amplitude = 1.85;
        assertEquals(1.0, controller.getAmplitude(), 0.001);

        mic.amplitude = Double.NaN;
        assertEquals(0.0, controller.getAmplitude(), 0.001);

        mic.amplitude = 0.42;
        assertEquals(0.42, controller.getAmplitude(), 0.001);
    }

    // ── 6. Correct Amplitude Behavior Across States ────────────────────────────

    public void testAmplitudeBehaviorAcrossStates() {
        TestMicrophoneService mic = new TestMicrophoneService();
        TestSpeechService speech = new TestSpeechService();
        mic.amplitude = 0.6;
        speech.amplitude = 0.8;

        AssistantStateController controller = new AssistantStateController(mic, speech);

        // IDLE: amplitude must be 0.0
        assertEquals(0.0, controller.getAmplitude(), 0.001);

        // LISTENING: reads mic amplitude
        controller.transitionTo(AssistantState.LISTENING);
        assertEquals(0.6, controller.getAmplitude(), 0.001);

        // THINKING: must be 0.0
        controller.transitionTo(AssistantState.THINKING);
        assertEquals(0.0, controller.getAmplitude(), 0.001);

        // EXECUTING: must be 0.0
        controller.transitionTo(AssistantState.EXECUTING);
        assertEquals(0.0, controller.getAmplitude(), 0.001);

        // SPEAKING: reads speech amplitude
        controller.transitionTo(AssistantState.SPEAKING);
        assertEquals(0.8, controller.getAmplitude(), 0.001);

        // ERROR: must be 0.0
        controller.transitionTo(AssistantState.ERROR);
        assertEquals(0.0, controller.getAmplitude(), 0.001);
    }

    // ── 7. Microphone Startup on Entering LISTENING ───────────────────────────

    public void testMicrophoneStartupOnEnteringListening() {
        TestMicrophoneService mic = new TestMicrophoneService();
        AssistantStateController controller = new AssistantStateController(mic, new TestSpeechService());

        assertEquals(0, mic.startCalls);
        assertFalse(mic.isRunning());

        controller.transitionTo(AssistantState.LISTENING);

        assertEquals(1, mic.startCalls);
        assertTrue(mic.isRunning());
    }

    // ── 8. Microphone Shutdown on Leaving LISTENING ────────────────────────────

    public void testMicrophoneShutdownOnLeavingListening() {
        TestMicrophoneService mic = new TestMicrophoneService();
        AssistantStateController controller = new AssistantStateController(mic, new TestSpeechService());

        controller.transitionTo(AssistantState.LISTENING);
        assertTrue(mic.isRunning());

        controller.transitionTo(AssistantState.THINKING);
        assertEquals(1, mic.stopCalls);
        assertFalse(mic.isRunning());
    }

    // ── 9. Microphone Startup Failure and State Recovery ───────────────────────

    public void testMicrophoneStartupFailureAndStateRecovery() {
        TestMicrophoneService mic = new TestMicrophoneService();
        mic.failOnStart = true;
        AssistantStateController controller = new AssistantStateController(mic, new TestSpeechService());

        boolean result = controller.transitionTo(AssistantState.LISTENING);

        assertFalse(result, "Transition should report failure when mic fails");
        assertEquals(AssistantState.ERROR, controller.getState(),
                "Controller must enter ERROR state and never falsely report LISTENING");
        assertTrue(controller.getLastErrorMessage() != null && !controller.getLastErrorMessage().isBlank(),
                "Error message should explain microphone startup failure");
    }

    // ── 10. Idempotent Shutdown ────────────────────────────────────────────────

    public void testIdempotentShutdown() {
        TestMicrophoneService mic = new TestMicrophoneService();
        AssistantStateController controller = new AssistantStateController(mic, new TestSpeechService());

        controller.transitionTo(AssistantState.LISTENING);
        controller.shutdown();

        assertTrue(controller.isShutdown());
        assertEquals(AssistantState.IDLE, controller.getState());
        assertEquals(1, mic.stopCalls);

        // Second shutdown call must be safe and idempotent
        controller.shutdown();
        assertTrue(controller.isShutdown());
    }

    // ── 11. State Changes After Shutdown ───────────────────────────────────────

    public void testStateChangesAfterShutdown() {
        AssistantStateController controller = new AssistantStateController(
                new TestMicrophoneService(), new TestSpeechService());

        controller.shutdown();
        boolean transitionAttempt = controller.transitionTo(AssistantState.THINKING);

        assertFalse(transitionAttempt, "Transition after shutdown must be rejected");
        assertEquals(AssistantState.IDLE, controller.getState());
    }

    // ── 12. Error Recovery Behavior ───────────────────────────────────────────

    public void testErrorRecoveryBehavior() {
        AssistantStateController controller = new AssistantStateController(
                new TestMicrophoneService(), new TestSpeechService());

        controller.transitionTo(AssistantState.ERROR, "Network timeout");
        assertEquals("Network timeout", controller.getLastErrorMessage());

        boolean recovered = controller.resetError();
        assertTrue(recovered, "resetError should successfully return to IDLE");
        assertEquals(AssistantState.IDLE, controller.getState());
        assertEquals(null, controller.getLastErrorMessage(), "Error message should be cleared after reset");
    }

    // ── 13. Repeated Transitions Without Duplicate Sessions ───────────────────

    public void testRepeatedTransitionsWithoutDuplicateSessions() {
        TestMicrophoneService mic = new TestMicrophoneService();
        AssistantStateController controller = new AssistantStateController(mic, new TestSpeechService());

        controller.transitionTo(AssistantState.LISTENING);
        assertEquals(1, mic.startCalls);

        // Transitioning to LISTENING again while already LISTENING
        boolean selfTransition = controller.transitionTo(AssistantState.LISTENING);
        assertTrue(selfTransition, "Self-transition should succeed as no-op");
        assertEquals(1, mic.startCalls, "Self-transition must not start a duplicate microphone session");
    }

    // ── 14. Task Execution Progress Behavior ──────────────────────────────────

    public void testTaskExecutionProgressBehavior() {
        AssistantStateController controller = new AssistantStateController(
                new TestMicrophoneService(), new TestSpeechService());

        controller.transitionTo(AssistantState.EXECUTING);
        assertEquals(-1.0, controller.getTaskProgress(), 0.001); // default indeterminate

        controller.setTaskProgress(0.45);
        assertEquals(0.45, controller.getTaskProgress(), 0.001);

        controller.setTaskProgress(1.5);
        assertEquals(1.0, controller.getTaskProgress(), 0.001); // clamped to 1.0

        controller.setTaskProgress(-5.0);
        assertEquals(-1.0, controller.getTaskProgress(), 0.001); // resets to indeterminate
    }

    // ── 15. Read-Only State Property Verification ─────────────────────────────

    public void testReadOnlyStateProperty() {
        AssistantStateController controller = new AssistantStateController(
                new TestMicrophoneService(), new TestSpeechService());

        javafx.beans.property.ReadOnlyObjectProperty<AssistantState> prop = controller.stateProperty();
        assertEquals(AssistantState.IDLE, prop.get());

        if (prop instanceof javafx.beans.property.Property) {
            throw new AssertionError("stateProperty() must be read-only and not expose writable Property interface");
        }
    }

    // ── 16. Task Progress Special Values (NaN, Infinities) ────────────────────

    public void testTaskProgressSpecialValues() {
        AssistantStateController controller = new AssistantStateController(
                new TestMicrophoneService(), new TestSpeechService());

        controller.setTaskProgress(Double.NaN);
        assertEquals(-1.0, controller.getTaskProgress(), 0.001);

        controller.setTaskProgress(Double.POSITIVE_INFINITY);
        assertEquals(1.0, controller.getTaskProgress(), 0.001);

        controller.setTaskProgress(Double.NEGATIVE_INFINITY);
        assertEquals(-1.0, controller.getTaskProgress(), 0.001);
    }

    // ── 17. Null Target State Rejection ───────────────────────────────────────

    public void testNullTargetStateRejection() {
        AssistantStateController controller = new AssistantStateController(
                new TestMicrophoneService(), new TestSpeechService());

        boolean result = controller.transitionTo(null);
        assertFalse(result, "transitionTo(null) must be rejected");
        assertEquals(AssistantState.IDLE, controller.getState());
        assertFalse(controller.canTransitionTo(null));
    }

    // ── 18. Rapid State Transitions Consistency ───────────────────────────────

    public void testRapidStateTransitionsConsistency() {
        TestMicrophoneService mic = new TestMicrophoneService();
        AssistantStateController controller = new AssistantStateController(mic, new TestSpeechService());

        for (int i = 0; i < 50; i++) {
            assertTrue(controller.transitionTo(AssistantState.LISTENING));
            assertTrue(controller.transitionTo(AssistantState.THINKING));
            assertTrue(controller.transitionTo(AssistantState.EXECUTING));
            assertTrue(controller.transitionTo(AssistantState.SPEAKING));
            assertTrue(controller.transitionTo(AssistantState.IDLE));
        }

        assertEquals(AssistantState.IDLE, controller.getState());
        assertEquals(50, mic.startCalls);
        assertEquals(50, mic.stopCalls);
        assertFalse(mic.isRunning());
    }

    // ── 19. Microphone RMS PCM Calculation ────────────────────────────────────

    public void testMicrophoneRmsPcmCalculation() {
        assertEquals(0.0, com.friday.audio.MicrophoneService.computeRmsNormalized(null, 0), 0.001);
        assertEquals(0.0, com.friday.audio.MicrophoneService.computeRmsNormalized(new byte[0], 0), 0.001);

        byte[] silence = new byte[1024];
        assertEquals(0.0, com.friday.audio.MicrophoneService.computeRmsNormalized(silence, silence.length), 0.001);

        byte[] fullScale = new byte[1024];
        for (int i = 0; i < fullScale.length; i += 4) {
            fullScale[i] = (byte) 0xFF;
            fullScale[i + 1] = 0x7F; // +32767
            fullScale[i + 2] = 0x00;
            fullScale[i + 3] = (byte) 0x80; // -32768
        }
        double rms = com.friday.audio.MicrophoneService.computeRmsNormalized(fullScale, fullScale.length);
        assertTrue(rms > 0.99 && rms <= 1.0, "RMS for full scale square wave should be ~1.0 but was: " + rms);
    }

    // ── Assert helpers ─────────────────────────────────────────────────────────

    private static void assertTrue(boolean condition) {
        assertTrue(condition, "Assertion failed");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertFalse(boolean condition) {
        assertFalse(condition, "Assertion failed: expected false but was true");
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError("Expected: " + expected + " but was: " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(message + " - Expected: " + expected + " but was: " + actual);
        }
    }

    private static void assertEquals(double expected, double actual, double delta) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError("Expected: " + expected + " within " + delta + " but was: " + actual);
        }
    }
}
