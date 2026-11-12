package com.foodwise.scan;

import com.foodwise.common.ApiException;
import com.foodwise.config.FoodwiseProperties;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.UUID;

/**
 * Local filesystem storage for receipt / haul images (Roadmap UD-003: local storage for MVP). Images are kept for
 * audit next to the scan session; nothing is sent to third parties. Paths are generated server-side only.
 */
@Service
public class ImageStorageService {

    static final long MAX_PIXELS = 40_000_000L;

    private final Path root;

    public ImageStorageService(FoodwiseProperties props) throws IOException {
        this.root = Path.of(props.scan().storageDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public record StoredImage(String relativePath, BufferedImage image) {
    }

    /** Validates (JPEG/PNG by magic bytes, bounded dimensions), decodes and stores the image. */
    public StoredImage store(UUID kitchenId, byte[] bytes) {
        String ext = detectExtension(bytes);
        BufferedImage image = decode(bytes);
        String relative = kitchenId + "/" + UUID.randomUUID() + "." + ext;
        Path target = resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store image", e);
        }
        return new StoredImage(relative, image);
    }

    public byte[] read(String relativePath) {
        try {
            return Files.readAllBytes(resolve(relativePath));
        } catch (IOException e) {
            throw ApiException.notFound("Image not found.");
        }
    }

    public String contentType(String relativePath) {
        return relativePath.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    private Path resolve(String relative) {
        Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root)) {
            throw ApiException.forbidden("Invalid image path.");
        }
        return p;
    }

    static String detectExtension(byte[] b) {
        if (b == null || b.length < 12) {
            throw ApiException.validation("file", "The file is empty or not an image.");
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        throw ApiException.validation("file", "Please upload a JPEG or PNG photo.");
    }

    /** Reads dimensions before decoding to reject decompression bombs. */
    static BufferedImage decode(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw ApiException.validation("file", "Unsupported image format.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS || pixels <= 0) {
                    throw ApiException.validation("file", "Image dimensions are too large.");
                }
                BufferedImage img = reader.read(0);
                if (img == null) {
                    throw ApiException.validation("file", "Could not read the image.");
                }
                return img;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw ApiException.validation("file", "Could not read the image. Try another photo.");
        }
    }
}
