package com.magictunnels;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 传送流程（插件的核心手感）：
 *
 *   · 木棍右键核心方块 = 绑定/配对（/magicteleport add 进入绑定模式，两次点击成一条通道）
 *   · 空手/别的物品右键核心方块 = 开始读秒传送（必须站在那块核心方块上）
 *   · 读秒期间每 tick 检查位置：**离开那块方块 / 换世界 → 立即取消**
 *   · 粒子 = 紫色螺旋绕玩家（PORTAL + WITCH，双螺旋上升）
 *   · 读满之后先查权限（主人 / 允许他人 / 管理员），再查收费，然后才传送
 *
 * 所有阈值（读秒时长、核心方块、上限）都从 TunnelStore 取，不写死。
 */
public class TunnelFlow implements Listener {

    private static final long BIND_TTL = 60000L;          // 绑定模式下两次点击的时限
    private static final long CONTAINER_TTL = 60000L;     // 容器选择的时限

    private final MagicTeleport plugin;

    private final Set<UUID> bindMode = new HashSet<UUID>();
    private final Map<UUID, TunnelStore.Endpoint> bindFirst = new HashMap<UUID, TunnelStore.Endpoint>();
    private final Map<UUID, Long> bindSince = new HashMap<UUID, Long>();
    private final Map<UUID, String> containerSel = new HashMap<UUID, String>();
    private final Map<UUID, Long> containerSince = new HashMap<UUID, Long>();
    private final Map<UUID, Run> runs = new HashMap<UUID, Run>();

    /** 一次进行中的读秒 */
    private static class Run {
        Player p;
        TunnelStore.Channel ch;
        TunnelStore.Endpoint dest;
        int cx, cy, cz;
        String world;
        int tick;
        int total;
        BukkitTask task;
    }

    TunnelFlow(MagicTeleport plugin) {
        this.plugin = plugin;
    }

    private Messages m() {
        return plugin.messages();
    }

    // ================= 入口 =================

    /** /magicteleport add 或界面「新增通道」：切换绑定模式 */
    public void startBind(Player p) {
        UUID u = p.getUniqueId();
        if (bindMode.remove(u)) {
            bindFirst.remove(u);
            bindSince.remove(u);
            m().send(p, "bind.mode-off");
            return;
        }
        bindMode.add(u);
        bindFirst.remove(u);
        bindSince.remove(u);
        m().send(p, "bind.mode-on");
        m().send(p, "bind.on-core-info", MagicTeleport.nice(plugin.store().core()));
    }

    /** /magicteleport container：进入容器选择模式 */
    public void startContainerSel(Player p, String channelId) {
        containerSel.put(p.getUniqueId(), channelId);
        containerSince.put(p.getUniqueId(), System.currentTimeMillis());
        m().send(p, "container.select-hint");
    }

    /** 全部取消（重载 / 卸载 / 退出） */
    public void cancelAll(String reason) {
        for (Run r : new ArrayList<Run>(runs.values())) {
            stop(r, reason == null ? null : m().format("tp.cancel-all", reason));
        }
        bindMode.clear();
        bindFirst.clear();
        bindSince.clear();
        containerSel.clear();
        containerSince.clear();
    }

