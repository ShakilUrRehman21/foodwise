package com.foodwise.scan;

import com.recognition.software.jdeskew.ImageDeskew;
import net.sourceforge.tess4j.util.ImageHelper;
import org.springframework.stereotype.Component;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Receipt preprocessing before OCR (FR-SCAN-002): grayscale -> normalise size -> deskew -> median denoise ->
 * adaptive (local mean) binarisation, which copes with shadows and faded thermal paper better than a global threshold.
 * Pure Java (no OpenCV native dependency).
 */
@Component
public class ImagePreprocessor {

    static final int TARGET_MIN_WIDTH = 1200;
    static final int TARGET_MAX_WIDTH = 2400;

    public BufferedImage prepareReceipt(BufferedImage source) {
        BufferedImage gray = toGray(source);
        gray = normaliseWidth(gray);
        gray = deskew(gray);
        gray = median3(gray);
        return adaptiveThreshold(gray, 31, 12);
    }

    static BufferedImage toGray(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight()); // flatten transparency onto white
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    static BufferedImage normaliseWidth(BufferedImage img) {
        int w = img.getWidth();
        double scale = w < TARGET_MIN_WIDTH ? (double) TARGET_MIN_WIDTH / w : w > TARGET_MAX_WIDTH ? (double) TARGET_MAX_WIDTH / w : 1.0;
        if (Math.abs(scale - 1.0) < 0.01) {
            return img;
        }
        int nw = (int) Math.round(w * scale);
        int nh = (int) Math.round(img.getHeight() * scale);
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    static BufferedImage deskew(BufferedImage img) {
        try {
            double angle = new ImageDeskew(img).getSkewAngle();
            if (Math.abs(angle) > 0.5 && Math.abs(angle) < 20) {
                BufferedImage rotated = ImageHelper.rotateImage(img, -angle);
                return toGray(rotated);
            }
        } catch (RuntimeException ignored) {
            // deskew is best-effort
        }
        return img;
    }

    static BufferedImage median3(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        byte[] src = new byte[w * h];
        img.getRaster().getDataElements(0, 0, w, h, src);
        byte[] dst = src.clone();
        int[] window = new int[9];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int k = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    int row = (y + dy) * w;
                    for (int dx = -1; dx <= 1; dx++) {
                        window[k++] = src[row + x + dx] & 0xFF;
                    }
                }
                java.util.Arrays.sort(window);
                dst[y * w + x] = (byte) window[4];
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        out.getRaster().setDataElements(0, 0, w, h, dst);
        return out;
    }

    /** Local-mean threshold using an integral image: pixel is black if below (mean of window - c). */
    static BufferedImage adaptiveThreshold(BufferedImage img, int window, int c) {
        int w = img.getWidth(), h = img.getHeight();
        byte[] px = new byte[w * h];
        img.getRaster().getDataElements(0, 0, w, h, px);
        long[] integral = new long[(w + 1) * (h + 1)];
        for (int y = 1; y <= h; y++) {
            long rowSum = 0;
            for (int x = 1; x <= w; x++) {
                rowSum += px[(y - 1) * w + (x - 1)] & 0xFF;
                integral[y * (w + 1) + x] = integral[(y - 1) * (w + 1) + x] + rowSum;
            }
        }
        int r = window / 2;
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - r), y1 = Math.min(h - 1, y + r);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - r), x1 = Math.min(w - 1, x + r);
                long sum = integral[(y1 + 1) * (w + 1) + (x1 + 1)] - integral[y0 * (w + 1) + (x1 + 1)]
                        - integral[(y1 + 1) * (w + 1) + x0] + integral[y0 * (w + 1) + x0];
                int count = (y1 - y0 + 1) * (x1 - x0 + 1);
                int v = px[y * w + x] & 0xFF;
                out[y * w + x] = (byte) (v * count < (sum - (long) c * count) ? 0 : 255);
            }
        }
        BufferedImage bin = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        bin.getRaster().setDataElements(0, 0, w, h, out);
        return bin;
    }
}
