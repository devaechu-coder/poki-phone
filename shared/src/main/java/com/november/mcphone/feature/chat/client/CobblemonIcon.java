package com.november.mcphone.feature.chat.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 포키: 코블몬 파티 칸·도감과 같은 방법(drawProfilePokemon)으로 포켓몬 모델을 작게 그린다.
 * 포키폰은 코블몬 없이도 돌아야 해서 리플렉션. 한 번이라도 실패하면 꺼지고 false 를 돌려줌 (그럼 글자로 대신).
 */
public final class CobblemonIcon {
    private CobblemonIcon() {}
    private static boolean tried, broken;
    private static Method getByIdentifier, draw;
    private static Constructor<?> renderable, floating;
    private static Object profilePose;
    /** 모델마다 움직임 상태 (숨쉬기 애니메이션 이어지게) */
    private static final Map<String, Object> STATES = new HashMap<>();

    private static boolean init() {
        if (tried) return !broken;
        tried = true;
        try {
            Class<?> species = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");
            getByIdentifier = species.getMethod("getByIdentifier", ResourceLocation.class);
            Class<?> rp = Class.forName("com.cobblemon.mod.common.pokemon.RenderablePokemon");
            Class<?> sp = Class.forName("com.cobblemon.mod.common.pokemon.Species");
            renderable = rp.getConstructor(sp, Set.class, ItemStack.class);
            floating = Class.forName("com.cobblemon.mod.common.client.render.models.blockbench.FloatingState").getConstructor();
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object pose = Enum.valueOf((Class) Class.forName("com.cobblemon.mod.common.entity.PoseType"), "PROFILE");
            profilePose = pose;
            for (Method m : Class.forName("com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt").getMethods())
                if (m.getName().equals("drawProfilePokemon") && m.getParameterCount() == 15 && m.getParameterTypes()[0] == rp) draw = m;
            if (draw == null) throw new NoSuchMethodException("drawProfilePokemon");
        } catch (Throwable t) {
            broken = true;
            com.november.mcphone.MCphone.LOGGER.info("[포키폰] 코블몬 포켓몬 아이콘을 못 씀 (이름 글자로 대신): {}", t.toString());
        }
        return !broken;
    }

    /**
     * 상자(x, y, size) 안에 포켓몬을 그린다.
     * @param species "cobblemon:charizard" 같은 id
     * @return 그렸으면 true
     */
    public static boolean draw(GuiGraphics g, String species, boolean shiny, int x, int y, int size, float partial) {
        if (species == null || species.isEmpty() || !init()) return false;
        var pose = g.pose();
        pose.pushPose();
        try {
            ResourceLocation id = ResourceLocation.tryParse(species.contains(":") ? species : "cobblemon:" + species);
            if (id == null) return false;
            Object sp = getByIdentifier.invoke(null, id);
            if (sp == null) return false;
            Object rp = renderable.newInstance(sp, shiny ? Set.of("shiny") : Set.of(), ItemStack.EMPTY);
            Object state = STATES.computeIfAbsent(species + shiny, k -> {
                try { return floating.newInstance(); } catch (Exception e) { throw new RuntimeException(e); }
            });
            // 코블몬 파티 칸: 26칸 너비에 scale 10, 가운데(x+13), 위로 2
            float k = size / 26f;
            pose.translate(x + size / 2f, y - 2 * k, 0);
            draw.invoke(null, rp, pose, new Quaternionf().rotationXYZ((float) Math.toRadians(13), (float) Math.toRadians(35), 0f), profilePose, state,
                    partial, 10f * k, true, false, 1f, 1f, 1f, 1f, 0f, 0f);
            return true;
        } catch (Throwable t) {
            broken = true;
            com.november.mcphone.MCphone.LOGGER.warn("[포키폰] 포켓몬 아이콘 그리기 실패, 이름 글자로 대신: {}", t.toString());
            return false;
        } finally {
            pose.popPose();
        }
    }
}
