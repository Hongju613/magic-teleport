package com.magictunnels;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MagicTeleport —— magic teleport tunnels for Paper.
 *
 * 主类只做**装配 + 命令分发**，逻辑分别在：
 *   {@link TunnelStore}    通道/配置的唯一数据源，落盘 tunnels.yml
 *   {@link TunnelTemplate} 3×3×6 结构模板的录制与逐格校验
 *   {@link TunnelFlow}     木棍绑定 / 右键读秒传送 / 紫色螺旋粒子 / 离开取消
 *   {@link TunnelFee}      物品+金币收费、收取容器（木棍左键箱子/木桶）
 *   {@link TunnelGui}      54 格管理界面（边框 + 通道列表 + 底部按钮）
 *   {@link TunnelVip}      VIP 接口占位（以后接 LuckPerms 组等）
 *   {@link Messages}       多语言提示（lang/en_US.yml、lang/zh_CN.yml）
 *
 * 硬规矩：核心方块可配置（/magicteleport block），所有判定/校验/落点都跟配置走，不写死。
 */
public class MagicTeleport extends JavaPlugin implements TabExecutor {

    private static MagicTeleport instance;

    private TunnelStore store;
    private TunnelFlow flow;
    private TunnelGui gui;
    private TunnelFee fee;
    private TunnelVip vip;
    private Messages messages;

    public static MagicTeleport get() {
        return instance;
    }

    public TunnelStore store() {
        return store;
    }

    public TunnelFlow flow() {
        return flow;
    }

    public TunnelGui gui() {
        return gui;
    }

    public TunnelFee fee() {
        return fee;
    }

    public TunnelVip vip() {
        return vip;
    }

    public Messages messages() {
        return messages;
    }

