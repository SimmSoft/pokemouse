import pl.openai.pokeballmouse.MotionTelemetryDetector;

public final class MotionTelemetrySelfTest {
    public static void main(String[] args) {
        expect(MotionTelemetryDetector.Direction.RIGHT, impulse(0.9f, 0f, 0f));
        expect(MotionTelemetryDetector.Direction.LEFT, impulse(-0.9f, 0f, 0f));
        expect(MotionTelemetryDetector.Direction.UP, impulse(0f, 0.9f, 0f));
        expect(MotionTelemetryDetector.Direction.DOWN, impulse(0f, -0.9f, 0f));
        expect(MotionTelemetryDetector.Direction.FORWARD, impulse(0f, 0f, 0.9f));
        expect(MotionTelemetryDetector.Direction.BACKWARD, impulse(0f, 0f, -0.9f));

        MotionTelemetryDetector quiet = new MotionTelemetryDetector();
        quiet.update(0f, 0f, 1f, 0.22f, 1000);
        if (quiet.update(0.12f, -0.10f, 1.05f, 0.22f, 1020) != null)
            throw new AssertionError("idle noise triggered live direction");

        System.out.println("MotionTelemetrySelfTest: PASS");
    }

    private static MotionTelemetryDetector.Direction impulse(float x, float y, float z) {
        MotionTelemetryDetector d = new MotionTelemetryDetector();
        d.update(0f, 0f, 0f, 0.35f, 1000L);
        return d.update(x, y, z, 0.35f, 1080L);
    }

    private static void expect(MotionTelemetryDetector.Direction expected,
                               MotionTelemetryDetector.Direction actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
}
