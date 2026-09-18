package com.redtourism.service.impl;

import com.redtourism.common.I18nUtil;
import com.redtourism.service.TranslationImportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 译文 CSV 批量导入实现。
 *
 * 设计要点：
 * - 按表头列名解析（id/name/...），行与数据通过 id 对应，调整文件内行顺序不会错位；
 * - 单事务“清空目标语言列 -> 逐行更新”，再次上传整份覆盖上次结果；任意一行失败全部回滚；
 * - id 在数据库中不存在时直接报错回滚，防止静默错配。
 */
@Slf4j
@Service
public class TranslationImportServiceImpl implements TranslationImportService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 一种业务类型的翻译配置。 */
    private static class TypeConfig {
        final String table;
        /** 字段定义：{CSV列名, 中文源列, en列, ja列} */
        final String[][] fields;

        TypeConfig(String table, String[][] fields) {
            this.table = table;
            this.fields = fields;
        }
    }

    private static final Map<String, TypeConfig> TYPE_CONFIGS = new LinkedHashMap<>();

    static {
        // 景点：列表/详情展示需要的名称与简介
        TYPE_CONFIGS.put("spot", new TypeConfig("scenic_spot", new String[][]{
                {"name", "name", "name_en", "name_ja"},
                {"description", "description", "description_en", "description_ja"}
        }));
        // 线路
        TYPE_CONFIGS.put("route", new TypeConfig("route", new String[][]{
                {"name", "name", "name_en", "name_ja"},
                {"description", "description", "description_en", "description_ja"}
        }));
        // 红色文化
        TYPE_CONFIGS.put("culture", new TypeConfig("culture_content", new String[][]{
                {"title", "title", "title_en", "title_ja"},
                {"content", "content", "content_en", "content_ja"}
        }));
        // 美食
        TYPE_CONFIGS.put("food", new TypeConfig("food", new String[][]{
                {"name", "name", "name_en", "name_ja"},
                {"description", "description", "description_en", "description_ja"}
        }));
    }

    private TypeConfig requireConfig(String targetType) {
        if (targetType == null) throw new IllegalArgumentException("缺少译文类型");
        TypeConfig config = TYPE_CONFIGS.get(targetType.toLowerCase());
        if (config == null) {
            throw new IllegalArgumentException("不支持的译文类型：" + targetType
                    + "（可选 spot/route/culture/food）");
        }
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importCsv(String targetType, String lang, byte[] csvBytes) {
        TypeConfig config = requireConfig(targetType);
        String lng = I18nUtil.normalize(lang);
        if (I18nUtil.ZH.equals(lng)) throw new IllegalArgumentException("中文为源语言，无需导入译文");

        String content = new String(csvBytes, StandardCharsets.UTF_8);
        if (content.startsWith("﻿")) content = content.substring(1); // 去 BOM
        List<List<String>> rows = parseCsv(content);
        if (rows.size() < 2) throw new IllegalArgumentException("文件内容为空或缺少表头");

        List<String> header = rows.get(0);
        int idCol = header.indexOf("id");
        if (idCol < 0) throw new IllegalArgumentException("CSV 缺少必需的 id 列，请使用系统提供的模板");

        // 列名 -> 目标数据库列
        Map<String, String> colMapping = new LinkedHashMap<>();
        for (String[] f : config.fields) {
            String csvCol = f[0] + "_" + lng;
            int idx = header.indexOf(csvCol);
            if (idx >= 0) colMapping.put(csvCol, EN.equals(lng) ? f[2] : f[3]);
        }
        if (colMapping.isEmpty()) {
            throw new IllegalArgumentException("CSV 中未找到任何 " + lng
                    + " 文译文列（列名应以 _" + lng + " 结尾），请使用系统提供的模板");
        }

        // 再次上传：整份覆盖。先清空该类型下该语言的所有译文列。
        for (String dbColumn : colMapping.values()) {
            jdbcTemplate.update("UPDATE " + config.table + " SET " + dbColumn + " = NULL");
        }

        ImportResult result = new ImportResult();
        result.setTargetType(targetType.toLowerCase());
        result.setLang(lng);

        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String idStr = getCell(row, idCol).trim();
            if (idStr.isEmpty()) continue; // 空行跳过

            Long id;
            try {
                id = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("第 " + (r + 1) + " 行的 id 不是数字：" + idStr);
            }

            // 收集本行译文列
            List<String> targetColumns = new ArrayList<>();
            List<String> values = new ArrayList<>();
            boolean hasAnyValue = false;
            for (Map.Entry<String, String> e : colMapping.entrySet()) {
                int colIdx = header.indexOf(e.getKey());
                String value = getCell(row, colIdx).trim();
                targetColumns.add(e.getValue());
                values.add(value);
                if (!value.isEmpty()) hasAnyValue = true;
            }
            if (!hasAnyValue) {
                result.setSkippedRows(result.getSkippedRows() + 1);
                continue;
            }

            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM " + config.table + " WHERE id = ?",
                    Integer.class, id);
            if (exists == null || exists == 0) {
                throw new IllegalArgumentException("第 " + (r + 1) + " 行的 id=" + id
                        + " 在数据库中不存在，已中止导入（本次上传未保存任何内容）");
            }

            StringBuilder sql = new StringBuilder("UPDATE ").append(config.table).append(" SET ");
            for (int i = 0; i < targetColumns.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append(targetColumns.get(i)).append(" = ?");
            }
            sql.append(" WHERE id = ?");
            Object[] params = new Object[values.size() + 1];
            for (int i = 0; i < values.size(); i++) params[i] = emptyToNull(values.get(i));
            params[values.size()] = id;
            jdbcTemplate.update(sql.toString(), params);

            result.setTotalRows(result.getTotalRows() + 1);
            result.setUpdatedRows(result.getUpdatedRows() + 1);
        }

        result.setMessage("成功覆盖 " + result.getUpdatedRows() + " 条"
                + (result.getSkippedRows() > 0 ? "，空行/空译文跳过 " + result.getSkippedRows() + " 条" : ""));
        return result;
    }

    @Override
    public String buildTemplate(String targetType, String lang) {
        TypeConfig config = requireConfig(targetType);
        String lng = I18nUtil.normalize(lang);
        if (I18nUtil.ZH.equals(lng)) throw new IllegalArgumentException("中文为源语言，无需译文模板");

        StringBuilder out = new StringBuilder();
        List<String> header = new ArrayList<>();
        header.add("id");
        for (String[] f : config.fields) header.add(f[0]); // 中文源列
        for (String[] f : config.fields) header.add(f[0] + "_" + lng); // 目标译文列（预填现有译文）
        out.append(toCsvLine(header)).append("\r\n");

        String[] zhCols = new String[config.fields.length];
        String[] trCols = new String[config.fields.length];
        for (int i = 0; i < config.fields.length; i++) {
            zhCols[i] = config.fields[i][1];
            trCols[i] = EN.equals(lng) ? config.fields[i][2] : config.fields[i][3];
        }
        StringBuilder query = new StringBuilder("SELECT id");
        for (String c : zhCols) query.append(", ").append(c);
        for (String c : trCols) query.append(", ").append(c);
        query.append(" FROM ").append(config.table).append(" ORDER BY id");

        List<Map<String, Object>> records = jdbcTemplate.queryForList(query.toString());
        for (Map<String, Object> rec : records) {
            List<String> line = new ArrayList<>();
            line.add(String.valueOf(rec.get("id")));
            for (String c : zhCols) line.add(asString(rec.get(c)));
            for (String c : trCols) line.add(asString(rec.get(c)));
            out.append(toCsvLine(line)).append("\r\n");
        }
        return out.toString();
    }

    // ==================== CSV 解析（支持引号、引号转义、字段内换行、CRLF） ====================

    private List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
            } else {
                if (ch == '"' && !fieldStarted) {
                    inQuotes = true;
                    fieldStarted = true;
                } else if (ch == ',') {
                    row.add(field.toString());
                    field.setLength(0);
                    fieldStarted = false;
                } else if (ch == '\n') {
                    row.add(field.toString());
                    rows.add(row);
                    row = new ArrayList<>();
                    field.setLength(0);
                    fieldStarted = false;
                } else if (ch == '\r') {
                    // CRLF：吞掉 \r 等待 \n；单独 \r 也按换行处理
                    if (i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                        i++;
                    }
                    row.add(field.toString());
                    rows.add(row);
                    row = new ArrayList<>();
                    field.setLength(0);
                    fieldStarted = false;
                } else {
                    field.append(ch);
                }
            }
        }
        // 文件末尾最后一行（无换行结尾）
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private String getCell(List<String> row, int idx) {
        return idx >= 0 && idx < row.size() && row.get(idx) != null ? row.get(idx) : "";
    }

    private String emptyToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private String asString(Object o) {
        return o == null ? "" : o.toString();
    }

    private String toCsvLine(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(cells.get(i)));
        }
        return sb.toString();
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
