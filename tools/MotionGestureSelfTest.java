import pl.openai.pokeballmouse.MotionGestureDetector;

public final class MotionGestureSelfTest {
    private static void expect(MotionGestureDetector.Direction actual,
                               MotionGestureDetector.Direction expected, String label) {
        if (actual != expected) throw new AssertionError(label + ": " + actual + " != " + expected);
    }

    public static void main(String[] args) {
        MotionGestureDetector d = new MotionGestureDetector();

        // Idle / hand tremor at the lowest user setting must stay neutral.
        d.update(0f, 0f, 1f, false, 0.22f, 1000);
        d.update(0.06f, -0.04f, 1.02f, false, 0.22f, 1030);
        d.update(0.03f, 0.02f, 0.98f, true, 0.22f, 1100); // Top down, baseline
        if (d.update(0.10f, -0.08f, 0.96f, true, 0.22f, 1180) != null)
            throw new AssertionError("idle noise triggered");

        // A quick initial movement triggers once.
        expect(d.update(0.95f, 0.04f, 0.97f, true, 0.40f, 1210),
                MotionGestureDetector.Direction.RIGHT, "right");
        // Natural braking/return in the opposite direction must be ignored while Top stays held.
        if (d.update(-1.00f, 0.02f, 1.00f, true, 0.40f, 1300) != null)
            throw new AssertionError("return movement retriggered");
        if (d.update(-0.90f, 0.01f, 1.01f, true, 0.40f, 1700) != null)
            throw new AssertionError("same Top hold accepted a second gesture");

        // Releasing Top rearms the detector for the next hold.
        d.update(0f, 0f, 1f, false, 0.40f, 1800);
        d.update(0f, 0f, 1f, true, 0.40f, 1900);
        expect(d.update(-0.95f, 0.03f, 0.98f, true, 0.40f, 1980),
                MotionGestureDetector.Direction.LEFT, "left");

        d.reset();
        d.update(0f, 0f, 1f, false, 0.40f, 3000);
        d.update(0f, 0f, 1f, true, 0.40f, 3100);
        expect(d.update(0.02f, 0.95f, 1f, true, 0.40f, 3180),
                MotionGestureDetector.Direction.UP, "up");

        d.reset();
        d.update(0f, 0f, 1f, false, 0.40f, 4000);
        d.update(0f, 0f, 1f, true, 0.40f, 4100);
        expect(d.update(0.01f, -0.95f, 1f, true, 0.40f, 4180),
                MotionGestureDetector.Direction.DOWN, "down");

        // Z-dominant jolt is not mapped to a 4-direction action.
        d.reset();
        d.update(0f, 0f, 0f, false, 0.40f, 5000);
        d.update(0f, 0f, 0f, true, 0.40f, 5100);
        if (d.update(0.42f, 0.03f, 1.0f, true, 0.40f, 5180) != null)
            throw new AssertionError("z-dominant jolt triggered");

        System.out.println("MotionGestureSelfTest: PASS");
    }
}
