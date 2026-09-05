import pl.openai.pokeballmouse.PokeballDecoder;

public final class DecoderSelfTest {
    private static byte[] packet(int rawX, int rawY, int buttons, int ax, int ay, int az,
                                 int gx, int gy, int gz, int gw) {
        String r = String.format("%04x", rawX & 0xffff);
        String h = "" + r.charAt(1) + r.charAt(3) + r.charAt(2) + r.charAt(0);
        byte[] data = new byte[17];
        data[1] = (byte) buttons;
        data[2] = (byte) Integer.parseInt(h.substring(0, 2), 16);
        data[3] = (byte) Integer.parseInt(h.substring(2, 4), 16);
        data[4] = (byte) ((rawY >>> 8) & 0xff);
        data[5] = (byte) (rawY & 0xff);
        put16(data, 6, gx);
        put16(data, 8, gy);
        put16(data, 10, gz);
        put16(data, 12, gw);
        data[14] = (byte) az;
        data[15] = (byte) ay;
        data[16] = (byte) ax;
        return data;
    }

    private static void put16(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >>> 8) & 0xff);
        data[offset + 1] = (byte) (value & 0xff);
    }

    private static void near(float actual, float expected, float tolerance, String name) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(name + ": actual=" + actual + " expected=" + expected);
        }
    }

    public static void main(String[] args) {
        PokeballDecoder.Decoded min = PokeballDecoder.decode(packet(
                PokeballDecoder.JOY_X_MIN, PokeballDecoder.JOY_Y_MIN, 0, 0, 0, 0, 0, 0, 0, 32767));
        near(min.x, -1f, 0.001f, "x min");
        near(min.y, 1f, 0.001f, "y min");
        near(min.gyroW, 1f, 0.001f, "gyro w");
        near(min.pitch, 0f, 0.01f, "pitch identity");
        near(min.yaw, 0f, 0.01f, "yaw identity");
        near(min.roll, 0f, 0.01f, "roll identity");

        PokeballDecoder.Decoded max = PokeballDecoder.decode(packet(
                PokeballDecoder.JOY_X_MAX, PokeballDecoder.JOY_Y_MAX, 3, 127, -127, 64,
                2800, -2800, 1400, 20000));
        near(max.x, 1f, 0.001f, "x max");
        near(max.y, -1f, 0.001f, "y max");
        near(max.accelX, 1f, 0.001f, "accel x");
        near(max.accelY, -1f, 0.001f, "accel y");
        near(max.accelZ, 64f / 127f, 0.001f, "accel z");
        near(max.gyroX, 0.5f, 0.001f, "gyro x");
        near(max.gyroY, -0.5f, 0.001f, "gyro y");
        near(max.gyroZ, 0.25f, 0.001f, "gyro z");
        if (!max.topPressed || !max.stickPressed) throw new AssertionError("button bits");

        int cx = Math.round((PokeballDecoder.JOY_X_MIN + PokeballDecoder.JOY_X_MAX) / 2f);
        int cy = Math.round((PokeballDecoder.JOY_Y_MIN + PokeballDecoder.JOY_Y_MAX) / 2f);
        PokeballDecoder.Decoded center = PokeballDecoder.decode(packet(cx, cy, 0, 0, 0, 0, 0, 0, 0, 32767));
        near(center.x, 0f, 0.001f, "x center");
        near(center.y, 0f, 0.001f, "y center");

        System.out.println("DecoderSelfTest: PASS");
    }
}
