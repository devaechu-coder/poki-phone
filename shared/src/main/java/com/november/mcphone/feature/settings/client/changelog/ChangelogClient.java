package com.november.mcphone.feature.settings.client.changelog;

import com.november.mcphone.MCphone;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** 只在手机被实际打开时读取本机记录，不需要服务端或网络请求。 */
public final class ChangelogClient {
    private ChangelogClient() {}
    private static List<Changelog.Release> releases;
    public static List<Changelog.Release> releases() {
        if (releases == null) {
            releases = List.of();
            try (var stream = ChangelogClient.class.getResourceAsStream("/assets/mcphone/changelog.json")) {
                if (stream == null) throw new java.io.IOException("缺少包内更新日志");
                releases = Changelog.read(new InputStreamReader(stream, StandardCharsets.UTF_8));
            } catch (Exception e) { MCphone.LOGGER.warn("[MCphone] 更新日志读取失败", e); }
        }
        return releases;
    }
    public static Path pendingRecord() {
        String version = MCphone.getVersion();
        if (!Changelog.shouldNotify(version, "", Changelog.current(releases(), version) != null)) return null;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return null;
        var single = mc.getSingleplayerServer();
        var remote = mc.getCurrentServer();
        String scope = single != null ? "local:" + single.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                : remote != null ? "remote:" + remote.ip.toLowerCase(Locale.ROOT) : null;
        if (scope == null) return null;
        Path file = ChangelogStore.path(mc.gameDirectory.toPath(), mc.player.getUUID(), scope);
        try { return Changelog.shouldNotify(version, ChangelogStore.read(file), true) ? file : null; }
        catch (Exception e) {
            MCphone.LOGGER.warn("[MCphone] 更新日志查看记录读取失败", e);
            return null;
        }
    }
    public static void shown(Path record) {
        try { ChangelogStore.shown(record, MCphone.getVersion()); }
        catch (Exception e) { MCphone.LOGGER.warn("[MCphone] 更新日志查看记录保存失败", e); }
    }
}
