package com.magictunnels;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 多语言提示（单一入口）。
 *
 * 设计：
 *   · `plugins/MagicTeleport/config.yml` 的 `lang` 决定用哪个语言文件
 *   · 内置 `lang/en_US.yml` 与 `lang/zh_CN.yml`（启动时解压到 `plugins/MagicTeleport/lang/`，可直接改）
 *   · 找不到的 key 先回退 en_US，再回退成 `!!key!!`（方便一眼看出漏翻）
 *   · 占位符 `{0}` `{1}`…，加载时把 `&` 色码转成 `§`
 */
public class Messages {

    private final MagicTeleport plugin;
    private YamlConfiguration cfg = new YamlConfiguration();
    private YamlConfiguration fallback = new YamlConfiguration();
    private String lang = "en_US";

    Messages(MagicTeleport plugin) {
        this.plugin = plugin;
    }

    public String lang() {
        return lang;
    }

    public void load() {
        plugin.reloadConfig();
        lang = plugin.getConfig().getString("lang", "en_US");
        if (lang == null || lang.trim().isEmpty()) {
            lang = "en_US";
        }
        File dir = new File(plugin.getDataFolder(), "lang");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("无法创建语言目录 " + dir);
        }
        // 内置语言：缺文件就解出来（已存在不覆盖，方便玩家自定义）
        for (String code : new String[]{"en_US", "zh_CN"}) {
            File f = new File(dir, code + ".yml");
            if (!f.exists()) {
                try {
                    plugin.saveResource("lang/" + code + ".yml", false);
                } catch (Throwable ignored) {
                }
            }
        }
        cfg = loadLang(dir, lang);
        fallback = loadLang(dir, "en_US");
    }

    private YamlConfiguration loadLang(File dir, String code) {
        File f = new File(dir, code + ".yml");
        YamlConfiguration out = null;
        if (f.exists()) {
            try {
                out = YamlConfiguration.loadConfiguration(f);
            } catch (Throwable t) {
                plugin.getLogger().warning("语言文件读取失败 " + f.getName() + "：" + t);
            }
        } else {
            InputStream in = plugin.getResource("lang/" + code + ".yml");
            if (in != null) {
                try {
                    out = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                } catch (Throwable t) {
                    plugin.getLogger().warning("内置语言文件读取失败 " + code + "：" + t);
                }
            }
        }
        return out == null ? new YamlConfiguration() : out;
    }

    /** 取一条（已转色码）；key 缺失返回 !!key!! */
    public String raw(String key) {
        String s = cfg.getString(key);
        if (s == null) {
            s = fallback.getString(key);
        }
        if (s == null) {
            return "!!" + key + "!!";
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** 带占位符取一条：{0}、{1}… */
    public String format(String key, Object... args) {
        String s = raw(key);
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }

    /** 取列表（GUI 的 lore / 帮助行） */
    public List<String> list(String key) {
        List<String> from = cfg.getStringList(key);
        if (from == null || from.isEmpty()) {
            from = fallback.getStringList(key);
        }
        List<String> out = new ArrayList<String>();
        if (from != null) {
            for (String s : from) {
                out.add(ChatColor.translateAlternateColorCodes('&', s));
            }
        }
        return out;
    }

    public void send(CommandSender s, String key, Object... args) {
        s.sendMessage(format(key, args));
    }
}