    /** 颜色码转换（& → §）。本插件统一用它 */
    public static String c(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    /** 方块名的小写展示名（回显用） */
    public static String nice(Material m) {
        return m == null ? "?" : m.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 给"主城方块交互锁"这类插件用的白名单桥：
     * 这个方块是不是当前配置的核心方块。没启用本插件时恒 false。
     */
    public static boolean isTunnelCore(Block b) {
        try {
            MagicTeleport me = instance;
            if (me == null || b == null || me.store == null) {
                return false;
            }
            Material core = me.store.core();
            return core != null && b.getType() == core;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        messages = new Messages(this);
        messages.load();
        store = new TunnelStore(this);
        store.load();
        fee = new TunnelFee(this);
        vip = new TunnelVip(this);
        flow = new TunnelFlow(this);
        gui = new TunnelGui(this);
        getServer().getPluginManager().registerEvents(flow, this);
        getServer().getPluginManager().registerEvents(gui, this);
        getCommand("magicteleport").setExecutor(this);
        getCommand("magicteleport").setTabCompleter(this);
        getLogger().info("Enabled: " + store.channelCount() + " tunnel(s) | limit " + store.amount()
                + " | countdown " + store.time() + "s | fee " + (store.charge() ? "on" : "off")
                + " | core " + nice(store.core()) + " | template " + (store.hasTemplate() ? "recorded" : "missing")
                + " | lang " + messages.lang());
    }

    @Override
    public void onDisable() {
        if (flow != null) {
            flow.cancelAll(messages == null ? null : messages.raw("general.plugin-disabled"));
        }
        if (store != null) {
            store.save();
        }
    }

    // ================= 命令 =================

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        if (sub.equals("")) {
            return cmdGui(s);
        }
        if (sub.equals("add")) {
            return cmdAdd(s);
        }
        if (sub.equals("delete")) {
            return cmdDelete(s);
        }
        if (sub.equals("items")) {
            return cmdItems(s, args);
        }
        if (sub.equals("container")) {
            return cmdContainer(s, args);
        }
        if (sub.equals("buy")) {
            return cmdBuy(s, args);
        }
        if (sub.equals("info")) {
            return cmdInfo(s);
        }
        if (sub.equals("setallow")) {
            return cmdSetAllow(s, args);
        }
        if (sub.equals("amount")) {
            return cmdAmount(s, args);
        }
        if (sub.equals("time")) {
            return cmdTime(s, args);
        }
        if (sub.equals("sell")) {
            return cmdSell(s, args);
        }
        if (sub.equals("price")) {
            return cmdPrice(s, args);
        }
        if (sub.equals("charge")) {
            return cmdCharge(s, args);
        }
        if (sub.equals("vipallow")) {
            return cmdVipAllow(s, args);
        }
        if (sub.equals("vipamount")) {
            return cmdVipAmount(s, args);
        }
        if (sub.equals("createallow")) {
            return cmdCreateAllow(s, args);
        }
        if (sub.equals("block")) {
            return cmdBlock(s, args);
        }
        if (sub.equals("template")) {
            return cmdTemplate(s, args);
        }
        if (sub.equals("admin")) {
            return cmdAdmin(s, args);
        }
        if (sub.equals("reload")) {
            return cmdReload(s);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            List<String> subs = Arrays.asList("add", "delete", "items", "container", "buy", "info",
                    "setallow", "amount", "time", "sell", "price", "charge", "vipallow", "vipamount",
                    "createallow", "block", "template", "admin", "reload");
            for (String x : subs) {
                if (x.startsWith(args[0].toLowerCase())) {
                    out.add(x);
                }
            }
            return out;
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("template")) {
                return Arrays.asList("set");
            }
            if (args[0].equalsIgnoreCase("admin")) {
                return Arrays.asList("create", "validate", "container");
            }
            if (args[0].equalsIgnoreCase("buy")) {
                return Arrays.asList("confirm");
            }
            if (args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("charge")
                    || args[0].equalsIgnoreCase("vipallow") || args[0].equalsIgnoreCase("createallow")) {
                return Arrays.asList("true", "false");
            }
            if (args[0].equalsIgnoreCase("items") || args[0].equalsIgnoreCase("container")
                    || args[0].equalsIgnoreCase("setallow")) {
                if (s instanceof Player) {
                    for (TunnelStore.Channel ch : store.listByOwner(((Player) s).getUniqueId())) {
                        out.add(ch.id);
                    }
                    return out;
                }
            }
        }
        return out;
    }

    // ---- 小工具 ----

    private Player needPlayer(CommandSender s) {
        if (s instanceof Player) {
            return (Player) s;
        }
        messages.send(s, "general.player-only");
        return null;
    }

    private boolean isAdmin(CommandSender s) {
        return s.hasPermission("magicteleport.admin");
    }

    private boolean adminOr(CommandSender s) {
        if (!isAdmin(s)) {
            messages.send(s, "general.no-permission");
            return false;
        }
        return true;
    }

    /** 解析布尔；非法返回 null（并已提示） */
    private Boolean parseBool(CommandSender s, String v) {
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("on") || v.equals("1")) {
            return Boolean.TRUE;
        }
        if (v.equalsIgnoreCase("false") || v.equalsIgnoreCase("no") || v.equalsIgnoreCase("off") || v.equals("0")) {
            return Boolean.FALSE;
        }
        messages.send(s, "general.not-a-bool");
        return null;
    }

    /** 解析整数；非法返回 Integer.MIN_VALUE（并已提示） */
    private int parseInt(CommandSender s, String v, String what) {
        try {
            return Integer.parseInt(v);
        } catch (Throwable t) {
            messages.send(s, "general.not-a-number", what, v);
            return Integer.MIN_VALUE;
        }
    }

    private String onOff(boolean on) {
        return messages.raw(on ? "general.toggle-on" : "general.toggle-off");
    }

    private String enabledDisabled(boolean on) {
        return messages.raw(on ? "general.enabled" : "general.disabled");
    }

    private void help(CommandSender s) {
        s.sendMessage(messages.raw("help.header"));
        for (String line : messages.list("help.player")) {
            s.sendMessage(line);
        }
        if (isAdmin(s)) {
            for (String line : messages.list("help.admin")) {
                s.sendMessage(line);
            }
        }
    }

    // ---- 子命令 ----

    private boolean cmdGui(CommandSender s) {
        Player p = needPlayer(s);
        if (p == null) {
            help(s);
            return true;
        }
        gui.openMain(p, false);
        return true;
    }

    private boolean cmdAdd(CommandSender s) {
        Player p = needPlayer(s);
        if (p == null) {
            return true;
        }
        flow.startBind(p);
        return true;
    }

