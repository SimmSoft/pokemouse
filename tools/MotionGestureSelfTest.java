import pl.openai.pokeballmouse.MotionGestureDetector;

public final class MotionGestureSelfTest {
    private static void expect(MotionGestureDetector.Direction actual,
                               MotionGestureDetector.Direction expected, String label) {
        if (actual != expected) throw new AssertionError(label + ": " + actual + " != " + expected);
    }

    public static void main(String[] args) {
        MotionGestureDetector d = new MotionGestureDetector();
        d.update(0f, 0f, 1f, false, 0.45f, 1000);
        // Small noise must not trigger.
        if (d.update(0.10f, -0.08f, 0.96f, true, 0.45f, 1100) != null)
            throw new AssertionError("noise triggered");
        expect(d.update(0.95f, 0.05f, 0.95f, true, 0.45f, 1200),
                MotionGestureDetector.Direction.RIGHT, "right");
        // Cooldown suppresses immediate repeat.
        if (d.update(-0.95f, 0f, 1f, true, 0.45f, 1300) != null)
            throw new AssertionError("cooldown failed");

        d.reset();
        d.update(0f, 0f, 1f, false, 0.45f, 2000);
        expect(d.update(-0.95f, 0.03f, 0.95f, true, 0.45f, 2500),
                MotionGestureDetector.Direction.LEFT, "left");

        d.reset();
        d.update(0f, 0f, 1f, false, 0.45f, 3000);
        expect(d.update(0.02f, 0.95f, 1f, true, 0.45f, 3500),
                MotionGestureDetector.Direction.UP, "up");

        d.reset();
        d.update(0f, 0f, 1f, false, 0.45f, 4000);
        expect(d.update(0.01f, -0.95f, 1f, true, 0.45f, 4500),
                MotionGestureDetector.Direction.DOWN, "down");

        // Forward/backward jolt (Z-dominant) must not trigger a direction.
        d.reset();
        d.update(0f, 0f, 0f, false, 0.45f, 5000);
        if (d.update(0.50f, 0.03f, 1.0f, true, 0.45f, 5500) != null)
            throw new AssertionError("z-dominant jolt triggered");

        System.out.println("MotionGestureSelfTest: PASS");
    }
}
