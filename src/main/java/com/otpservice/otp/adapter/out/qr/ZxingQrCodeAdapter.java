package com.otpservice.otp.adapter.out.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.otpservice.otp.application.port.out.QrCodePort;
import org.springframework.stereotype.Component;

import java.util.Map;

// Fondo blanco propio: la interfaz es oscura y los lectores de QR necesitan contraste.
@Component
public class ZxingQrCodeAdapter implements QrCodePort {

    private static final int QUIET_ZONE_MODULES = 4;

    @Override
    public String svg(String content) {
        BitMatrix matrix = matrix(content);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + width + " " + height + "\""
                + " shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"Código QR para la app autenticadora\">"
                + "<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>"
                + "<path fill=\"#000000\" d=\"" + path + "\"/></svg>";
    }

    BitMatrix matrix(String content) {
        try {
            return new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.MARGIN, QUIET_ZONE_MODULES,
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.CHARACTER_SET, "UTF-8"));
        } catch (WriterException exception) {
            throw new IllegalStateException("No se pudo generar el código QR", exception);
        }
    }
}
