package com.magictunnels;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 3×3×6 结构模板：**录制**（游戏里搭一个 → /magicteleport template set）
 * 与**逐格校验**（差一格就不认）。
 *
 * 坐标约定：锚点 = 核心方块 = 底面中心。
 *   · 相对坐标 dx,dz ∈ [-1,1]，dy ∈ [0,5]
 *   · 模板下标 = dy*9 + (dz+1)*3 + (dx+1)
 *   · (dy=0,dx=0,dz=0) 这格记作 {@link #CORE}，校验时按**当前配置的核心方块**比对
 *     —— 所以 /magicteleport block 换核心方块后，判定自动跟着走，模板不用重录。
 */
public final class TunnelTemplate {

    public static final String CORE = "CORE";
    private static final int SIZE = 54;

    private TunnelTemplate() {
    }

    /** 归一化方块名：去 minecraft: 前缀、大写 */
    public static String norm(String raw) {
        if (raw == null) {
            return "AIR";
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("MINECRAFT:")) {
            s = s.substring(10);
        }
        return s;
    }

    /** 以锚点（核心方块，底心）为基准录制 3×3×6 的 54 格 */
    public static List<String> record(World w, int cx, int cy, int cz) {
        List<String> out = new ArrayList<String>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            out.add("AIR");
        }
        for (int dy = 0; dy < 6; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    String v;
                    if (dy == 0 && dx == 0 && dz == 0) {
                        v = CORE;
                    } else {
                        v = norm(w.getBlockAt(cx + dx, cy + dy, cz + dz).getType().name());
                    }
                    out.set(dy * 9 + (dz + 1) * 3 + (dx + 1), v);
                }
            }
        }
        return out;
    }

    /**
     * 逐格校验。
     *
     * @return 空列表 = 完全一致；否则返回差异描述（最多 12 条）
     */
    public static List<String> check(List<String> t, World w, int cx, int cy, int cz, Material core) {
        List<String> bad = new ArrayList<String>();
        if (t == null || t.size() != SIZE) {
            bad.add("（服务器还没有录制模板，先 /magicteleport template set）");
            return bad;
        }
        String coreName = core == null ? "AIR" : core.name();
        for (int dy = 0; dy < 6; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    String want = t.get(dy * 9 + (dz + 1) * 3 + (dx + 1));
                    String expect = CORE.equals(want) ? coreName : want;
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (!b.getType().name().equalsIgnoreCase(expect)) {
                        if (bad.size() < 12) {
                            bad.add("相对(" + dx + "," + dy + "," + dz + ") 期望 "
                                    + expect.toLowerCase(Locale.ROOT) + " 实际 "
                                    + b.getType().name().toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        }
        return bad;
    }

    /** 一句话概括模板内容（录制回显用） */
    public static String describe(Messages m, List<String> t) {
        if (t == null || t.size() != SIZE) {
            return m.raw("template.none");
        }
        int solid = 0;
        int air = 0;
        int core = 0;
        int cry = 0;
        for (String v : t) {
            if (CORE.equals(v)) {
                core++;
            } else if ("AIR".equals(v)) {
                air++;
            } else {
                solid++;
                if (v.contains("CRYING_OBSIDIAN")) {
                    cry++;
                }
            }
        }
        return m.format("template.describe", solid + core, core, cry, air);
    }
}
