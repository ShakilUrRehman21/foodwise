package com.foodwise.scan;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.foodwise.config.FoodwiseProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Experimental countertop "grocery haul" recognition (FR-SCAN-005/006): a YOLOv8 model exported to ONNX and run
 * in-process with ONNX Runtime for Java (SYSTEM_DESIGN 6.2/6.3). Disabled until an approved model file is configured
 * via {@code foodwise.scan.haul-model-path} - training is blocked on dataset licence approval (Roadmap UD-004).
 */
@Component
public class HaulDetector {

    private static final Logger log = LoggerFactory.getLogger(HaulDetector.class);
    static final int INPUT = 640;
    static final float NMS_IOU = 0.45f;

    private final double threshold;
    private OrtEnvironment env;
    private OrtSession session;
    private String inputName;
    private List<String> labels = List.of();
    private String unavailableReason;

    public record Detection(String label, float confidence, float x, float y, float w, float h) {
    }

    public HaulDetector(FoodwiseProperties props) {
        this.threshold = props.scan().haulConfidenceThreshold();
        String modelPath = props.scan().haulModelPath();
        if (modelPath == null || modelPath.isBlank() || !Files.isRegularFile(Path.of(modelPath))) {
            unavailableReason = "Grocery haul recognition is not enabled on this server yet (no approved model installed).";
            log.info("Haul detection disabled: no ONNX model configured (foodwise.scan.haul-model-path).");
            return;
        }
        try {
            env = OrtEnvironment.getEnvironment();
            session = env.createSession(modelPath, new OrtSession.SessionOptions());
            inputName = session.getInputNames().iterator().next();
            labels = loadLabels(props.scan().haulLabelsPath(), session);
            log.info("Haul detection model loaded from {} with {} classes", modelPath, labels.size());
        } catch (Throwable e) {
            unavailableReason = "Grocery haul recognition failed to start on the server.";
            log.error("Could not load ONNX model {}", modelPath, e);
            session = null;
        }
    }

    public boolean isAvailable() {
        return session != null;
    }

    public String unavailableReason() {
        return unavailableReason;
    }

    public synchronized List<Detection> detect(BufferedImage image) throws Exception {
        if (session == null) {
            throw new IllegalStateException(unavailableReason);
        }
        float scale = Math.min((float) INPUT / image.getWidth(), (float) INPUT / image.getHeight());
        int nw = Math.round(image.getWidth() * scale), nh = Math.round(image.getHeight() * scale);
        int padX = (INPUT - nw) / 2, padY = (INPUT - nh) / 2;
        BufferedImage letterbox = new BufferedImage(INPUT, INPUT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = letterbox.createGraphics();
        g.setColor(new Color(114, 114, 114));
        g.fillRect(0, 0, INPUT, INPUT);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, padX, padY, nw, nh, null);
        g.dispose();

        float[] chw = new float[3 * INPUT * INPUT];
        int plane = INPUT * INPUT;
        for (int y = 0; y < INPUT; y++) {
            for (int x = 0; x < INPUT; x++) {
                int rgb = letterbox.getRGB(x, y);
                int i = y * INPUT + x;
                chw[i] = ((rgb >> 16) & 0xFF) / 255f;
                chw[plane + i] = ((rgb >> 8) & 0xFF) / 255f;
                chw[2 * plane + i] = (rgb & 0xFF) / 255f;
            }
        }
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), new long[]{1, 3, INPUT, INPUT});
             OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
            float[][] out = ((float[][][]) result.get(0).getValue())[0]; // [4 + classes][anchors]
            int classes = out.length - 4;
            int anchors = out[0].length;
            List<Detection> raw = new ArrayList<>();
            for (int a = 0; a < anchors; a++) {
                int best = -1;
                float bestScore = 0;
                for (int c = 0; c < classes; c++) {
                    if (out[4 + c][a] > bestScore) {
                        bestScore = out[4 + c][a];
                        best = c;
                    }
                }
                if (best >= 0 && bestScore >= threshold) {
                    float cx = (out[0][a] - padX) / scale, cy = (out[1][a] - padY) / scale;
                    float w = out[2][a] / scale, h = out[3][a] / scale;
                    String label = best < labels.size() ? labels.get(best) : "class_" + best;
                    raw.add(new Detection(label, bestScore, cx - w / 2, cy - h / 2, w, h));
                }
            }
            return nms(raw);
        }
    }

    static List<Detection> nms(List<Detection> detections) {
        List<Detection> sorted = new ArrayList<>(detections);
        sorted.sort(Comparator.comparingDouble(Detection::confidence).reversed());
        List<Detection> keep = new ArrayList<>();
        for (Detection d : sorted) {
            boolean suppressed = false;
            for (Detection k : keep) {
                if (k.label().equals(d.label()) && iou(k, d) > NMS_IOU) {
                    suppressed = true;
                    break;
                }
            }
            if (!suppressed) {
                keep.add(d);
            }
        }
        return keep;
    }

    static float iou(Detection a, Detection b) {
        float x1 = Math.max(a.x(), b.x()), y1 = Math.max(a.y(), b.y());
        float x2 = Math.min(a.x() + a.w(), b.x() + b.w()), y2 = Math.min(a.y() + a.h(), b.y() + b.h());
        float inter = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        float union = a.w() * a.h() + b.w() * b.h() - inter;
        return union <= 0 ? 0 : inter / union;
    }

    private static List<String> loadLabels(String labelsPath, OrtSession session) throws Exception {
        if (labelsPath != null && !labelsPath.isBlank() && Files.isRegularFile(Path.of(labelsPath))) {
            return Files.readAllLines(Path.of(labelsPath)).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        // Ultralytics exports embed "names" metadata like {0: 'apple', 1: 'banana'}
        String names = session.getMetadata().getCustomMetadata().get("names");
        List<String> out = new ArrayList<>();
        if (names != null) {
            Matcher m = Pattern.compile("(\\d+)\\s*:\\s*'([^']*)'").matcher(names);
            while (m.find()) {
                out.add(m.group(2));
            }
        }
        return out;
    }

    @PreDestroy
    void close() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (Exception ignored) {
            // shutting down
        }
    }
}
