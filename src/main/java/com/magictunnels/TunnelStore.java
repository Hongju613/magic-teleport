package com.magictunnels;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 通道与配置的**唯一数据源**：所有状态都在 plugins/MagicTeleport/tunnels.yml 里，改完调 {@link #save()}。
 * 其它类一律通过这里读写，不许各存一份（架构硬规矩：一个事实只有一个权威来源）。
 */
public class TunnelStore {

    /** 隧道的一端：核心方块的位置（落点 = 它正上方） */
    public static class Endpoint {
        public String world = "";
        public int x, y, z;

        public Endpoint() {
        }

        public Endpoint(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public boolean same(Endpoint o) {
            return o != null && world.equalsIgnoreCase(o.world) && x == o.x && y == o.y && z == o.z;
        }
    }

    /** 收费容器位置 */
    public static class ContainerRef {
        public String world = "";
        public int x, y, z;
    }

    /** 一条传送通道 */
    public static class Channel {
        public String id;
        public UUID owner;
        public String name = "";
        public boolean allowOthers = true;
        public Endpoint a = new Endpoint();
        public Endpoint b = new Endpoint();
        public Map<Material, Integer> feeItems = new LinkedHashMap<Material, Integer>();
        public double feeMoney;
        public ContainerRef container;

        public Endpoint other(Endpoint e) {
            return e.same(a) ? b : a;
        }

        public boolean contains(Endpoint e) {
            return a.same(e) || b.same(e);
        }
    }

    private final MagicTeleport plugin;
    private File file;
    private YamlConfiguration yml = new YamlConfiguration();

    // ---- 配置 ----
    private int amount = 4;
    private int time = 3;
    private boolean sell;
    private int price;
    private boolean charge;
    private boolean vipallow;
    private final Map<Integer, Integer> vipamount = new HashMap<Integer, Integer>();
    private Material core = Material.REINFORCED_DEEPSLATE;
    private final Map<String, Boolean> worlds = new HashMap<String, Boolean>();
    private List<String> template;      // 54 格，下标 = dy*9 + (dz+1)*3 + (dx+1)，锚点(0,0,0)=核心
    private final Map<String, Channel> channels = new LinkedHashMap<String, Channel>();
    private final Map<UUID, Integer> bought = new HashMap<UUID, Integer>();
    private int nextId = 1;

    TunnelStore(MagicTeleport plugin) {
        this.plugin = plugin;
    }

    // ================= 读 =================

    public int amount() {
        return amount;
    }

    public int time() {
        return time;
    }

    public boolean sell() {
        return sell;
    }

    public int price() {
        return price;
    }

    public boolean charge() {
        return charge;
    }

    public boolean vipallow() {
        return vipallow;
    }

    public int vipAmount(int level) {
        Integer v = vipamount.get(level);
        return v == null ? 0 : v;
    }

    public Material core() {
        return core;
    }

    public boolean worldAllow(String world) {
        if (world == null) {
            return false;
        }
        Boolean b = worlds.get(world.toLowerCase(Locale.ROOT));
        return b != null && b;
    }

    public List<String> template() {
        return template;
    }

    public boolean hasTemplate() {
        return template != null && template.size() == 54;
    }

    public int bought(UUID u) {
        Integer v = bought.get(u);
        return v == null ? 0 : v;
    }

    /** 基础上限 = 服务器默认 + 已购 */
    public int baseLimit(UUID u) {
        return amount + bought(u);
    }

    public int channelCount() {
        return channels.size();
    }

    public Collection<Channel> all() {
        return channels.values();
    }

    public Channel byId(String id) {
        return channels.get(id);
    }

    /** 某玩家拥有的通道，按编号升序 */
    public List<Channel> listByOwner(UUID u) {
        List<Channel> out = new ArrayList<Channel>();
        for (Channel ch : channels.values()) {
            if (u.equals(ch.owner)) {
                out.add(ch);
            }
        }
        Collections.sort(out, new Comparator<Channel>() {
            public int compare(Channel x, Channel y) {
                return num(x.id) - num(y.id);
            }
        });
        return out;
    }

    private static int num(String id) {
        try {
            return Integer.parseInt(id);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * 某个核心方块位置是否已被某条通道占用。
     *
     * @return null=没占用；否则 Object[]{Channel, Boolean}，Boolean=true 表示是 A 端
     */
    public Object[] channelAt(String world, int x, int y, int z) {
        for (Channel ch : channels.values()) {
            if (world.equalsIgnoreCase(ch.a.world) && ch.a.x == x && ch.a.y == y && ch.a.z == z) {
                return new Object[]{ch, Boolean.TRUE};
            }
            if (world.equalsIgnoreCase(ch.b.world) && ch.b.x == x && ch.b.y == y && ch.b.z == z) {
                return new Object[]{ch, Boolean.FALSE};
            }
        }
        return null;
    }

    // ================= 写 =================

    public void setAmount(int n) {
        amount = Math.max(0, n);
    }

    public void setTime(int n) {
        time = Math.max(1, n);
    }

    public void setSell(boolean b) {
        sell = b;
    }

    public void setPrice(int n) {
        price = Math.max(0, n);
    }

    public void setCharge(boolean b) {
        charge = b;
    }

    public void setVipallow(boolean b) {
        vipallow = b;
    }

    public void setVipAmount(int level, int n) {
        vipamount.put(level, Math.max(0, n));
    }

    public void setCore(Material m) {
        if (m != null && m.isBlock()) {
            core = m;
        }
    }

    public void setWorldAllow(String world, boolean b) {
        worlds.put(world.toLowerCase(Locale.ROOT), b);
    }

    public void setTemplate(List<String> t) {
        template = t;
    }

    /** 创建通道（自动分配编号 + 落盘），返回新通道 */
    public Channel create(UUID owner, String name, Endpoint a, Endpoint b) {
        Channel ch = new Channel();
        ch.id = String.valueOf(nextId++);
        ch.owner = owner;
        ch.name = name;
        ch.a = a;
        ch.b = b;
        channels.put(ch.id, ch);
        save();
        return ch;
    }

    public void remove(Channel ch) {
        channels.remove(ch.id);
        save();
    }

    public void addBought(UUID u) {
        bought.put(u, bought(u) + 1);
        save();
    }

    // ================= 落盘 =================

    public void load() {
        file = new File(plugin.getDataFolder(), "tunnels.yml");
        yml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        boolean changed = applyDefaults();
        readConfig();
        readChannels();
        readPlayers();
        if (changed) {
            save();
        }
    }

    private boolean applyDefaults() {
        boolean changed = false;
        if (!yml.contains("配置.amount")) {
            yml.set("配置.amount", 4);
            changed = true;
        }
        if (!yml.contains("配置.time")) {
            yml.set("配置.time", 3);
            changed = true;
        }
        if (!yml.contains("配置.sell")) {
            yml.set("配置.sell", false);
            changed = true;
        }
        if (!yml.contains("配置.price")) {
            yml.set("配置.price", 0);
            changed = true;
        }
        if (!yml.contains("配置.charge")) {
            yml.set("配置.charge", false);
            changed = true;
        }
        if (!yml.contains("配置.vipallow")) {
            yml.set("配置.vipallow", false);
            changed = true;
        }
        if (!yml.contains("配置.core")) {
            yml.set("配置.core", "REINFORCED_DEEPSLATE");
            changed = true;
        }
        if (!yml.contains("配置.vipamount")) {
            yml.set("配置.vipamount.1", 8);
            yml.set("配置.vipamount.2", 16);
            yml.set("配置.vipamount.3", 32);
            changed = true;
        }
        if (!yml.contains("配置.worlds")) {
            yml.set("配置.worlds", new LinkedHashMap<String, Boolean>());
            changed = true;
        }
        if (!yml.contains("通道")) {
            yml.set("通道", new LinkedHashMap<String, Object>());
            changed = true;
        }
        if (!yml.contains("玩家")) {
            yml.set("玩家", new LinkedHashMap<String, Object>());
            changed = true;
        }
        if (!yml.contains("下个id")) {
            yml.set("下个id", 1);
            changed = true;
        }
        return changed;
    }

    private void readConfig() {
        ConfigurationSection c = yml.getConfigurationSection("配置");
        if (c == null) {
            return;
        }
        amount = c.getInt("amount", 4);
        time = c.getInt("time", 3);
        sell = c.getBoolean("sell", false);
        price = c.getInt("price", 0);
        charge = c.getBoolean("charge", false);
        vipallow = c.getBoolean("vipallow", false);
        core = parseMaterial(c.getString("core", "REINFORCED_DEEPSLATE"));
        vipamount.clear();
        ConfigurationSection va = c.getConfigurationSection("vipamount");
        if (va != null) {
            for (String k : va.getKeys(false)) {
                try {
                    vipamount.put(Integer.parseInt(k), va.getInt(k));
                } catch (Throwable ignored) {
                }
            }
        }
        worlds.clear();
        ConfigurationSection ws = c.getConfigurationSection("worlds");
        if (ws != null) {
            for (String k : ws.getKeys(false)) {
                worlds.put(k.toLowerCase(Locale.ROOT), ws.getBoolean(k));
            }
        }
        template = loadTemplate(c.get("template"));
    }

    private List<String> loadTemplate(Object raw) {
        if (!(raw instanceof List)) {
            return null;
        }
        List<String> out = new ArrayList<String>(Collections.<String>nCopies(54, "AIR"));
        List<?> layers = (List<?>) raw;
        for (int dy = 0; dy < 6 && dy < layers.size(); dy++) {
            Object l = layers.get(dy);
            if (!(l instanceof List)) {
                return null;
            }
            List<?> rows = (List<?>) l;
            for (int dz = 0; dz < 3 && dz < rows.size(); dz++) {
                Object r = rows.get(dz);
                if (!(r instanceof List)) {
                    return null;
                }
                List<?> cols = (List<?>) r;
                for (int dx = 0; dx < 3 && dx < cols.size(); dx++) {
                    Object v = cols.get(dx);
                    out.set(dy * 9 + dz * 3 + dx, v == null ? "AIR" : String.valueOf(v));
                }
            }
        }
        return out;
    }

    private Object templateToYaml(List<String> t) {
        List<Object> layers = new ArrayList<Object>();
        for (int dy = 0; dy < 6; dy++) {
            List<Object> rows = new ArrayList<Object>();
            for (int dz = 0; dz < 3; dz++) {
                List<String> cols = new ArrayList<String>();
                for (int dx = 0; dx < 3; dx++) {
                    cols.add(t.get(dy * 9 + dz * 3 + dx));
                }
                rows.add(cols);
            }
            layers.add(rows);
        }
        return layers;
    }

    private void readChannels() {
        channels.clear();
        ConfigurationSection s = yml.getConfigurationSection("通道");
        if (s == null) {
            return;
        }
        for (String id : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(id);
            if (c == null) {
                continue;
            }
            Channel ch = new Channel();
            ch.id = id;
            try {
                ch.owner = UUID.fromString(c.getString("主人", ""));
            } catch (Throwable t) {
                continue;   // 坏数据直接跳过，别崩
            }
            ch.name = c.getString("名称", id);
            ch.allowOthers = c.getBoolean("允许他人", true);
            readEndpoint(c.getConfigurationSection("A"), ch.a);
            readEndpoint(c.getConfigurationSection("B"), ch.b);
            ch.feeItems.clear();
            ConfigurationSection f = c.getConfigurationSection("收费物品");
            if (f != null) {
                for (String k : f.getKeys(false)) {
                    Material m = parseMaterialOrNull(k);
                    if (m != null) {
                        ch.feeItems.put(m, Math.max(1, Math.min(64, f.getInt(k))));
                    }
                }
            }
            ch.feeMoney = c.getDouble("金币价格", 0);
            ConfigurationSection cc = c.getConfigurationSection("容器");
            if (cc != null) {
                List<Integer> p = cc.getIntegerList("位置");
                if (p != null && p.size() >= 3) {
                    ch.container = new ContainerRef();
                    ch.container.world = cc.getString("世界", "");
                    ch.container.x = p.get(0);
                    ch.container.y = p.get(1);
                    ch.container.z = p.get(2);
                }
            }
            channels.put(id, ch);
        }
    }

    private void readEndpoint(ConfigurationSection s, Endpoint e) {
        if (s == null) {
            return;
        }
        e.world = s.getString("世界", "");
        List<Integer> p = s.getIntegerList("核心");
        if (p != null && p.size() >= 3) {
            e.x = p.get(0);
            e.y = p.get(1);
            e.z = p.get(2);
        }
    }

    private void readPlayers() {
        bought.clear();
        ConfigurationSection s = yml.getConfigurationSection("玩家");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try {
                    UUID u = UUID.fromString(k);
                    ConfigurationSection c = s.getConfigurationSection(k);
                    bought.put(u, c == null ? 0 : Math.max(0, c.getInt("已购上限", 0)));
                } catch (Throwable ignored) {
                }
            }
        }
        nextId = Math.max(1, yml.getInt("下个id", 1));
    }

    public void save() {
        try {
            ConfigurationSection cfg = yml.createSection("配置");
            cfg.set("amount", amount);
            cfg.set("time", time);
            cfg.set("sell", sell);
            cfg.set("price", price);
            cfg.set("charge", charge);
            cfg.set("vipallow", vipallow);
            cfg.set("core", core.name());
            Map<String, Integer> va = new LinkedHashMap<String, Integer>();
            for (Map.Entry<Integer, Integer> e : vipamount.entrySet()) {
                va.put(String.valueOf(e.getKey()), e.getValue());
            }
            cfg.set("vipamount", va);
            cfg.set("worlds", new LinkedHashMap<String, Boolean>(worlds));
            cfg.set("template", template == null ? null : templateToYaml(template));

            yml.set("通道", null);
            ConfigurationSection chs = yml.createSection("通道");
            for (Channel ch : channels.values()) {
                ConfigurationSection c = chs.createSection(ch.id);
                c.set("主人", ch.owner.toString());
                c.set("名称", ch.name);
                c.set("允许他人", ch.allowOthers);
                writeEndpoint(c.createSection("A"), ch.a);
                writeEndpoint(c.createSection("B"), ch.b);
                if (ch.feeItems.isEmpty()) {
                    c.set("收费物品", new LinkedHashMap<String, Integer>());
                } else {
                    Map<String, Integer> f = new LinkedHashMap<String, Integer>();
                    for (Map.Entry<Material, Integer> e : ch.feeItems.entrySet()) {
                        f.put(e.getKey().name(), e.getValue());
                    }
                    c.set("收费物品", f);
                }
                c.set("金币价格", ch.feeMoney);
                if (ch.container != null) {
                    ConfigurationSection cc = c.createSection("容器");
                    cc.set("世界", ch.container.world);
                    cc.set("位置", java.util.Arrays.asList(ch.container.x, ch.container.y, ch.container.z));
                } else {
                    c.set("容器", null);
                }
            }

            yml.set("玩家", null);
            ConfigurationSection ps = yml.createSection("玩家");
            for (Map.Entry<UUID, Integer> e : bought.entrySet()) {
                ps.set(e.getKey().toString() + ".已购上限", e.getValue());
            }
            yml.set("下个id", nextId);

            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存 tunnels.yml 失败：" + e);
        }
    }

    private void writeEndpoint(ConfigurationSection s, Endpoint e) {
        s.set("世界", e.world);
        s.set("核心", java.util.Arrays.asList(e.x, e.y, e.z));
    }

    /** 核心方块用：必须是方块，非法就给默认值 */
    private Material parseMaterial(String raw) {
        Material m = parseMaterialOrNull(raw);
        return (m == null || !m.isBlock()) ? Material.REINFORCED_DEEPSLATE : m;
    }

    /** 通用解析（收费物品是**物品**不一定是方块，比如 DIAMOND —— 这里绝不能加 isBlock 限制） */
    private Material parseMaterialOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("MINECRAFT:")) {
            s = s.substring(10);
        }
        return Material.getMaterial(s);
    }
}