    // ================= 事件 =================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        if (b == null) {
            return;
        }

        // —— 左键：收费容器选择（木棍左键箱子/木桶）——
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            handleContainerSelect(p, e, b);
            return;
        }

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // 只认主手：空手/手持物品时主副手会各抛一次事件，不挡住会重复提示
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        boolean stick = p.getInventory().getItemInMainHand().getType() == Material.STICK;
        Material core = plugin.store().core();
        boolean isCore = b.getType() == core;

        if (stick) {
            if (!bindMode.contains(p.getUniqueId())) {
                if (isCore) {
                    e.setCancelled(true);
                    m().send(p, "bind.hint");
                }
                return;
            }
            e.setCancelled(true);
            handleBindClick(p, b);
            return;
        }
        if (isCore) {
            e.setCancelled(true);
            handleTeleportClick(p, b);
        }
    }

    private void handleContainerSelect(Player p, PlayerInteractEvent e, Block b) {
        String cid = containerSel.get(p.getUniqueId());
        if (cid == null) {
            return;
        }
        Long since = containerSince.get(p.getUniqueId());
        if (since != null && System.currentTimeMillis() - since > CONTAINER_TTL) {
            containerSel.remove(p.getUniqueId());
            containerSince.remove(p.getUniqueId());
            m().send(p, "container.timeout");
            return;
        }
        if (e.getHand() != EquipmentSlot.HAND
                || p.getInventory().getItemInMainHand().getType() != Material.STICK) {
            return;
        }
        Material t = b.getType();
        if (t != Material.CHEST && t != Material.BARREL && t != Material.TRAPPED_CHEST) {
            m().send(p, "container.only");
            return;
        }
        e.setCancelled(true);
        containerSel.remove(p.getUniqueId());
        containerSince.remove(p.getUniqueId());
        TunnelStore.Channel ch = plugin.store().byId(cid);
        if (ch == null) {
            m().send(p, "container.gone");
            return;
        }
        TunnelStore.ContainerRef ref = new TunnelStore.ContainerRef();
        ref.world = b.getWorld().getName();
        ref.x = b.getX();
        ref.y = b.getY();
        ref.z = b.getZ();
        ch.container = ref;
        plugin.store().save();
        m().send(p, "container.set", ch.id, ref.world, ref.x, ref.y, ref.z);
    }

    // ================= 绑定 =================

    private void handleBindClick(Player p, Block b) {
        Material core = plugin.store().core();
        if (b.getType() != core) {
            m().send(p, "bind.not-core", MagicTeleport.nice(core));
            return;
        }
        String world = b.getWorld().getName();
        if (!p.hasPermission("magicteleport.admin") && !plugin.store().worldAllow(world)) {
            m().send(p, "bind.wrong-world");
            return;
        }
        List<String> bad = TunnelTemplate.check(plugin.store().template(),
                b.getWorld(), b.getX(), b.getY(), b.getZ(), core);
        if (bad != null && !bad.isEmpty()) {
            m().send(p, "bind.invalid-structure");
            if (p.hasPermission("magicteleport.admin")) {
                for (String s : bad) {
                    m().send(p, "admin.detail-line", s);
                }
            }
            return;
        }
        Object[] used = plugin.store().channelAt(world, b.getX(), b.getY(), b.getZ());
        if (used != null) {
            m().send(p, "bind.already-used", ((TunnelStore.Channel) used[0]).id);
            return;
        }
        Long since = bindSince.get(p.getUniqueId());
        if (since != null && System.currentTimeMillis() - since > BIND_TTL) {
            bindFirst.remove(p.getUniqueId());
            m().send(p, "bind.timeout-reset");
        }
        TunnelStore.Endpoint ep = new TunnelStore.Endpoint(world, b.getX(), b.getY(), b.getZ());
        TunnelStore.Endpoint first = bindFirst.get(p.getUniqueId());
        if (first == null) {
            bindFirst.put(p.getUniqueId(), ep);
            bindSince.put(p.getUniqueId(), System.currentTimeMillis());
            m().send(p, "bind.first-selected", world, b.getX(), b.getY(), b.getZ());
            return;
        }
        if (first.same(ep)) {
            m().send(p, "bind.same-structure");
            return;
        }
        int limit = plugin.vip().limitFor(p);
        if (limit < 0) {
            m().send(p, "bind.vip-only");
            clearBind(p);
            return;
        }
        int owned = plugin.store().listByOwner(p.getUniqueId()).size();
        if (owned >= limit) {
            m().send(p, "bind.limit-reached", owned, limit);
            clearBind(p);
            return;
        }
        TunnelStore.Channel ch = plugin.store().create(p.getUniqueId(),
                m().format("bind.default-name", p.getName()), first, ep);
        clearBind(p);
        m().send(p, "bind.created", ch.id);
        try {
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
        } catch (Throwable ignored) {
        }
    }

    private void clearBind(Player p) {
        bindMode.remove(p.getUniqueId());
        bindFirst.remove(p.getUniqueId());
        bindSince.remove(p.getUniqueId());
    }

    // ================= 传送 =================

    private void handleTeleportClick(Player p, Block b) {
        String world = b.getWorld().getName();
        Object[] hit = plugin.store().channelAt(world, b.getX(), b.getY(), b.getZ());
        if (hit == null) {
            m().send(p, "tp.no-channel");
            return;
        }
        if (runs.containsKey(p.getUniqueId())) {
            m().send(p, "tp.busy");
            return;
        }
        if (!standingOn(p, b)) {
            m().send(p, "tp.need-stand");
            return;
        }
        TunnelStore.Channel ch = (TunnelStore.Channel) hit[0];
        boolean fromA = (Boolean) hit[1];
        TunnelStore.Endpoint dest = fromA ? ch.b : ch.a;
        World dw = Bukkit.getWorld(dest.world);
        if (dw == null) {
            m().send(p, "tp.dest-world-missing");
            return;
        }
        if (dw.getBlockAt(dest.x, dest.y, dest.z).getType() != plugin.store().core()) {
            m().send(p, "tp.dest-broken");
            return;
        }
        startRun(p, ch, dest, b);
    }

    private boolean standingOn(Player p, Block b) {
        Location loc = p.getLocation();
        return loc.getBlockX() == b.getX() && loc.getBlockZ() == b.getZ() && loc.getBlockY() == b.getY() + 1;
    }

    private void startRun(Player p, TunnelStore.Channel ch, TunnelStore.Endpoint dest, Block coreBlock) {
        Run run = new Run();
        run.p = p;
        run.ch = ch;
        run.dest = dest;
        run.cx = coreBlock.getX();
        run.cy = coreBlock.getY();
        run.cz = coreBlock.getZ();
        run.world = coreBlock.getWorld().getName();
        run.total = plugin.store().time() * 20;
        runs.put(p.getUniqueId(), run);
        m().send(p, "tp.start", plugin.store().time());
        try {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.4F);
        } catch (Throwable ignored) {
        }
        run.task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick(run);
            }
        }, 1L, 1L);
    }

    private void tick(Run run) {
        Player p = run.p;
        if (!p.isOnline()) {
            stop(run, null);
            return;
        }
        if (!p.getWorld().getName().equals(run.world)) {
            stop(run, m().raw("tp.cancelled-left"));
            return;
        }
        Location loc = p.getLocation();
        if (!(loc.getBlockX() == run.cx && loc.getBlockZ() == run.cz && loc.getBlockY() == run.cy + 1)) {
            stop(run, m().raw("tp.cancelled-left"));
            return;
        }
        World w = p.getWorld();
        double prog = (double) run.tick / (double) run.total;
        double ang = run.tick * 0.85;
        double r = 0.62;
        for (int k = 0; k < 2; k++) {
            double a = ang + k * Math.PI;
            double x = run.cx + 0.5 + r * Math.cos(a);
            double z = run.cz + 0.5 + r * Math.sin(a);
            double y = run.cy + 0.25 + prog * 2.1;
            w.spawnParticle(Particle.PORTAL, x, y, z, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.WITCH, x, y, z, 1, 0.08, 0.08, 0.08, 0);
        }
        run.tick++;
        if (run.tick >= run.total) {
            stop(run, null);
            finish(run);
        }
    }

    private void stop(Run run, String msg) {
        Run cur = runs.remove(run.p.getUniqueId());
        if (cur != null && cur.task != null) {
            try {
                cur.task.cancel();
            } catch (Throwable ignored) {
            }
        }
        if (msg != null && run.p.isOnline()) {
            run.p.sendMessage(msg);
        }
    }

    private void finish(Run run) {
        Player p = run.p;
        TunnelStore.Channel ch = run.ch;
        boolean owner = p.getUniqueId().equals(ch.owner);
        boolean admin = p.hasPermission("magicteleport.admin");

        // ① 读满之后才提示没权限（按设计文档要求）
        if (!owner && !ch.allowOthers && !admin) {
            m().send(p, "tp.no-permission");
            return;
        }
        // ② 收费（主人免费；只有开启 charge 才收）
        if (plugin.store().charge() && !owner && !admin) {
            if (!plugin.fee().tryPay(p, ch)) {
                m().send(p, "tp.insufficient");
                return;
            }
        }
        // ③ 目的地复查 + 传送
        World w = Bukkit.getWorld(run.dest.world);
        if (w == null) {
            m().send(p, "tp.dest-world-missing");
            return;
        }
        if (w.getBlockAt(run.dest.x, run.dest.y, run.dest.z).getType() != plugin.store().core()) {
            m().send(p, "tp.dest-broken");
            return;
        }
        Location to = new Location(w, run.dest.x + 0.5, run.dest.y + 1, run.dest.z + 0.5,
                p.getLocation().getYaw(), p.getLocation().getPitch());
        p.teleport(to);
        try {
            w.spawnParticle(Particle.PORTAL, to, 40, 0.5, 1.2, 0.5, 0.06);
            p.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        } catch (Throwable ignored) {
        }
        m().send(p, "tp.arrived");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        Run r = runs.remove(u);
        if (r != null && r.task != null) {
            try {
                r.task.cancel();
            } catch (Throwable ignored) {
            }
        }
        bindMode.remove(u);
        bindFirst.remove(u);
        bindSince.remove(u);
        containerSel.remove(u);
        containerSince.remove(u);
    }
}
