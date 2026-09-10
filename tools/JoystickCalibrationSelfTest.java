import pl.openai.pokeballmouse.JoystickCalibration;

public final class JoystickCalibrationSelfTest {
    private static void close(float actual, float expected, float eps, String label) {
        if (Math.abs(actual - expected) > eps) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        close(JoystickCalibration.applyAxis(0.10f, 0.10f), 0f, 0.0001f, "center");
        close(JoystickCalibration.applyAxis(1f, 0.10f), 1f, 0.0001f, "positive end");
        close(JoystickCalibration.applyAxis(-1f, 0.10f), -1f, 0.0001f, "negative end");
        close(JoystickCalibration.applyAxis(0.55f, 0.10f), 0.50f, 0.0001f, "positive half range");
        close(JoystickCalibration.applyAxis(-0.45f, 0.10f), -0.50f, 0.0001f, "negative half range");
        close(JoystickCalibration.applyAxis(-0.35f, -0.10f), -0.2777778f, 0.0002f, "negative center scaling");
        close(JoystickCalibration.applyAxis(0.40f, 0f), 0.40f, 0.0001f, "identity");
        System.out.println("JoystickCalibrationSelfTest: PASS");
    }
}
