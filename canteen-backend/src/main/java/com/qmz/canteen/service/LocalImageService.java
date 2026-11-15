package com.qmz.canteen.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 本地图片 → base64 data URL。只接受本地路径，拒绝远程 URL 与路径穿越。
 * 供菜品草稿视觉识别、经营助理识图等复用。
 */
@Service
public class LocalImageService {

    @Value("${canteen.upload-dir:uploads}")
    private String uploadDir;

    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    /** 解析为 data URL；无图片或找不到文件时返回 null */
    public String toDataUrl(String image) {
        if (image == null || image.isBlank()) {
            return null;
        }
        String trimmed = image.trim();
        String filename = trimmed.substring(trimmed.lastIndexOf('/') + 1);
        if (filename.isBlank() || filename.contains("..")) {
            return null;
        }
        List<Path> candidates = new ArrayList<>();
        Path uploads = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (trimmed.contains("/uploads/")) {
            candidates.add(uploads.resolve(filename));
        }
        if (trimmed.contains("/dishes/")) {
            candidates.add(Paths.get("..", "canteen-frontend", "public", "dishes", filename)
                    .toAbsolutePath().normalize());
            candidates.add(Paths.get("public", "dishes", filename).toAbsolutePath().normalize());
        }
        candidates.add(uploads.resolve(filename));

        for (Path path : candidates) {
            try {
                if (!Files.isRegularFile(path) || Files.size(path) > MAX_IMAGE_BYTES) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(path);
                return "data:" + mimeOf(filename) + ";base64," + Base64.getEncoder().encodeToString(bytes);
            } catch (Exception ignored) {
                // 继续尝试下一个候选路径
            }
        }
        return null;
    }

    private static String mimeOf(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }
}
