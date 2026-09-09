package pl.openai.pokeballmouse;

/**
 * Compatibility placeholder for the obsolete pre-0.6 calibration wizard.
 *
 * Older repository uploads could leave a CalibrationWizard.java behind even
 * after the current app stopped referencing it.  That stale source referenced
 * removed InputRouter methods and removed string resources, causing javac to
 * fail.  Keeping this tiny source at the same path makes incremental GitHub
 * uploads deterministic while the current joystick "Set as 0" calibration is
 * handled by JoystickCalibration + ControlConfig.
 */
public final class CalibrationWizard {
    public static final String COMPAT_MARKER = "PBM_CALIBRATION_WIZARD_COMPAT_061";
    private CalibrationWizard() {}
}
