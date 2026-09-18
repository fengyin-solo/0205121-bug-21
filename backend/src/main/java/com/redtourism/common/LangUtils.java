package com.redtourism.common;

/**
 * 多语言工具类：统一处理 zh/en/ja 三种语言。
 * 所有列表、详情接口都必须通过本类解析语言并回填字段，
 * 缺少译文时统一回退中文，并在对象上通过 langFallback 标记注明。
 */
public final class LangUtils {

    public static final String ZH = "zh";
    public static final String EN = "en";
    public static final String JA = "ja";

    private LangUtils() {}

    /** 规范化语言参数，非法值一律回退为中文 */
    public static String normalize(String lang) {
        if (EN.equalsIgnoreCase(lang)) return EN;
        if (JA.equalsIgnoreCase(lang)) return JA;
        return ZH;
    }

    public static boolean isZh(String lang) {
        return ZH.equals(normalize(lang));
    }

    /**
     * 选择当前语言的展示值：译文非空用译文，否则回退中文。
     *
     * @param base        中文字段值
     * @param translated  目标语言字段值（可能为 null/空串）
     * @return 应展示给用户的值
     */
    public static String pick(String lang, String base, String translated) {
        if (isZh(lang)) return base;
        return (translated != null && !translated.isEmpty()) ? translated : base;
    }

    /**
     * 与 {@link #pick(String, String, String)} 相同，额外通过 out[0] 告知调用方是否发生了回退。
     * 仅当中文原文非空而译文缺失时才标记为回退；原文本身为空则不算。
     */
    public static String pick(String lang, String base, String translated, boolean[] out) {
        if (out != null && out.length > 0) out[0] = false;
        if (isZh(lang)) return base;
        if (translated != null && !translated.isEmpty()) return translated;
        if (base != null && !base.isEmpty() && out != null && out.length > 0) out[0] = true;
        return base;
    }
}
