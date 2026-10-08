package com.mayiran.commerceservice.enums;

import lombok.extern.slf4j.Slf4j;

/**
 * 用户角色枚举。
 * 为什么需要它：t_user 表里只有 role 一列，存的是代号 USER / AGENT / ADMIN；
 * 而页面要显示的是中文「普通用户 / 客服 / 管理员」。
 * 这张对照表只能在 Java 里维护，否则换个前端（以后做小程序）就得再写一遍。
 */
@Slf4j
public enum RoleEnum {

    /** 普通用户：下单的人，登录后进订单页 */
    USER("普通用户"),
    /** 客服：处理工单的人，登录后进工单页 */
    AGENT("客服"),
    /** 管理员 */
    ADMIN("管理员");

    /** 该角色对应的中文文本。final：赋值后不许再改 */
    private final String text;

    /**
     * 构造器。枚举的构造器只能是 private ——
     * 这正是"只可能有这三个实例"的实现方式：外面 new 不出来。
     */
    RoleEnum(String text) {
        this.text = text;
    }

    /** 取中文。用法：RoleEnum.AGENT.getText() → "客服" */
    public String getText() {
        return text;
    }

    /**
     * 按代号查枚举，给"从数据库读出来的 role"用。
     * 用法：RoleEnum.of("AGENT").getText() → "客服"
     *
     * 【为什么不能见到不认识的值就抛异常】
     * 这个方法只在登录路径上被调用。一旦数据库里出现意料之外的 role 值
     * （脏数据、人工改库、以后加了新角色但忘了同步这个枚举），
     * 抛异常 = 这个账号**彻底登不进去**，而且报错信息对用户毫无意义。
     *
     * 所以按项目已经确立的 fail-safe 原则处理：**认不出来就降级成最小权限 USER**。
     * 方向和 StatsServiceImpl 里"剩余角色一律锁成只能看自己"一致 ——
     * 拿不准的时候，宁可少给权限，也不要多给，更不要把人挡在门外。
     *
     * 同时容忍大小写和首尾空格（"agent" / " AGENT " 都能认），
     * 避免因为一个空格把客服挡在门外 —— 这类问题排查起来极费时间。
     */
    public static RoleEnum of(String code) {
        if (code != null) {
            String normalized = code.trim().toUpperCase();
            for (RoleEnum r : RoleEnum.values()) {
                if (r.name().equals(normalized)) {
                    return r;
                }
            }
        }
        // 不认识的代号：降级为普通用户（最小权限），并留一条日志便于发现脏数据
        log.warn("未知角色代号 [{}]，已降级为 USER（最小权限）", code);
        return USER;
    }
}
