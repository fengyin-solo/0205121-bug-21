package com.redtourism.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.redtourism.common.LangUtils;
import com.redtourism.entity.CultureContent;
import com.redtourism.entity.Food;
import com.redtourism.entity.Route;
import com.redtourism.entity.ScenicSpot;
import com.redtourism.service.CultureService;
import com.redtourism.service.FoodService;
import com.redtourism.service.RouteService;
import com.redtourism.service.ScenicSpotService;
import com.redtourism.service.TranslationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 译文 CSV 批量导入实现。
 *
 * 设计要点：
 * 1. 按“ID 列”匹配记录，与文件行顺序、列顺序都无关（表头按名称定位）；
 * 2. 译文单元格留空 => 对应语言列置 NULL => 用户端读取时回退中文并注明；
 * 3. 整个导入在单个事务内完成，解析/校验失败在任何 UPDATE 之前抛出，
 *    落库阶段任何一行失败也整体回滚，绝不留下“半份译文”；
 * 4. 重新上传会覆盖该语言下一次导入的全部字段（先按行完整覆盖，缺失字段置空）。
 */
@Slf4j
@Service
public class TranslationServiceImpl implements TranslationService {

    @Autowired
    private ScenicSpotService spotService;
    @Autowired
    private RouteService routeService;
    @Autowired
    private FoodService foodService;
    @Autowired
    private CultureService cultureService;

