package com.november.mcphone.core.script;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 脚本里写的物品标识 → ItemStack（施工方案 §5.2 item）。 */
public final class ItemRefs {

    private ItemRefs() {
    }

    /** 标识写错或物品不存在时返回 {@link ItemStack#EMPTY}，调用方按「画不了」兜底。count 夹在 1–99。 */
    public static ItemStack resolve(String id, int count) {
        ResourceLocation location = id == null ? null : ResourceLocation.tryParse(id);
        if (location == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(location);
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, Math.max(1, Math.min(99, count)));
    }
}
