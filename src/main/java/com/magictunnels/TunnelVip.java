package com.magictunnels;

import org.bukkit.entity.Player;

/**
 * VIP 接口（**占位**）：设计文档 9.6 —— 本服没有 VIP 插件，所以只留挂载点：
 *   · /magicteleport vipallow <true|false>  是否只有 VIP 能建通道（只读写配置）
 *   · /magicteleport vipamount <等级> <数量> 不同等级可用几条（只读写配置）
 *
 * 以后接 LuckPerms 组（或别家 VIP 插件）时，只需要改 {@link #levelOf(Player)}：
 *   return player.hasPermission("magicteleport.vip.3") ? 3 : player.hasPermission("magicteleport.vip.2") ? 2 : ...;
 * 其它逻辑（上限计算、仅 VIP 可建）不用动。
 */
public class TunnelVip {

    private final MagicTeleport plugin;

    TunnelVip(MagicTeleport plugin) {
        this.plugin = plugin;
    }

    /** 玩家 VIP 等级；0 = 不是 VIP。**当前恒 0（未接入）** */
    public int levelOf(Player p) {
        return 0;
    }

    /**
     * 该玩家实际可拥有的通道数。
     *
     * @return >=0 正常上限；-1 表示禁止创建（启用「仅VIP可建」而玩家不是 VIP）
     */
    public int limitFor(Player p) {
        TunnelStore store = plugin.store();
        int base = store.baseLimit(p.getUniqueId());
        if (!store.vipallow()) {
            return base;
        }
        int lvl = levelOf(p);
        if (lvl <= 0) {
            return -1;
        }
        int v = store.vipAmount(lvl);
        return v > 0 ? v : base;
    }
}
