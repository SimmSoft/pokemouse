import pl.openai.pokeballmouse.JoystickCalibration;

public final class JoystickCalibrationSelfTest {
    private static void near(float actual, float expected, float eps, String label) {
        if (Math.abs(actual - expected) > eps) {
            throw new AssertionError(label + ": expected " + expected + " got " + actual);
        }
    }

    public static void main(String[] args) {
        near(JoystickCalibration.applyAxis(0.18f, 0.18f), 0f, 0.0001f, "center");
        near(JoystickCalibration.applyAxis(1f, 0.18f), 1f, 0.0001f, "positive edge");
        near(JoystickCalibration.applyAxis(-1f, 0.18f), -1f, 0.0001f, "negative edge");
        float left = Math.abs(JoystickCalibration.applyAxis(-0.41f, 0.18f));
        float right = Math.abs(JoystickCalibration.applyAxis(0.59f, 0.18f));
        near(left, right, 0.02f, "balanced travel");
        System.out.println("JoystickCalibrationSelfTest: PASS");
    }
}
