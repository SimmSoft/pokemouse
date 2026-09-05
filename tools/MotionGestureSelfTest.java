import pl.openai.pokeballmouse.MotionGestureDetector;

public final class MotionGestureSelfTest {
    private static void expect(MotionGestureDetector.Direction actual,
                               MotionGestureDetector.Direction expected, String label) {
        if (actual != expected) throw new AssertionError(label + ": " + actual + " != " + expected);
    }

    public static void main(String[] args) {
        MotionGestureDetector d = new MotionGestureDetector();

        // Idle / hand tremor at the lowest user setting must stay neutral.
        d.update(0f, 0f, 1f, false, 0.32f, 1000);
        d.update(0.06f, -0.04f, 1.02f, false, 0.32f, 1030);
        d.update(0.03f, 0.02f, 0.98f, true, 0.32f, 1100); // Top down
        if (d.update(0.10f, -0.08f, 0.96f, true, 0.32f, 1250) != null)
            throw new AssertionError("idle noise triggered during arming");
        if (d.update(0.08f, -0.05f, 0.99f, true, 0.32f, 1390) != null)
            throw new AssertionError("arming delay too short");

        // After 300 ms of holding Top, a deliberate movement triggers once.
        expect(d.update(0.95f, -0.04f, 0.98f, true, 0.40f, 1420),
                MotionGestureDetector.Direction.RIGHT, "right via X");
        if (d.update(-1.00f, 0.02f, 1.00f, true, 0.40f, 1510) != null)
            throw new AssertionError("return movement retriggered");

        // Releasing Top rearms the detector; Z is a valid horizontal fallback.
        d.update(0f, 0f, 1f, false, 0.40f, 1800);
        d.update(0f, 0f, 1f, true, 0.40f, 1900);
        d.update(0f, 0f, 1f, true, 0.40f, 2190); // still arming
        expect(d.update(0.02f, 0.02f, -0.95f, true, 0.40f, 2220),
                MotionGestureDetector.Direction.LEFT, "left via Z fallback");

        d.reset();
        d.update(0f, 0f, 1f, false, 0.40f, 3000);
        d.update(0f, 0f, 1f, true, 0.40f, 3100);
        d.update(0f, 0f, 1f, true, 0.40f, 3395);
        expect(d.update(0.02f, 0.95f, 1f, true, 0.40f, 3420),
                MotionGestureDetector.Direction.UP, "up");

        d.reset();
        d.update(0f, 0f, 1f, false, 0.40f, 4000);
        d.update(0f, 0f, 1f, true, 0.40f, 4100);
        d.update(0f, 0f, 1f, true, 0.40f, 4395);
        expect(d.update(0.01f, -0.95f, 1f, true, 0.40f, 4420),
                MotionGestureDetector.Direction.DOWN, "down");

        // Pressing Top while the hand is already moving must NOT capture that movement.
        d.reset();
        d.update(0f, 0f, 1f, false, 0.40f, 5000);
        d.update(0.65f, 0.10f, 0.95f, true, 0.40f, 5050);   // Top pressed mid-swing
        d.update(0.85f, 0.14f, 0.92f, true, 0.40f, 5150);   // baseline follows motion
        d.update(0.95f, 0.16f, 0.90f, true, 0.40f, 5250);
        d.update(0.98f, 0.16f, 0.90f, true, 0.40f, 5340);
        if (d.update(0.98f, 0.16f, 0.90f, true, 0.40f, 5370) != null)
            throw new AssertionError("mid-swing Top press falsely triggered");

        System.out.println("MotionGestureSelfTest: PASS");
    }
}
