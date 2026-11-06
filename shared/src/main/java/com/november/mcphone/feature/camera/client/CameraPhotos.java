package com.november.mcphone.feature.camera.client;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * 포키: 포키폰 카메라로 찍은 사진 목록 (게임 폴더 mcphone/camera_photos.txt).
 * 포키톡·SNS 에는 여기 있는 사진만 올릴 수 있음 — F2 스크린샷, 컴퓨터 사진, 스티커는 안 됨.
 * 한 줄 = "파일이름" 또는 "파일이름\t지문(SHA-256)". 지문은 사진이 다 저장된 뒤 적어서,
 * 나중에 같은 이름으로 다른 그림을 덮어써도 지문이 달라 막힘.
 */
public final class CameraPhotos {
    private CameraPhotos() {
    }

    /** 이름 → 지문 ("" = 아직 안 적음) */
    private static Map<String, String> names;
    /** 지문 계산 캐시: 경로 → (수정시각:크기, 지문) */
    private static final Map<Path, String[]> HASHES = new HashMap<>();

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("mcphone").resolve("camera_photos.txt");
    }

    private static synchronized Map<String, String> names() {
        if (names == null) {
            names = new HashMap<>();
            try {
                if (Files.exists(file())) for (String line : Files.readAllLines(file(), StandardCharsets.UTF_8)) {
                    if (line.isBlank()) continue;
                    int tab = line.indexOf('\t');
                    String n = tab < 0 ? line : line.substring(0, tab);
                    String h = tab < 0 ? "" : line.substring(tab + 1);
                    if (!h.isEmpty() || !names.containsKey(n)) names.put(n, h);
                }
            } catch (IOException ignored) {
            }
        }
        return names;
    }

    private static void append(String line) {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    /** 찍기 직전: 이름만 먼저 적어 둠 */
    public static synchronized void add(String fileName) {
        if (fileName == null || names().containsKey(fileName)) return;
        names().put(fileName, "");
        append(fileName);
    }

    /** 사진 파일이 다 써진 뒤 (Screenshot.grab 콜백): 지문을 적음 */
    public static synchronized void pin(String fileName) {
        if (fileName == null) return;
        Path p = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve(fileName.endsWith(".png") ? fileName : fileName + ".png");
        String h = hash(p);
        if (h == null) return;
        String key = p.getFileName().toString();
        names().put(fileName, h);
        names().put(key, h);
        append(key + "\t" + h);
    }

    /** 이름만 보고 카메라 사진인지 (갤러리 목록용, 빠름) */
    public static synchronized boolean isListed(Path photo) {
        if (photo == null || photo.getFileName() == null) return false;
        String n = photo.getFileName().toString();
        return names().containsKey(n) || names().containsKey(n.endsWith(".png") ? n.substring(0, n.length() - 4) : n);
    }

    /** 올리기 전 확인: 이름이 목록에 있고, 지문까지 같아야 함 */
    public static synchronized boolean isCamera(Path photo) {
        if (!isListed(photo)) return false;
        String n = photo.getFileName().toString();
        String want = names().get(n);
        if (want == null) want = names().get(n.endsWith(".png") ? n.substring(0, n.length() - 4) : n);
        String now = hash(photo);
        if (now == null) return false;
        if (want == null || want.isEmpty()) {
            // 지문 생기기 전 사진(예전 버전에서 찍은 것): 지금 모습으로 지문을 박아 둠
            names().put(n, now);
            append(n + "\t" + now);
            return true;
        }
        return want.equals(now);
    }

    private static String hash(Path p) {
        try {
            if (!Files.isRegularFile(p)) return null;
            String stamp = Files.getLastModifiedTime(p).toMillis() + ":" + Files.size(p);
            String[] c = HASHES.get(p);
            if (c != null && c[0].equals(stamp)) return c[1];
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String h = HexFormat.of().formatHex(md.digest(Files.readAllBytes(p)));
            HASHES.put(p, new String[]{stamp, h});
            return h;
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }
}
