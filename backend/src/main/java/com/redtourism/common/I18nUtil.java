package com.redtourism.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多语言统一处理工具。
 *
 * 口径与用户端、管理端保持一致：
 * 1. 目标译文非空 -> 用译文覆盖中文字段（前端始终读 name/description 等标准字段即可）；
 * 2. 目标译文为空 -> 回退到中文，并把该字段记入 fallbackFields，由前端标注“中文”；
 * 3. lang=zh 或未知语言 -> 原样返回中文，不标注。
 */
public final class I18nUtil {

    public static final String ZH = "zh";
    public static final String EN = "en";
    public static final String JA = "ja";

    private I18nUtil() {}

    /** 规范化语言代码，仅支持 zh/en/ja，其余一律回退 zh。 */
    public static String normalize(String lang) {
        if (EN.equalsIgnoreCase(lang)) return EN;
        if (JA.equalsIgnoreCase(lang)) return JA;
        return ZH;
    }

    /**
     * 按语言覆盖字段值并记录回退情况。
     *
     * @param target      业务实体
     * @param lang        目标语言（zh/en/ja）
     * @param fieldGroups 每个元素为 {中文字段名, 英文字段名, 日文字段名}
     */
    public static void applyFields(Object target, String lang, String[]... fieldGroups) {
        if (target == null) return;
        String lng = normalize(lang);
        if (ZH.equals(lng)) {
            // 中文模式下不允许出现上一次请求遗留的回退标记
            setFallbackFields(target, null);
            return;
        }
        List<String> fallback = new ArrayList<>();
        for (String[] group : fieldGroups) {
            if (group == null || group.length < 3) continue;
            String zhField = group[0];
            String localizedField = EN.equals(lng) ? group[1] : group[2];
            Object localizedValue = readField(target, localizedField);
            if (localizedValue != null && !localizedValue.toString().trim().isEmpty()) {
                writeField(target, zhField, localizedValue);
            } else {
                fallback.add(zhField);
            }
        }
        setFallbackFields(target, fallback);
    }

    /** 批量处理。 */
    public static <T> void applyAll(Collection<T> targets, String lang, String[]... fieldGroups) {
        if (targets == null) return;
        for (T t : targets) applyFields(t, lang, fieldGroups);
    }

    /** 某字段是否发生了中文回退。 */
    public static boolean isFallback(Object target, String fieldName) {
        if (target == null) return false;
        Object ff = readField(target, "fallbackFields");
        if (ff instanceof Collection) return ((Collection<?>) ff).contains(fieldName);
        return false;
    }

    private static void setFallbackFields(Object target, List<String> fallback) {
        Map<String, Boolean> map = null;
        if (fallback != null) {
            map = new LinkedHashMap<>();
            for (String f : fallback) map.put(f, true);
        }
        writeField(target, "fallbackFields", map);
    }

    private static Object readField(Object target, String fieldName) {
        try {
            java.lang.reflect.Field f = findField(target.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(target);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static void writeField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = findField(target.getClass(), fieldName);
            if (f == null) return;
            f.setAccessible(true);
            f.set(target, value);
        } catch (IllegalAccessException ignored) {
        }
    }

    private static java.lang.reflect.Field findField(Class<?> type, String fieldName) {
        Class<?> c = type;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
