package com.november.mcphone.feature.sns;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.november.mcphone.feature.sns.net.SnsServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/** 운영진용: /포키폰 sns 검토함 | 목록 | 복구 <글번호> | 삭제 <글번호> | 정지 <닉> | 정지해제 <닉> */
public final class SnsCommand {
    private SnsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(root("포키폰"));
        d.register(root("pokiphone"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name).requires(s -> s.hasPermission(2))
                .then(Commands.literal("sns")
                        .then(Commands.literal("검토함").executes(c -> list(c, true)))
                        .then(Commands.literal("이로치테스트").then(Commands.argument("species", StringArgumentType.word()).executes(SnsCommand::shinyTest)))
                        .then(Commands.literal("shinytest").then(Commands.argument("species", StringArgumentType.word()).executes(SnsCommand::shinyTest)))
                        .then(Commands.literal("review").executes(c -> list(c, true)))
                        .then(Commands.literal("목록").executes(c -> list(c, false)))
                        .then(Commands.literal("list").executes(c -> list(c, false)))
                        .then(Commands.literal("복구").then(Commands.argument("id", StringArgumentType.word()).executes(c -> restore(c))))
                        .then(Commands.literal("restore").then(Commands.argument("id", StringArgumentType.word()).executes(c -> restore(c))))
                        .then(Commands.literal("삭제").then(Commands.argument("id", StringArgumentType.word()).executes(c -> delete(c))))
                        .then(Commands.literal("delete").then(Commands.argument("id", StringArgumentType.word()).executes(c -> delete(c))))
                        .then(Commands.literal("정지").then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> mute(c, true))))
                        .then(Commands.literal("mute").then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> mute(c, true))))
                        .then(Commands.literal("정지해제").then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> mute(c, false))))
                        .then(Commands.literal("unmute").then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> mute(c, false)))));
    }

    /** 이로치를 안 잡아도 자랑 카드가 어떻게 보이는지 확인 (영어 이름: charizard) */
    private static int shinyTest(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p = c.getSource().getPlayerOrException();
        String sp = StringArgumentType.getString(c, "species").toLowerCase(java.util.Locale.ROOT);
        if (!sp.contains(":")) sp = "cobblemon:" + sp;
        String biome = p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
        String card = "species=" + sp + "|shiny=1|lv=36|nature=cobblemon:adamant|ability=blaze|gender=MALE|iv=158|ball=cobblemon:ultra_ball|biome=" + biome + "|test=1";
        com.november.mcphone.feature.sns.net.SnsServer.autoPost(p, card);
        say(c, "§a테스트 이로치 카드를 SNS 에 올렸어 (폰 SNS 에서 확인, 지우려면 글에서 삭제)");
        return 1;
    }

    private static void say(CommandContext<CommandSourceStack> c, String s) {
        c.getSource().sendSuccess(() -> Component.literal(s), false);
    }

    private static int list(CommandContext<CommandSourceStack> c, boolean held) {
        var data = SnsData.get(c.getSource().getServer());
        var fmt = new SimpleDateFormat("MM/dd HH:mm");
        int n = 0;
        say(c, held ? "§d[포키 SNS] 검토함 (신고 " + SnsServer.REPORT_LIMIT + "회 이상)" : "§d[포키 SNS] 최근 글");
        for (var p : data.posts()) {
            if (held != p.held && held) continue;
            if (n++ >= 15) break;
            String text = p.text.length() > 40 ? p.text.substring(0, 40) + "…" : p.text;
            say(c, "§7" + p.id.toString().substring(0, 8) + " §f" + p.name + " §8" + fmt.format(new Date(p.time))
                    + (p.image != null ? " §b[사진]" : "") + (p.reports.isEmpty() ? "" : " §c신고" + p.reports.size())
                    + "\n §7" + (text.isEmpty() ? "(글 없음)" : text));
        }
        if (n == 0) say(c, "§7비어 있음");
        else say(c, "§8복구: /포키폰 sns 복구 <앞8자리>  삭제: /포키폰 sns 삭제 <앞8자리>");
        return n;
    }

    private static SnsData.Post find(CommandContext<CommandSourceStack> c) {
        var p = SnsData.get(c.getSource().getServer()).findPrefix(StringArgumentType.getString(c, "id"));
        if (p == null) say(c, "§c그 번호의 글이 없거나 여러 개야. 앞 8자리를 정확히 써 줘");
        return p;
    }

    private static int restore(CommandContext<CommandSourceStack> c) {
        var p = find(c);
        if (p == null) return 0;
        p.held = false; p.reports.clear();
        SnsData.get(c.getSource().getServer()).setDirty();
        say(c, "§a복구했어 (신고 기록 초기화)");
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> c) {
        var p = find(c);
        if (p == null) return 0;
        SnsServer.delete(c.getSource().getServer(), SnsData.get(c.getSource().getServer()), p);
        say(c, "§a삭제했어");
        return 1;
    }

    private static int mute(CommandContext<CommandSourceStack> c, boolean on) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var data = SnsData.get(c.getSource().getServer());
        int n = 0;
        for (var gp : GameProfileArgument.getGameProfiles(c, "player")) {
            data.mute(gp.getId(), on);
            say(c, "§a" + gp.getName() + (on ? " 님 SNS 글·댓글 정지" : " 님 SNS 정지 해제"));
            n++;
        }
        return n;
    }
}