    private boolean cmdDelete(CommandSender s) {
        Player p = needPlayer(s);
        if (p == null) {
            return true;
        }
        p.sendMessage(messages.raw("gui.delete-prompt"));
        gui.openMain(p, true);
        return true;
    }

    /** 挑出玩家要操作的通道：可带编号；只有一个时不用带 */
    private TunnelStore.Channel pickChannel(Player p, String[] args, int idx) {
        List<TunnelStore.Channel> own = store.listByOwner(p.getUniqueId());
        if (args.length > idx) {
            TunnelStore.Channel ch = store.byId(args[idx]);
            if (ch == null || (!ch.owner.equals(p.getUniqueId()) && !isAdmin(p))) {
                messages.send(p, "channel.not-found", args[idx]);
                return null;
            }
            return ch;
        }
        if (own.isEmpty()) {
            messages.send(p, "channel.none");
            return null;
        }
        if (own.size() == 1) {
            return own.get(0);
        }
        StringBuilder sb = new StringBuilder();
        for (TunnelStore.Channel ch : own) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("#").append(ch.id);
        }
        messages.send(p, "channel.multi-hint", sb.toString());
        return null;
    }

    private boolean cmdItems(CommandSender s, String[] args) {
        Player p = needPlayer(s);
        if (p == null) {
            return true;
        }
        TunnelStore.Channel ch = pickChannel(p, args, 1);
        if (ch == null) {
            return true;
        }
        gui.openFee(p, ch);
        return true;
    }

    private boolean cmdContainer(CommandSender s, String[] args) {
        Player p = needPlayer(s);
        if (p == null) {
            return true;
        }
        TunnelStore.Channel ch = pickChannel(p, args, 1);
        if (ch == null) {
            return true;
        }
        flow.startContainerSel(p, ch.id);
        return true;
    }

    /** 购买上限的提示（界面按钮与 /magicteleport buy 共用） */
    public void buyPrompt(Player p) {
        if (!store.sell()) {
            messages.send(p, "buy.disabled");
            return;
        }
        if (!fee.hasEconomy()) {
            messages.send(p, "buy.no-economy");
            return;
        }
        messages.send(p, "buy.prompt", store.price());
    }

    private boolean cmdBuy(CommandSender s, String[] args) {
        Player p = needPlayer(s);
        if (p == null) {
            return true;
        }
        if (!store.sell()) {
            messages.send(p, "buy.disabled");
            return true;
        }
        Economy eco = fee.economy();
        if (eco == null) {
            messages.send(p, "buy.no-economy");
            return true;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            buyPrompt(p);
            return true;
        }
        int price = store.price();
        if (!eco.has(p, price)) {
            messages.send(p, "buy.not-enough", price);
            return true;
        }
        eco.withdrawPlayer(p, price);
        store.addBought(p.getUniqueId());
        messages.send(p, "buy.success", store.baseLimit(p.getUniqueId()));
        return true;
    }

    private boolean cmdInfo(CommandSender s) {
        Player p = needPlayer(s);
        if (p == null) {
            return true;
        }
        List<TunnelStore.Channel> own = store.listByOwner(p.getUniqueId());
        if (own.isEmpty()) {
            messages.send(p, "channel.none-info");
            return true;
        }
        messages.send(p, "channel.info-header", own.size(), Math.max(0, vip.limitFor(p)));
        for (TunnelStore.Channel ch : own) {
            String state = messages.raw(ch.allowOthers ? "state.allow-others" : "state.owner-only");
            messages.send(p, "channel.info-line", ch.id, ch.name,
                    ch.a.world + " " + ch.a.x + "," + ch.a.y + "," + ch.a.z,
                    ch.b.world + " " + ch.b.x + "," + ch.b.y + "," + ch.b.z, state);
        }
        return true;
    }

    private boolean cmdSetAllow(CommandSender s, String[] args) {
        Player p = needPlayer(s);
        if (p == null) {
            return true;
        }
        if (args.length < 3) {
            messages.send(p, "setallow.usage");
            return true;
        }
        TunnelStore.Channel ch = store.byId(args[1]);
        if (ch == null || (!ch.owner.equals(p.getUniqueId()) && !isAdmin(p))) {
            messages.send(p, "channel.not-found", args[1]);
            return true;
        }
        Boolean b = parseBool(s, args[2]);
        if (b == null) {
            return true;
        }
        ch.allowOthers = b;
        store.save();
        messages.send(p, "setallow.result", ch.id,
                messages.raw(b ? "state.allow-others" : "state.owner-only"));
        return true;
    }

    private boolean cmdAmount(CommandSender s, String[] args) {
        if (!adminOr(s)) {
            return true;
        }
        if (args.length < 2) {
            messages.send(s, "amount.usage", store.amount());
            return true;
        }
        int n = parseInt(s, args[1], messages.raw("general.field-amount"));
        if (n == Integer.MIN_VALUE) {
            return true;
        }
        if (n < 0) {
            messages.send(s, "amount.negative");
            return true;
        }
        store.setAmount(n);
        store.save();
        messages.send(s, "amount.result", n);
        return true;
    }

    private boolean cmdTime(CommandSender s, String[] args) {
        if (!adminOr(s)) {
            return true;
        }
        if (args.length < 2) {
            messages.send(s, "time.usage", store.time());
            return true;
        }
        int n = parseInt(s, args[1], messages.raw("general.field-seconds"));
        if (n == Integer.MIN_VALUE) {
            return true;
        }
        if (n < 1) {
            messages.send(s, "time.too-small");
            return true;
        }
        store.setTime(n);
        store.save();
        messages.send(s, "time.result", n);
        return true;
    }

    private boolean cmdSell(CommandSender s, String[] args) {
        if (!adminOr(s)) {
            return true;
        }
        if (args.length < 2) {
            messages.send(s, "sell.usage", store.sell());
            return true;
        }
        Boolean b = parseBool(s, args[1]);
        if (b == null) {
            return true;
        }
        store.setSell(b);
        store.save();
        messages.send(s, "sell.result", enabledDisabled(b));
        return true;
    }

    private boolean cmdPrice(CommandSender s, String[] args) {
        if (!adminOr(s)) {
            return true;
        }
        if (args.length < 2) {
            messages.send(s, "price.usage", store.price());
            return true;
        }
        int n = parseInt(s, args[1], messages.raw("general.field-price"));
        if (n == Integer.MIN_VALUE) {
            return true;
        }
        if (n < 0) {
            messages.send(s, "price.negative");
            return true;
        }
        store.setPrice(n);
        store.save();
        messages.send(s, "price.result", n);
        return true;
    }

    private boolean cmdCharge(CommandSender s, String[] args) {
        if (!adminOr(s)) {
            return true;
        }
        if (args.length < 2) {
            messages.send(s, "charge.usage", store.charge());
            return true;
        }
        Boolean b = parseBool(s, args[1]);
        if (b == null) {
            return true;
        }
        store.setCharge(b);
        store.save();
        messages.send(s, "charge.result", enabledDisabled(b));
        return true;
    }

    private boolean cmdVipAllow(CommandSender s, String[] args) {
        if (!adminOr(s)) {
            return true;
        }
        if (args.length < 2) {
            messages.send(s, "vipallow.usage", store.vipallow());
            return true;
        }
        Boolean b = parseBool(s, args[1]);
        if (b == null) {
            return true;
        }
        store.setVipallow(b);
        store.save();
        messages.send(s, "vipallow.result", enabledDisabled(b));
        messages.send(s, "vipallow.note");
        return true;
    }

    private boolean cmdVipAmount(CommandSender s, String[] args) {
        if (!adminOr(s)) {
            return true;
        }
        if (args.length < 3) {
            messages.send(s, "vipamount.usage");
            return true;
        }
        int lvl = parseInt(s, args[1], messages.raw("general.field-level"));
        if (lvl == Integer.MIN_VALUE) {
            return true;
        }
        int n = parseInt(s, args[2], messages.raw("general.field-amount"));
        if (n == Integer.MIN_VALUE) {
            return true;
        }
        store.setVipAmount(lvl, n);
        store.save();
        messages.send(s, "vipamount.result", lvl, n);
        return true;
    }

    private boolean cmdCreateAllow(CommandSender s, String[] args) {
        if (!adminOr(s)) {
            return true;
        }
        if (args.length < 2) {
            messages.send(s, "createallow.usage");
            return true;
        }
        Boolean b = parseBool(s, args[1]);
        if (b == null) {
            return true;
        }
        String world;
        if (args.length >= 3) {
            world = args[2];
        } else {
            Player p = needPlayer(s);
            if (p == null) {
                messages.send(s, "createallow.console-need-world");
                return true;
            }
            world = p.getWorld().getName();
        }
        store.setWorldAllow(world, b);
        store.save();
        messages.send(s, "createallow.result", world, messages.raw(b ? "createallow.allowed" : "createallow.denied"));
        return true;
    }

    private boolean cmdBlock(CommandSender s, String[] args) {
        if (!adminOr(s)) {
            return true;
        }
        if (args.length < 2) {
            messages.send(s, "block.usage", nice(store.core()));
            return true;
        }
        String raw = args[1].trim().toUpperCase(java.util.Locale.ROOT);
        if (raw.startsWith("MINECRAFT:")) {
            raw = raw.substring(10);
        }
        Material m = Material.getMaterial(raw);
        if (m == null || !m.isBlock() || m == Material.AIR) {
            messages.send(s, "block.unknown", args[1]);
            return true;
        }
        store.setCore(m);
        store.save();
        messages.send(s, "block.result", nice(m));
        messages.send(s, "block.note");
        return true;
    }

    private boolean cmdTemplate(CommandSender s, String[] args) {
        if (!adminOr(s)) {
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("set")) {
            if (args.length >= 6) {
                World w = Bukkit.getWorld(args[2]);
                if (w == null) {
                    messages.send(s, "general.world-not-found", args[2]);
                    return true;
                }
                int x = parseInt(s, args[3], messages.raw("general.field-x"));
                if (x == Integer.MIN_VALUE) {
                    return true;
                }
                int y = parseInt(s, args[4], messages.raw("general.field-y"));
                if (y == Integer.MIN_VALUE) {
                    return true;
                }
                int z = parseInt(s, args[5], messages.raw("general.field-z"));
                if (z == Integer.MIN_VALUE) {
                    return true;
                }
                recordTemplate(s, w, x, y, z);
                return true;
            }
            Player p = needPlayer(s);
            if (p == null) {
                messages.send(s, "template.console-need-world");
                return true;
            }
            Location l = p.getLocation();
            recordTemplate(s, p.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
            return true;
        }
        messages.send(s, "template.usage-set-player");
        messages.send(s, "template.usage-set-console");
        messages.send(s, "template.current", TunnelTemplate.describe(messages, store.template()));
        return true;
    }

    private void recordTemplate(CommandSender s, World w, int x, int y, int z) {
        Block anchor = w.getBlockAt(x, y, z);
        if (anchor.getType() != store.core()) {
            messages.send(s, "template.anchor-not-core", nice(store.core()), nice(anchor.getType()));
            return;
        }
        List<String> t = TunnelTemplate.record(w, x, y, z);
        store.setTemplate(t);
        store.save();
        messages.send(s, "template.recorded", TunnelTemplate.describe(messages, t));
    }

    private boolean cmdAdmin(CommandSender s, String[] args) {
        if (!adminOr(s)) {
            return true;
        }
        if (args.length < 2) {
            messages.send(s, "admin.usage-create");
            messages.send(s, "admin.usage-validate");
            messages.send(s, "admin.usage-container");
            return true;
        }
        if (args[1].equalsIgnoreCase("create")) {
            if (args.length < 10) {
                messages.send(s, "admin.args-missing-create");
                return true;
            }
            OfflinePlayer owner = Bukkit.getOfflinePlayer(args[2]);
            World w = Bukkit.getWorld(args[3]);
            if (w == null) {
                messages.send(s, "general.world-not-found", args[3]);
                return true;
            }
            int[] v = new int[6];
            for (int i = 0; i < 6; i++) {
                v[i] = parseInt(s, args[4 + i], messages.raw("general.field-coord"));
                if (v[i] == Integer.MIN_VALUE) {
                    return true;
                }
            }
            if (!store.hasTemplate()) {
                messages.send(s, "admin.no-template");
                return true;
            }
            List<String> b1 = TunnelTemplate.check(store.template(), w, v[0], v[1], v[2], store.core());
            List<String> b2 = TunnelTemplate.check(store.template(), w, v[3], v[4], v[5], store.core());
            boolean bad = false;
            if (b1 != null && !b1.isEmpty()) {
                bad = true;
                messages.send(s, "admin.invalid-structure", 1);
                for (String x : b1) {
                    messages.send(s, "admin.detail-line", x);
                }
            }
            if (b2 != null && !b2.isEmpty()) {
                bad = true;
                messages.send(s, "admin.invalid-structure", 2);
                for (String x : b2) {
                    messages.send(s, "admin.detail-line", x);
                }
            }
            if (bad) {
                return true;
            }
            if (v[0] == v[3] && v[1] == v[4] && v[2] == v[5]) {
                messages.send(s, "admin.same-structure");
                return true;
            }
            Object[] u1 = store.channelAt(w.getName(), v[0], v[1], v[2]);
            Object[] u2 = store.channelAt(w.getName(), v[3], v[4], v[5]);
            if (u1 != null || u2 != null) {
                messages.send(s, "admin.occupied");
                return true;
            }
            TunnelStore.Channel ch = store.create(owner.getUniqueId(),
                    messages.format("bind.default-name", owner.getName()),
                    new TunnelStore.Endpoint(w.getName(), v[0], v[1], v[2]),
                    new TunnelStore.Endpoint(w.getName(), v[3], v[4], v[5]));
            messages.send(s, "admin.created", ch.id, owner.getName(), ch.allowOthers);
            return true;
        }
        if (args[1].equalsIgnoreCase("validate")) {
            if (args.length < 6) {
                messages.send(s, "admin.args-missing-validate");
                return true;
            }
            World w = Bukkit.getWorld(args[2]);
            if (w == null) {
                messages.send(s, "general.world-not-found", args[2]);
                return true;
            }
            int x = parseInt(s, args[3], messages.raw("general.field-x"));
            if (x == Integer.MIN_VALUE) {
                return true;
            }
            int y = parseInt(s, args[4], messages.raw("general.field-y"));
            if (y == Integer.MIN_VALUE) {
                return true;
            }
            int z = parseInt(s, args[5], messages.raw("general.field-z"));
            if (z == Integer.MIN_VALUE) {
                return true;
            }
            List<String> bad = TunnelTemplate.check(store.template(), w, x, y, z, store.core());
            if (bad.isEmpty()) {
                messages.send(s, "admin.valid");
            } else {
                messages.send(s, "admin.invalid-count", bad.size());
                for (String one : bad) {
                    messages.send(s, "admin.detail-line", one);
                }
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("container")) {
            if (args.length < 7) {
                messages.send(s, "admin.args-missing-container");
                return true;
            }
            TunnelStore.Channel ch = store.byId(args[2]);
            if (ch == null) {
                messages.send(s, "admin.channel-not-found", args[2]);
                return true;
            }
            World w = Bukkit.getWorld(args[3]);
            if (w == null) {
                messages.send(s, "general.world-not-found", args[3]);
                return true;
            }
            int x = parseInt(s, args[4], messages.raw("general.field-x"));
            if (x == Integer.MIN_VALUE) {
                return true;
            }
            int y = parseInt(s, args[5], messages.raw("general.field-y"));
            if (y == Integer.MIN_VALUE) {
                return true;
            }
            int z = parseInt(s, args[6], messages.raw("general.field-z"));
            if (z == Integer.MIN_VALUE) {
                return true;
            }
            Block b = w.getBlockAt(x, y, z);
            Material t = b.getType();
            if (t != Material.CHEST && t != Material.BARREL && t != Material.TRAPPED_CHEST) {
                messages.send(s, "admin.not-container", nice(t));
                return true;
            }
            TunnelStore.ContainerRef ref = new TunnelStore.ContainerRef();
            ref.world = w.getName();
            ref.x = x;
            ref.y = y;
            ref.z = z;
            ch.container = ref;
            store.save();
            messages.send(s, "admin.container-set", ch.id, w.getName(), x, y, z);
            return true;
        }
        messages.send(s, "general.unknown-subcommand", args[1]);
        return true;
    }

    private boolean cmdReload(CommandSender s) {
        if (!adminOr(s)) {
            return true;
        }
        messages.load();
        store.load();
        flow.cancelAll(messages.raw("general.reloading"));
        messages.send(s, "reload.done", store.channelCount(), store.amount(), store.time(),
                onOff(store.charge()), nice(store.core()),
                store.hasTemplate() ? messages.raw("template.recorded-state") : messages.raw("template.none"));
        return true;
    }
}