    /** 每种模块允许导入的列（字段 key -> CSV 表头），顺序即模板列顺序 */
    static final Map<String, LinkedHashMap<String, String>> TEMPLATE_COLUMNS = new LinkedHashMap<>();
    static {
        LinkedHashMap<String, String> spot = new LinkedHashMap<>();
        spot.put("id", "ID");
        spot.put("name", "名称");
        spot.put("description", "简介");
        spot.put("ticketReservation", "门票预约");
        spot.put("suggestedDuration", "建议停留");
        spot.put("itemsToBring", "随身物品");
        TEMPLATE_COLUMNS.put("spot", spot);

        LinkedHashMap<String, String> route = new LinkedHashMap<>();
        route.put("id", "ID");
        route.put("name", "名称");
        route.put("description", "简介");
        TEMPLATE_COLUMNS.put("route", route);

        LinkedHashMap<String, String> food = new LinkedHashMap<>();
        food.put("id", "ID");
        food.put("name", "名称");
        food.put("description", "简介");
        TEMPLATE_COLUMNS.put("food", food);

        LinkedHashMap<String, String> culture = new LinkedHashMap<>();
        culture.put("id", "ID");
        culture.put("title", "标题");
        culture.put("content", "正文");
        TEMPLATE_COLUMNS.put("culture", culture);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importTranslations(String module, String lang, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("译文文件不能为空");
        }

        // 1) 完整读取并解析（先读后写，任何异常都会在事务提交前抛出，数据库无改动）
        byte[] bytes = file.getBytes();
        // 兼容带 UTF-8 BOM 的文件（Excel 另存时常带）
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            bytes = java.util.Arrays.copyOfRange(bytes, 3, bytes.length);
        }
        List<List<String>> rows;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            rows = parseCsv(reader);
        }
        if (rows.size() < 2) {
            throw new IllegalArgumentException("文件内容为空：至少需要表头和一行译文数据");
        }

        LinkedHashMap<String, String> columns = TEMPLATE_COLUMNS.get(module);

        // 2) 表头校验：必须包含 ID 列与至少一个译文列，避免列错位造成串行
        Map<String, Integer> headerIndex = resolveHeader(rows.get(0), columns);

        // 3) 逐行校验：ID 必须存在且为数字，重复 ID 直接拒绝（防止同一记录被两行覆盖）
        List<ParsedRow> parsedRows = new ArrayList<>();
        Set<Long> seenIds = new LinkedHashSet<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> line = rows.get(i);
            // 跳过完全空白的行
            if (line.stream().allMatch(v -> v == null || v.trim().isEmpty())) continue;

            String idText = cell(line, headerIndex.get("id"));
            if (idText == null || idText.trim().isEmpty()) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行缺少 ID，已中止导入（未写入任何译文）");
            }
            long id;
            try {
                id = Long.parseLong(idText.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 行 ID 不是数字：" + idText + "，已中止导入");
            }
            if (!seenIds.add(id)) {
                throw new IllegalArgumentException("ID " + id + " 在文件中出现多次，无法确定以哪行为准，已中止导入");
            }

            Map<String, String> values = new LinkedHashMap<>();
            for (String key : columns.keySet()) {
                if ("id".equals(key)) continue;
                Integer idx = headerIndex.get(key);
                values.put(key, idx == null ? "" : cell(line, idx).trim());
            }
            parsedRows.add(new ParsedRow(id, values));
        }
        if (parsedRows.isEmpty()) {
            throw new IllegalArgumentException("文件中没有可导入的译文数据行");
        }

        // 4) 预加载并校验所有目标记录存在（记录不存在则整体失败，不产生部分更新）；
        //    更新在本方法事务内执行，任何一行失败整体回滚
        int updated = 0;
        int fallbackCells = 0;
        for (ParsedRow pr : parsedRows) {
            Object entity = loadEntity(module, pr.id);
            if (entity == null) {
                throw new IllegalArgumentException(
                        "ID " + pr.id + " 在" + moduleName(module) + "中不存在，已中止导入（未写入任何译文）");
            }
            applyTranslations(entity, pr.values, lang);
            updated++;
            for (String v : pr.values.values()) {
                if (v == null || v.isEmpty()) fallbackCells++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("updated", updated);
        summary.put("lang", lang);
        summary.put("type", module);
        summary.put("blankCells", fallbackCells);
        log.info("译文导入成功：module={}, lang={}, 更新记录数={}", module, lang, updated);
        return summary;
    }

    /* ==================== 落库 ==================== */

    private Object loadEntity(String module, Long id) {
        switch (module) {
            case "spot": return spotService.getById(id);
            case "route": return routeService.getById(id);
            case "food": return foodService.getById(id);
            case "culture": return cultureService.getById(id);
            default: return null;
        }
    }

    private String moduleName(String module) {
        switch (module) {
            case "spot": return "景点";
            case "route": return "线路";
            case "food": return "美食";
            case "culture": return "文化内容";
            default: return module;
        }
    }

    /**
     * 把一行译文写入对应语言列并立即 UPDATE。
     * 空串写 NULL（表示“译文缺失”，用户端回退中文）；显式 set 保证 NULL 也落库。
     */
    private void applyTranslations(Object entity, Map<String, String> values, String lang) {
        boolean en = LangUtils.EN.equals(lang);
        if (entity instanceof ScenicSpot) {
            ScenicSpot s = (ScenicSpot) entity;
            LambdaUpdateWrapper<ScenicSpot> w = new LambdaUpdateWrapper<>();
            w.eq(ScenicSpot::getId, s.getId());
            if (values.containsKey("name")) w.set(en ? ScenicSpot::getNameEn : ScenicSpot::getNameJa, nullable(values.get("name")));
            if (values.containsKey("description")) w.set(en ? ScenicSpot::getDescriptionEn : ScenicSpot::getDescriptionJa, nullable(values.get("description")));
            if (values.containsKey("ticketReservation")) w.set(en ? ScenicSpot::getTicketReservationEn : ScenicSpot::getTicketReservationJa, nullable(values.get("ticketReservation")));
            if (values.containsKey("suggestedDuration")) w.set(en ? ScenicSpot::getSuggestedDurationEn : ScenicSpot::getSuggestedDurationJa, nullable(values.get("suggestedDuration")));
            if (values.containsKey("itemsToBring")) w.set(en ? ScenicSpot::getItemsToBringEn : ScenicSpot::getItemsToBringJa, nullable(values.get("itemsToBring")));
            spotService.update(w);
        } else if (entity instanceof Route) {
            Route r = (Route) entity;
            LambdaUpdateWrapper<Route> w = new LambdaUpdateWrapper<>();
            w.eq(Route::getId, r.getId());
            if (values.containsKey("name")) w.set(en ? Route::getNameEn : Route::getNameJa, nullable(values.get("name")));
            if (values.containsKey("description")) w.set(en ? Route::getDescriptionEn : Route::getDescriptionJa, nullable(values.get("description")));
            routeService.update(w);
        } else if (entity instanceof Food) {
            Food f = (Food) entity;
            LambdaUpdateWrapper<Food> w = new LambdaUpdateWrapper<>();
            w.eq(Food::getId, f.getId());
            if (values.containsKey("name")) w.set(en ? Food::getNameEn : Food::getNameJa, nullable(values.get("name")));
            if (values.containsKey("description")) w.set(en ? Food::getDescriptionEn : Food::getDescriptionJa, nullable(values.get("description")));
            foodService.update(w);
        } else if (entity instanceof CultureContent) {
            CultureContent c = (CultureContent) entity;
            LambdaUpdateWrapper<CultureContent> w = new LambdaUpdateWrapper<>();
            w.eq(CultureContent::getId, c.getId());
            if (values.containsKey("title")) w.set(en ? CultureContent::getTitleEn : CultureContent::getTitleJa, nullable(values.get("title")));
            if (values.containsKey("content")) w.set(en ? CultureContent::getContentEn : CultureContent::getContentJa, nullable(values.get("content")));
            cultureService.update(w);
        }
    }

    /* ==================== CSV 解析（支持引号、逗号、换行，列顺序无关） ==================== */

    /**
     * 表头解析：按表头名称定位列，而不是按列序号，
     * 这样即使上传文件的列顺序与模板不同也不会错位。
     */
    private Map<String, Integer> resolveHeader(List<String> headers, LinkedHashMap<String, String> columns) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i) == null ? "" : headers.get(i).trim();
            for (Map.Entry<String, String> col : columns.entrySet()) {
                if (!index.containsKey(col.getKey()) && matchesHeader(h, col.getKey(), col.getValue())) {
                    index.put(col.getKey(), i);
                }
            }
        }
        if (!index.containsKey("id")) {
            throw new IllegalArgumentException("表头中找不到 ID 列，请使用系统提供的模板填写后上传");
        }
        boolean hasAnyField = columns.keySet().stream()
                .filter(k -> !"id".equals(k))
                .anyMatch(index::containsKey);
        if (!hasAnyField) {
            throw new IllegalArgumentException("表头中找不到任何译文列，请检查表头是否被修改");
        }
        return index;
    }

    private boolean matchesHeader(String header, String fieldKey, String title) {
        if (header.equalsIgnoreCase(fieldKey)) return true;
        if (header.equals(title)) return true;
        // 兼容用户在表头里附带语言标记，如 “名称(en)”
        return header.startsWith(title);
    }

    private List<List<String>> parseCsv(BufferedReader reader) throws Exception {
        List<List<String>> records = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        List<String> current = new ArrayList<>();
        boolean inQuotes = false;
        boolean content = false; // 文件是否出现过任何非空白内容
        // 单字符回退缓冲：处理形如 "abc", 的引号后紧跟分隔符的场景
        int pending = -1;
        while (true) {
            int read = pending != -1 ? pending : reader.read();
            pending = -1;
            if (read == -1) break;
            content = true;
            char c = (char) read;
            if (inQuotes) {
                if (c == '"') {
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        inQuotes = false;
                        if (next != -1) pending = next;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    current.add(field.toString());
                    field.setLength(0);
                } else if (c == '\n') {
                    current.add(field.toString());
                    field.setLength(0);
                    records.add(current);
                    current = new ArrayList<>();
                } else if (c != '\r') {
                    field.append(c);
                }
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("CSV 文件存在未闭合的引号，请检查后重新上传");
        }
        // 最后一行没有换行符结尾
        if (content && (field.length() > 0 || !current.isEmpty())) {
            current.add(field.toString());
            records.add(current);
        }
        return records;
    }

    /** 安全读取一行中指定下标的单元格，越界返回空串 */
    private String cell(List<String> line, int index) {
        if (index < 0 || index >= line.size()) return "";
        String v = line.get(index);
        return v == null ? "" : v;
    }

    private String nullable(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        return s;
    }

    private static class ParsedRow {
        final long id;
        final Map<String, String> values;
        ParsedRow(long id, Map<String, String> values) {
            this.id = id;
            this.values = values;
        }
    }
}
