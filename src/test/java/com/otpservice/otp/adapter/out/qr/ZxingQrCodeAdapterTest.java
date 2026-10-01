package com.otpservice.otp.adapter.out.qr;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZxingQrCodeAdapterTest {

    private static final String URI = "otpauth://totp/OTP%20Service:ana%40gmail.com"
            + "?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=OTP%20Service&algorithm=SHA1&digits=6&period=30";

    private final ZxingQrCodeAdapter adapter = new ZxingQrCodeAdapter();

    @Test
    void elQrSeLeeYDevuelveLaMismaUri() throws Exception {
        BitMatrix matrix = adapter.matrix(URI);

        assertThat(decode(matrix)).isEqualTo(URI);
    }

    @Test
    void generaUnSvgConFondoBlanco() {
        String svg = adapter.svg(URI);

        assertThat(svg).startsWith("<svg").endsWith("</svg>")
                .contains("fill=\"#ffffff\"")
                .contains("aria-label=");
    }

    private static String decode(BitMatrix matrix) throws Exception {
        int scale = 4;
        int width = matrix.getWidth() * scale;
        int height = matrix.getHeight() * scale;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = matrix.get(x / scale, y / scale) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
        return new QRCodeReader().decode(bitmap).getText();
    }
}
