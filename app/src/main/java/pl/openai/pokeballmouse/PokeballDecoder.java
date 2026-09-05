package pl.openai.pokeballmouse;

import java.util.Locale;

/** Pure-Java decoder for Poké Ball Plus input report bytes. */
public final class PokeballDecoder {
    private PokeballDecoder() {}

    public static final int JOY_X_MIN = 7980;
    public static final int JOY_X_MAX = 49620;
    public static final int JOY_Y_MIN = 13350;
    public static final int JOY_Y_MAX = 50280;

    public static final class Decoded {
        public final float x;
        public final float y;
        public final boolean topPressed;
        public final boolean stickPressed;
        public final float gyroX;
        public final float gyroY;
        public final float gyroZ;
        public final float gyroW;
        public final float pitch;
        public final float yaw;
        public final float roll;
        public final float accelX;
        public final float accelY;
        public final float accelZ;

        private Decoded(float x, float y, boolean topPressed, boolean stickPressed,
                        float gyroX, float gyroY, float gyroZ, float gyroW,
                        float pitch, float yaw, float roll,
                        float accelX, float accelY, float accelZ) {
            this.x = clamp(x);
            this.y = clamp(y);
            this.topPressed = topPressed;
            this.stickPressed = stickPressed;
            this.gyroX = gyroX;
            this.gyroY = gyroY;
            this.gyroZ = gyroZ;
            this.gyroW = gyroW;
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;
            this.accelX = accelX;
            this.accelY = accelY;
            this.accelZ = accelZ;
        }
    }

    public static Decoded decode(byte[] data) {
        if (data == null || data.length < 6) {
            throw new IllegalArgumentException("Poké Ball Plus input report must contain at least 6 bytes");
        }

        int buttonByte = data[1] & 0xff;
        boolean top = (buttonByte & 0x01) != 0;
        boolean stick = (buttonByte & 0x02) != 0;

        String hexX = String.format(Locale.ROOT, "%02x%02x", data[2] & 0xff, data[3] & 0xff);
        String reorderedX = "" + hexX.charAt(3) + hexX.charAt(0) + hexX.charAt(2) + hexX.charAt(1);
        int rawX = Integer.parseInt(reorderedX, 16);
        int rawY = ((data[4] & 0xff) << 8) | (data[5] & 0xff);

        float x = 2f * ((rawX - JOY_X_MIN) / (float) (JOY_X_MAX - JOY_X_MIN)) - 1f;
        float y = -2f * ((rawY - JOY_Y_MIN) / (float) (JOY_Y_MAX - JOY_Y_MIN)) + 1f;

        float gx = Float.NaN, gy = Float.NaN, gz = Float.NaN, gw = Float.NaN;
        float pitch = Float.NaN, yaw = Float.NaN, roll = Float.NaN;
        if (data.length >= 14) {
            // Reverse-engineered Poké Ball Plus report: quaternion-like orientation values.
            // X/Y/Z use the scale observed by the reference PBP tools, W uses signed int16 / 32767.
            gx = signed16(data[6], data[7]) / 5600f;
            gy = signed16(data[8], data[9]) / 5600f;
            gz = signed16(data[10], data[11]) / 5600f;
            gw = signed16(data[12], data[13]) / 32767f;
            float[] e = quaternionToEuler(gx, gy, gz, gw);
            pitch = e[0]; yaw = e[1]; roll = e[2];
        }

        float accelX = Float.NaN, accelY = Float.NaN, accelZ = Float.NaN;
        if (data.length >= 17) {
            accelX = data[data.length - 1] / 127f;
            accelY = data[data.length - 2] / 127f;
            accelZ = data[data.length - 3] / 127f;
        }

        return new Decoded(x, y, top, stick,
                gx, gy, gz, gw, pitch, yaw, roll,
                accelX, accelY, accelZ);
    }

    private static int signed16(byte hi, byte lo) {
        return (short) (((hi & 0xff) << 8) | (lo & 0xff));
    }

    private static float[] quaternionToEuler(float qx, float qy, float qz, float qw) {
        qx = clamp(qx); qy = clamp(qy); qz = clamp(qz); qw = clamp(qw);
        double sinrCosp = 2.0 * (qw * qx + qy * qz);
        double cosrCosp = 1.0 - 2.0 * (qx * qx + qy * qy);
        float roll = (float) Math.toDegrees(Math.atan2(sinrCosp, cosrCosp));

        double t = 2.0 * (qw * qy - qx * qz);
        t = Math.max(-1.0, Math.min(1.0, t));
        float pitch = (float) Math.toDegrees(Math.asin(t));

        double sinyCosp = 2.0 * (qw * qz + qx * qy);
        double cosyCosp = 1.0 - 2.0 * (qy * qy + qz * qz);
        float yaw = (float) Math.toDegrees(Math.atan2(sinyCosp, cosyCosp));
        return new float[]{pitch, yaw, roll};
    }

    private static float clamp(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }
}
