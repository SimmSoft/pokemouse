import pl.openai.pokeballmouse.MotionTelemetryDetector;

public final class MotionTelemetrySelfTest {
    private static long t = 1000L;

    public static void main(String[] args) {
        expect(MotionTelemetryDetector.Direction.RIGHT, impulse(0.8f, 0f, 0f));
        expect(MotionTelemetryDetector.Direction.LEFT, impulse(-0.8f, 0f, 0f));
        expect(MotionTelemetryDetector.Direction.UP, impulse(0f, 0.8f, 0f));
        expect(MotionTelemetryDetector.Direction.DOWN, impulse(0f, -0.8f, 0f));
        expect(MotionTelemetryDetector.Direction.FORWARD, impulse(0f, 0f, 0.8f));
        expect(MotionTelemetryDetector.Direction.BACKWARD, impulse(0f, 0f, -0.8f));
        System.out.println("MotionTelemetrySelfTest: PASS");
    }

    private static MotionTelemetryDetector.Direction impulse(float x, float y, float z) {
        MotionTelemetryDetector d = new MotionTelemetryDetector();
        d.update(0f, 0f, 0f, 0.25f, t);
        t += 500L;
        return d.update(x, y, z, 0.25f, t);
    }

    private static void expect(MotionTelemetryDetector.Direction expected,
                               MotionTelemetryDetector.Direction actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
