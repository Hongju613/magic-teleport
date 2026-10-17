package com.magictunnels;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 54 格（6 行）管理界面：
 *   · 边框 = 第 1 列 / 第 9 列（灰玻璃）
 *   · 通道列表 = 行 0-4、列 1-7（最多 35 条）
 *   · 最下一行按钮：新增通道 / 删除通道 / 物品设置 / 收费容器 / 购买上限
 *
 * 三个 Holder 认领三种界面（主界面 / 收费物品 / 确认删除），点击一律按 Holder 分派。
 */
public class TunnelGui implements Listener {

    /** 主界面：deleteMode=true 时点通道会弹确认删除 */
    public static class MainHolder implements InventoryHolder {
        public boolean deleteMode;
        private Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }

        public void set(Inventory inv) {
            this.inv = inv;
        }
    }

    /** 收费物品界面：关闭时把内容快照成收费表（界面本身不放实物，避免复制物品） */
    public static class FeeHolder implements InventoryHolder {
        public String channelId;
        private Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }

        public void set(Inventory inv) {
            this.inv = inv;
        }
    }

    /** 确认删除界面 */
    public static class ConfirmHolder implements InventoryHolder {
        public String channelId;
        private Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }

        public void set(Inventory inv) {
            this.inv = inv;
        }
    }

    private final MagicTeleport plugin;

    TunnelGui(MagicTeleport plugin) {
        this.plugin = plugin;
    }

    private Messages m() {
        return plugin.messages();
    }

    private static ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack border() {
        return item(Material.GRAY_STAINED_GLASS_PANE, m().raw("gui.border"), null);
    }

    // ================= 主界面 =================

    public void openMain(Player p, boolean deleteMode) {
        MainHolder holder = new MainHolder();
        holder.deleteMode = deleteMode;
        Inventory inv = Bukkit.createInventory(holder, 54, m().raw("gui.title-main"));
        holder.set(inv);
        renderMain(p, inv, holder);
        p.openInventory(inv);
    }

    private void renderMain(Player p, Inventory inv, MainHolder holder) {
        for (int slot = 0; slot < 54; slot++) {
            inv.setItem(slot, border());
        }
        List<TunnelStore.Channel> own = plugin.store().listByOwner(p.getUniqueId());
        int shown = Math.min(own.size(), 35);
        for (int i = 0; i < shown; i++) {
            int slot = (i / 7) * 9 + (i % 7) + 1;
            inv.setItem(slot, channelItem(own.get(i), holder.deleteMode));
        }
        inv.setItem(46, item(Material.EMERALD, m().raw("gui.button-add-name"), m().list("gui.button-add-lore")));
        inv.setItem(47, item(Material.BARRIER, m().raw("gui.button-delete-name"), m().list("gui.button-delete-lore")));
        inv.setItem(48, item(Material.CHEST, m().raw("gui.button-items-name"), m().list("gui.button-items-lore")));
        inv.setItem(49, item(Material.BARREL, m().raw("gui.button-container-name"), m().list("gui.button-container-lore")));
        inv.setItem(50, item(Material.GOLD_INGOT, m().raw("gui.button-buy-name"), m().list("gui.button-buy-lore")));
    }

    private ItemStack channelItem(TunnelStore.Channel ch, boolean deleteMode) {
        List<String> lore = new ArrayList<String>();
        lore.add(m().format("gui.channel-a", ch.a.world, ch.a.x, ch.a.y, ch.a.z));
        lore.add(m().format("gui.channel-b", ch.b.world, ch.b.x, ch.b.y, ch.b.z));
        lore.add(m().format("gui.channel-allow", m().raw(ch.allowOthers ? "general.word-yes" : "general.word-no")));
        if (ch.feeItems.isEmpty() && ch.feeMoney <= 0) {
            lore.add(m().raw("gui.channel-fee-none"));
        } else {
            if (!ch.feeItems.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Material, Integer> e : ch.feeItems.entrySet()) {
                    if (sb.length() > 0) {
                        sb.append(" ");
                    }
                    sb.append("\u00a7f").append(e.getKey().name().toLowerCase(java.util.Locale.ROOT))
                            .append("\u00d7").append(e.getValue());
                }
                lore.add(m().format("gui.channel-fee-items", sb.toString()));
            }
            if (ch.feeMoney > 0) {
                lore.add(m().format("gui.channel-fee-money", (long) ch.feeMoney));
            }
        }
        lore.add(m().raw(ch.container == null ? "gui.channel-container-no" : "gui.channel-container-yes"));
        lore.add(m().raw("gui.channel-sep"));
        lore.add(m().raw(deleteMode ? "gui.channel-hint-delete" : "gui.channel-hint-toggle"));
        return item(Material.LODESTONE, m().format("gui.channel-name", ch.id, ch.name), lore);
    }

    // ================= 收费物品界面 =================

    public void openFee(Player p, TunnelStore.Channel ch) {
        FeeHolder holder = new FeeHolder();
        holder.channelId = ch.id;
        Inventory inv = Bukkit.createInventory(holder, 27, m().raw("gui.title-fee"));
        holder.set(inv);
        p.openInventory(inv);
        m().send(p, "gui.fee-hint");
    }

    // ================= 确认删除界面 =================

    public void openConfirm(Player p, TunnelStore.Channel ch) {
        ConfirmHolder holder = new ConfirmHolder();
        holder.channelId = ch.id;
        Inventory inv = Bukkit.createInventory(holder, 9, m().raw("gui.title-confirm"));
        holder.set(inv);
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border());
        }
        List<String> lore = new ArrayList<String>();
        lore.add(m().format("gui.confirm-yes-lore", ch.id, ch.name));
        inv.setItem(3, item(Material.RED_WOOL, m().raw("gui.confirm-yes"), lore));
        inv.setItem(5, item(Material.LIME_WOOL, m().raw("gui.confirm-no"), null));
        p.openInventory(inv);
    }

    // ================= 事件 =================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getWhoClicked();
        Inventory inv = e.getInventory();
        InventoryHolder h = inv == null ? null : inv.getHolder();
        if (h instanceof MainHolder) {
            e.setCancelled(true);
            handleMainClick(p, inv, (MainHolder) h, e.getRawSlot());
        } else if (h instanceof ConfirmHolder) {
            e.setCancelled(true);
            handleConfirmClick(p, (ConfirmHolder) h, e.getRawSlot());
        }
        // FeeHolder 故意不取消：玩家就是要往里放物品
    }

    private void handleMainClick(Player p, Inventory inv, MainHolder holder, int slot) {
        if (slot == 46) {
            p.closeInventory();
            plugin.flow().startBind(p);
            return;
        }
        if (slot == 47) {
            holder.deleteMode = true;
            renderMain(p, inv, holder);
            m().send(p, "gui.delete-prompt");
            return;
        }
        if (slot == 48) {
            feeEntry(p);
            return;
        }
        if (slot == 49) {
            containerEntry(p);
            return;
        }
        if (slot == 50) {
            p.closeInventory();
            plugin.buyPrompt(p);
            return;
        }
        int row = slot / 9;
        int col = slot % 9;
        if (row < 0 || row > 4 || col < 1 || col > 7) {
            return;
        }
        List<TunnelStore.Channel> own = plugin.store().listByOwner(p.getUniqueId());
        int idx = row * 7 + (col - 1);
        if (idx >= own.size()) {
            return;
        }
        TunnelStore.Channel ch = own.get(idx);
        if (holder.deleteMode) {
            p.closeInventory();
            openConfirm(p, ch);
            return;
        }
        ch.allowOthers = !ch.allowOthers;
        plugin.store().save();
        renderMain(p, inv, holder);
        m().send(p, "gui.toggle-result", ch.id,
                m().raw(ch.allowOthers ? "state.allow-others" : "state.owner-only"));
    }

    private void feeEntry(Player p) {
        List<TunnelStore.Channel> own = plugin.store().listByOwner(p.getUniqueId());
        if (own.isEmpty()) {
            m().send(p, "channel.none");
            return;
        }
        if (own.size() == 1) {
            p.closeInventory();
            openFee(p, own.get(0));
            return;
        }
        m().send(p, "gui.fee-multi-items");
    }

    private void containerEntry(Player p) {
        List<TunnelStore.Channel> own = plugin.store().listByOwner(p.getUniqueId());
        if (own.isEmpty()) {
            m().send(p, "channel.none");
            return;
        }
        if (own.size() == 1) {
            p.closeInventory();
            plugin.flow().startContainerSel(p, own.get(0).id);
            return;
        }
        m().send(p, "gui.fee-multi-container");
    }

    private void handleConfirmClick(Player p, ConfirmHolder holder, int slot) {
        p.closeInventory();
        if (slot == 3) {
            TunnelStore.Channel ch = plugin.store().byId(holder.channelId);
            if (ch != null) {
                plugin.store().remove(ch);
                m().send(p, "gui.deleted", ch.id);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) {
            return;
        }
        Player p = (Player) e.getPlayer();
        InventoryHolder h = e.getInventory() == null ? null : e.getInventory().getHolder();
        if (h instanceof FeeHolder) {
            TunnelStore.Channel ch = plugin.store().byId(((FeeHolder) h).channelId);
            if (ch != null) {
                plugin.fee().applyFeeItems(p, ch, e.getInventory());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getInventory() == null ? null : e.getInventory().getHolder();
        if (h instanceof MainHolder || h instanceof ConfirmHolder) {
            e.setCancelled(true);
        }
    }
}
