package com.magictunnels;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 收费：物品（每种最多 1 组、可多种）+ 金币（默认 0）。
 *   · 主人自己传永远免费（由 TunnelFlow 判断）
 *   · 收来的**物品**直接进主人用木棍左键选定的那个容器（箱子/木桶），桶满就掉在容器脚下
 *   · 金币通过 Vault（EssentialsX 提供）扣，转给主人
 */
public class TunnelFee {

    private final MagicTeleport plugin;
    private Economy economy;
    private boolean economyLookedUp;

    TunnelFee(MagicTeleport plugin) {
        this.plugin = plugin;
    }

    public Economy economy() {
        if (!economyLookedUp) {
            economyLookedUp = true;
            try {
                RegisteredServiceProvider<Economy> rsp =
                        Bukkit.getServicesManager().getRegistration(Economy.class);
                economy = rsp == null ? null : rsp.getProvider();
            } catch (Throwable t) {
                economy = null;
            }
        }
        return economy;
    }

    public boolean hasEconomy() {
        return economy() != null;
    }

    /** 收费物品界面关掉时调用：把界面内容快照成收费表（每种封顶 64） */
    public void applyFeeItems(Player p, TunnelStore.Channel ch, Inventory inv) {
        Map<Material, Integer> map = new LinkedHashMap<Material, Integer>();
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType() == Material.AIR) {
                continue;
            }
            int n = map.containsKey(it.getType()) ? map.get(it.getType()) : 0;
            n += it.getAmount();
            if (n > 64) {
                n = 64;
            }
            map.put(it.getType(), n);
        }
        ch.feeItems = map;
        plugin.store().save();
        Messages m = plugin.messages();
        if (map.isEmpty()) {
            m.send(p, "fee.cleared");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Material, Integer> en : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("\u00a7f").append(en.getKey().name().toLowerCase(java.util.Locale.ROOT))
                    .append("\u00d7").append(en.getValue());
        }
        m.send(p, "fee.saved", sb.toString());
    }

    /** 能否付得起（不改动任何东西） */
    public boolean canPay(Player p, TunnelStore.Channel ch) {
        for (Map.Entry<Material, Integer> en : ch.feeItems.entrySet()) {
            if (count(p, en.getKey()) < en.getValue()) {
                return false;
            }
        }
        if (ch.feeMoney > 0) {
            Economy eco = economy();
            if (eco == null || !eco.has(p, ch.feeMoney)) {
                return false;
            }
        }
        return true;
    }

    /** 实际收费：扣材料/金币 → 物品入容器、金币给主人 */
    public boolean tryPay(Player p, TunnelStore.Channel ch) {
        if (!canPay(p, ch)) {
            return false;
        }
        for (Map.Entry<Material, Integer> en : ch.feeItems.entrySet()) {
            p.getInventory().removeItem(new ItemStack(en.getKey(), en.getValue()));
        }
        Economy eco = economy();
        if (ch.feeMoney > 0 && eco != null) {
            eco.withdrawPlayer(p, ch.feeMoney);
            try {
                eco.depositPlayer(Bukkit.getOfflinePlayer(ch.owner), ch.feeMoney);
            } catch (Throwable ignored) {
            }
        }
        Inventory inv = containerInventory(ch);
        for (Map.Entry<Material, Integer> en : ch.feeItems.entrySet()) {
            ItemStack item = new ItemStack(en.getKey(), en.getValue());
            ItemStack rest = null;
            if (inv != null) {
                Map<Integer, ItemStack> left = inv.addItem(item);
                if (!left.isEmpty()) {
                    rest = left.values().iterator().next();
                }
            } else {
                rest = item;
            }
            if (rest != null) {
                dropAtContainer(p, ch, rest);
            }
        }
        p.sendMessage(plugin.messages().raw("fee.paid"));
        return true;
    }

    private void dropAtContainer(Player p, TunnelStore.Channel ch, ItemStack item) {
        try {
            World w = ch.container == null ? null : Bukkit.getWorld(ch.container.world);
            if (w != null) {
                Location at = new Location(w, ch.container.x + 0.5, ch.container.y + 1, ch.container.z + 0.5);
                w.dropItemNaturally(at, item);
            } else {
                p.getWorld().dropItemNaturally(p.getLocation(), item);
            }
        } catch (Throwable ignored) {
        }
    }

    private int count(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == m) {
                n += it.getAmount();
            }
        }
        return n;
    }

    /** 通道的收取容器（容器没了/不是容器 → null） */
    public Inventory containerInventory(TunnelStore.Channel ch) {
        if (ch.container == null) {
            return null;
        }
        World w = Bukkit.getWorld(ch.container.world);
        if (w == null) {
            return null;
        }
        try {
            Block b = w.getBlockAt(ch.container.x, ch.container.y, ch.container.z);
            if (b.getState() instanceof InventoryHolder) {
                return ((InventoryHolder) b.getState()).getInventory();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
