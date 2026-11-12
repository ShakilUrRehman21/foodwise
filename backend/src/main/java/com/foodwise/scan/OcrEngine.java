package com.foodwise.scan;

import com.foodwise.config.FoodwiseProperties;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Local Tesseract 5 OCR (AD-002: no cloud/generative AI). Uses Tess4J (in-process, JNA) and falls back to the
 * {@code tesseract} command-line binary if the native library cannot be loaded. Temporary files used by the
 * fallback are deleted immediately after extraction (NFR-SEC-003).
 */
@Component
public class OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(OcrEngine.class);
    private static final List<String> KNOWN_TESSDATA = List.of(
            "/usr/share/tesseract-ocr/5/tessdata", "/usr/share/tesseract-ocr/4.00/tessdata", "/usr/share/tessdata",
            "/usr/local/share/tessdata", "/opt/homebrew/share/tessdata", "C:\\Program Files\\Tesseract-OCR\\tessdata");

    private final String language;
    private final String tessdataPath;
    private volatile Boolean tess4jUsable;

    public OcrEngine(FoodwiseProperties props) {
        this.language = props.scan().ocrLanguage();
        this.tessdataPath = resolveTessdata(props.scan().tessdataPath());
        if (tessdataPath == null) {
            log.warn("Tesseract language data not found. Set TESSDATA_PATH (folder containing {}.traineddata) to enable receipt OCR.",
                    language);
        } else {
            log.info("OCR using tessdata at {}", tessdataPath);
        }
    }

    public boolean isConfigured() {
        return tessdataPath != null;
    }

    /** Extracts text from a preprocessed image. Throws {@link OcrUnavailableException} when no OCR engine works. */
    public String extractText(BufferedImage image) {
        if (tessdataPath == null) {
            throw new OcrUnavailableException("OCR language data (" + language + ".traineddata) is not installed on the server.");
        }
        if (!Boolean.FALSE.equals(tess4jUsable)) {
            try {
                ITesseract t = new Tesseract();
                t.setDatapath(tessdataPath);
                t.setLanguage(language);
                t.setPageSegMode(6);   // single uniform block of text (SYSTEM_DESIGN 6.1)
                t.setOcrEngineMode(3); // default (LSTM)
                t.setVariable("preserve_interword_spaces", "1");
                t.setVariable("user_defined_dpi", "300");
                String text = t.doOCR(image);
                tess4jUsable = true;
                return text == null ? "" : text;
            } catch (LinkageError e) {
                tess4jUsable = false;
                log.warn("Tess4J native library unavailable ({}); falling back to the tesseract CLI.", e.getMessage());
            } catch (Exception e) {
                throw new IllegalStateException("OCR failed: " + e.getMessage(), e);
            }
        }
        return viaCli(image);
    }

    private String viaCli(BufferedImage image) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("foodwise-ocr-", ".png");
            ImageIO.write(image, "png", tmp.toFile());
            List<String> cmd = new ArrayList<>(List.of("tesseract", tmp.toString(), "stdout", "-l", language, "--psm", "6",
                    "--tessdata-dir", tessdataPath, "-c", "preserve_interword_spaces=1"));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("OCR timed out");
            }
            if (p.exitValue() != 0) {
                throw new OcrUnavailableException("The tesseract command failed (exit " + p.exitValue() + ").");
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new OcrUnavailableException("Tesseract OCR is not installed on the server.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OCR interrupted", e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    private String resolveTessdata(String configured) {
        List<String> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            candidates.add(configured);
        }
        String env = System.getenv("TESSDATA_PREFIX");
        if (env != null && !env.isBlank()) {
            candidates.add(env);
            candidates.add(Path.of(env, "tessdata").toString());
        }
        candidates.addAll(KNOWN_TESSDATA);
        for (String c : candidates) {
            if (Files.isRegularFile(Path.of(c, language + ".traineddata"))) {
                return Path.of(c).toAbsolutePath().toString();
            }
        }
        return null;
    }

    public static class OcrUnavailableException extends RuntimeException {
        public OcrUnavailableException(String message) {
            super(message);
        }
    }
}
