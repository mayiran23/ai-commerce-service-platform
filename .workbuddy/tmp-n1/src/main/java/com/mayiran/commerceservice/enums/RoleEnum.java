package com.mayiran.commerceservice.enums;

/**
 * 用户角色枚举。
 * 为什么需要它：t_user 表里只有 role 一列，存的是代号 USER / AGENT / ADMIN；
 * 而页面要显示的是中文「普通用户 / 客服 / 管理员」。
 * 这张对照表只能在 Java 里维护，否则换个前端（以后做小程序）就得再写一遍。
 */
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
     */
    public static RoleEnum of(String code) {
        for (RoleEnum r : RoleEnum.values()) {
            if (r.name().equals(code)) {
                return r;
            }
        }
        throw new IllegalArgumentException("未知角色: " + code);
    }
}
