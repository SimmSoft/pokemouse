import pl.openai.pokeballmouse.MotionGestureDetector;

public final class MotionGestureSelfTest {
    private static void expect(MotionGestureDetector.Direction actual,
                               MotionGestureDetector.Direction expected, String label) {
        if (actual != expected) throw new AssertionError(label + ": " + actual + " != " + expected);
    }

    public static void main(String[] args) {
        MotionGestureDetector d = new MotionGestureDetector();

        // Idle / hand tremor at the lowest setting must stay neutral.
        d.update(0f, 0f, 1f, false, 0.32f, 1000);
        d.update(0.06f, -0.04f, 1.02f, false, 0.32f, 1030);
        d.update(0.03f, 0.02f, 0.98f, true, 0.32f, 1100); // Top down
        if (d.update(0.10f, -0.08f, 0.96f, true, 0.32f, 1160) != null)
            throw new AssertionError("idle noise triggered during short arming interval");

        // A quick movement shortly after ~0.1 s must already work.
        expect(d.update(0.82f, -0.04f, 0.96f, true, 0.40f, 1210),
                MotionGestureDetector.Direction.RIGHT, "quick right after short arm");
        if (d.update(-0.95f, 0.02f, 1.00f, true, 0.40f, 1280) != null)
            throw new AssertionError("return movement retriggered");

        // Releasing Top rearms. A lateral gesture split across BOTH X and Z must still
        // be classified as left/right instead of being rejected as ambiguous.
        d.update(0f, 0f, 1f, false, 0.40f, 1500);
        d.update(0f, 0f, 1f, true, 0.40f, 1600);
        d.update(0.02f, 0.00f, 1.00f, true, 0.40f, 1660); // still settling
        expect(d.update(-0.52f, 0.18f, 0.43f, true, 0.40f, 1710),
                MotionGestureDetector.Direction.LEFT, "left via mixed X/Z plane");

        // Opposite lateral impulse in the next hold should use the learned lateral axis.
        d.update(0f, 0f, 1f, false, 0.40f, 1900);
        d.update(0f, 0f, 1f, true, 0.40f, 2000);
        d.update(0f, 0f, 1f, true, 0.40f, 2060);
        expect(d.update(0.55f, -0.10f, 1.52f, true, 0.40f, 2110),
                MotionGestureDetector.Direction.RIGHT, "right via learned mixed X/Z plane");

        d.reset();
        d.update(0f, 0f, 1f, false, 0.40f, 3000);
        d.update(0f, 0f, 1f, true, 0.40f, 3100);
        d.update(0f, 0f, 1f, true, 0.40f, 3160);
        expect(d.update(0.08f, 0.82f, 1.05f, true, 0.40f, 3210),
                MotionGestureDetector.Direction.UP, "up");

        d.reset();
        d.update(0f, 0f, 1f, false, 0.40f, 4000);
        d.update(0f, 0f, 1f, true, 0.40f, 4100);
        d.update(0f, 0f, 1f, true, 0.40f, 4160);
        expect(d.update(0.06f, -0.84f, 0.98f, true, 0.40f, 4210),
                MotionGestureDetector.Direction.DOWN, "down");

        // Pressing Top while the hand is already moving must not capture the press bump.
        d.reset();
        d.update(0f, 0f, 1f, false, 0.40f, 5000);
        d.update(0.58f, 0.10f, 0.92f, true, 0.40f, 5050); // Top pressed mid-swing
        d.update(0.75f, 0.13f, 0.89f, true, 0.40f, 5100); // baseline follows
        if (d.update(0.76f, 0.13f, 0.89f, true, 0.40f, 5145) != null)
            throw new AssertionError("button press/mid-swing falsely triggered after settle");

        System.out.println("MotionGestureSelfTest: PASS");
    }
}
