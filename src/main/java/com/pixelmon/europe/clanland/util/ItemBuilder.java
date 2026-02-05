package com.pixelmon.europe.clanland.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemBuilder {
    private final ItemStack stack;

    public ItemBuilder(Material mat, int data) {
        this.stack = new ItemStack(mat, 1, (short) data);
    }

    public ItemBuilder name(String name) {
        ItemMeta im = stack.getItemMeta();
        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        stack.setItemMeta(im);
        return this;
    }

    public ItemBuilder lore(List<String> l) {
        ItemMeta im = stack.getItemMeta();
        List<String> list = new ArrayList<>();
        for (String s : l) list.add(ChatColor.translateAlternateColorCodes('&', s));
        im.setLore(list);
        stack.setItemMeta(im);
        return this;
    }

    public ItemStack build() {
        return stack;
    }
}