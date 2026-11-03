package com.november.mcphone.feature.chat.group;

import com.november.mcphone.feature.chat.ChatImage;
import com.november.mcphone.feature.chat.group.net.*;
import java.util.*;

/** 真正的分片装配与限流，验证跨连接/跨群/任务票据和恶意元数据隔离。 */
public final class GroupImageAssemblyTest {
    private static int checks;
    private static final UUID G = new UUID(1, 1), H = new UUID(1, 2), R = new UUID(2, 1), S = new UUID(2, 2);
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static GroupCommand.Upload chunk(UUID group, UUID request, int index) { return new GroupCommand.Upload(request, group, 16, 16, 1, 0, index, 2, new byte[]{(byte) index, 5}); }
    public static void main(String[] args) {
        var a = new GroupImageAssembly(); var connection = new Object(); var other = new Object();
        check(a.accept(connection, chunk(G, R, 0), 8, 1000) == null, "首片只登记未完成上传");
        check(a.accept(other, chunk(G, R, 1), 8, 1001) == null, "另一连接不能补完");
        var complete = a.accept(connection, chunk(G, R, 1), 8, 1002);
        check(Arrays.equals(complete.png(), new byte[]{0, 5, 1, 5}), "按顺序装配原字节");
        check(a.accept(connection, chunk(G, R, 1), 8, 1003) == null, "完成后不能重放尾片");
        a.accept(connection, chunk(G, R, 0), 8, 1000);
        check(a.accept(connection, chunk(H, R, 1), 8, 1001) == null && a.accept(connection, chunk(G, R, 1), 8, 1002) == null, "跨群片拒收并销毁任务");
        a.accept(connection, chunk(G, R, 0), 8, 1000);
        check(a.accept(connection, chunk(G, S, 1), 8, 1001) == null, "票据必须一致");
        a.accept(connection, chunk(G, R, 0), 8, 1000);
        check(a.accept(connection, chunk(G, R, 1), 8, 1000 + ChatImage.UPLOAD_TIMEOUT_MS + 1) == null, "超时片拒收");
        a.accept(connection, chunk(G, R, 0), 3, 1000);
        check(a.accept(connection, chunk(G, R, 1), 3, 1001) == null, "累计字节不得超过服务器上限");
        a.accept(connection, chunk(G, R, 0), 8, 1000);
        var changed = new GroupCommand.Upload(R, G, 17, 16, 1, 0, 1, 2, new byte[]{1});
        check(a.accept(connection, changed, 8, 1001) == null, "不能在中途换元数据");
        var huge = new GroupCommand.Upload(R, G, 513, 16, 1, 0, 0, 1, new byte[]{1});
        check(a.accept(connection, huge, 8, 1000) == null, "拒绝超大尺寸");
        var animated = new GroupCommand.Upload(R, G, 16, 16, 2, 19, 0, 1, new byte[]{1});
        check(a.accept(connection, animated, 8, 1000) == null, "拒绝不合法动画时长");
        byte[] bytes = {1}; var immutable = new GroupCommand.Upload(R, G, 16, 16, 1, 0, 0, 1, bytes);
        bytes[0] = 2; immutable.data()[0] = 3; check(immutable.data()[0] == 1, "分片输入与返回值均不可污染");
        check(!GroupImageFiles.identity(G, bytes).equals(GroupImageFiles.identity(H, bytes)), "相同图片在不同群有独立身份");
        check(GroupImageFiles.identity(G, bytes).equals(GroupImageFiles.identity(G, bytes.clone())), "相同群相同内容可去重");
        var throttle = new GroupThrottle();
        check(throttle.allow(connection, GroupThrottle.Kind.CREATE, 1000) && !throttle.allow(connection, GroupThrottle.Kind.CREATE, 1001), "建群限流");
        check(throttle.allow(other, GroupThrottle.Kind.CREATE, 1001), "不同连接互不消耗额度");
        check(throttle.allow(connection, GroupThrottle.Kind.TEXT, 1001), "不同命令独立限流");
        check(throttle.allow(connection, GroupThrottle.Kind.CREATE, 11000), "建群冷却到期可重试");
        System.out.println("全部通过：" + checks + " 条断言");
    }
}
