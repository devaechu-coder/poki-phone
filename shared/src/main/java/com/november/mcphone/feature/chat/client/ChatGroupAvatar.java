package com.november.mcphone.feature.chat.client;

import com.november.mcphone.core.client.PlayerAvatar;
import com.november.mcphone.feature.chat.group.GroupSummary;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;

/** 群头像按人数居中拼合 MC 方形头像；沿用皮肤缓存，底框由调用方负责，避免嵌套描边。 */
final class ChatGroupAvatar {
    private ChatGroupAvatar() {}
    record Tile(float x, float y, int size) {}
    private static final List<List<Tile>> DEFAULT_TILES = List.of(
            layout(0, ChatLayout.AVATAR_SIZE), layout(1, ChatLayout.AVATAR_SIZE),
            layout(2, ChatLayout.AVATAR_SIZE), layout(3, ChatLayout.AVATAR_SIZE), layout(4, ChatLayout.AVATAR_SIZE));

    /** 两人横排、三人上单下双、四人四宫格；半像素中心保留到绘制，不能先向左上取整。 */
    static List<Tile> layout(int count, int size) {
        if (count < 0 || count > 4 || size < 3) throw new IllegalArgumentException("群头像人数或尺寸无效");
        if (count == 0) return List.of();
        if (count == 1) return List.of(new Tile(0, 0, size));
        int tile = (size - 1) / 2, step = tile + 1;
        float edge = (size - (tile * 2 + 1)) / 2f, center = (size - tile) / 2f;
        return switch (count) {
            case 2 -> List.of(new Tile(edge, center, tile), new Tile(edge + step, center, tile));
            case 3 -> List.of(new Tile(center, edge, tile), new Tile(edge, edge + step, tile),
                    new Tile(edge + step, edge + step, tile));
            default -> List.of(new Tile(edge, edge, tile), new Tile(edge + step, edge, tile),
                    new Tile(edge, edge + step, tile), new Tile(edge + step, edge + step, tile));
        };
    }
    static void draw(GuiGraphics g, GroupSummary room, int x, int y, int size) {
        var tiles = size == ChatLayout.AVATAR_SIZE ? DEFAULT_TILES.get(room.portraits().size()) : layout(room.portraits().size(), size);
        for (int i = 0; i < tiles.size(); i++) {
            var tile = tiles.get(i);
            g.pose().pushPose();
            try {
                g.pose().translate(x + tile.x(), y + tile.y(), 0);
                PlayerAvatar.draw(g, room.portraits().get(i), 0, 0, tile.size());
            } finally { g.pose().popPose(); }
        }
    }
}
