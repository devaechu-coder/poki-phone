package com.november.mcphone.feature.camera.client;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

/**
 * 포키: 포키폰 카메라로 찍은 사진 이름 목록 (게임 폴더 mcphone/camera_photos.txt).
 * 포키톡·SNS 에는 여기 있는 사진만 올릴 수 있음 — F2 스크린샷, 컴퓨터 사진, 스티커는 안 됨.
 */
public final class CameraPhotos {
    private CameraPhotos() {
    }

    private static Set<String> names;

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("mcphone").resolve("camera_photos.txt");
    }

    private static synchronized Set<String> names() {
        if (names == null) {
            names = new HashSet<>();
            try {
                if (Files.exists(file())) names.addAll(Files.readAllLines(file(), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }
        return names;
    }

    public static synchronized void add(String fileName) {
        if (fileName == null || !names().add(fileName)) return;
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), fileName + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    public static synchronized boolean isCamera(Path photo) {
        return photo != null && photo.getFileName() != null && names().contains(photo.getFileName().toString());
    }
}
