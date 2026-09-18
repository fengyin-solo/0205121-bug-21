package com.redtourism.controller;

import com.redtourism.common.LangUtils;
import com.redtourism.common.Result;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端译文批量导入/导出。
 *
 * 设计要点（与用户端同一语言口径）：
 * 1. 文件按“ID 列”匹配记录，与行顺序、列顺序无关——顺序错位不会再发生；
 * 2. 某个译文单元格留空 => 该语言字段置空 => 用户端展示时统一回退中文并注明；
 * 3. 整个导入在单个事务内完成（见 TranslationServiceImpl），任何一行解析/校验
 *    或落库失败都会整体回滚，不会留下“半份译文”；
 * 4. 重新上传会覆盖该语言下一次导入的全部字段结果。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/translation")
public class TranslationController {

    @Autowired
    private ScenicSpotService spotService;
    @Autowired
    private RouteService routeService;
    @Autowired
    private FoodService foodService;
    @Autowired
    private CultureService cultureService;
    @Autowired
    private TranslationService translationService;

    /** 每种模块允许导入的列（字段 key -> CSV 表头），顺序即模板列顺序 */
    private static final Map<String, LinkedHashMap<String, String>> TEMPLATE_COLUMNS = new LinkedHashMap<>();
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

    /**
     * 下载导入模板（含全部记录 ID 与中文原文，便于逐条翻译）。
     * GET /api/admin/translation/template?type=spot&lang=en
     */
    @GetMapping("/template")
    public void template(@RequestParam String type,
                         @RequestParam(defaultValue = "en") String lang,
                         javax.servlet.http.HttpServletResponse response) throws Exception {
        String module = checkModule(type);
        String language = LangUtils.normalize(lang);
        if (LangUtils.isZh(language)) throw new IllegalArgumentException("译文语言只能是 en 或 ja");

        String filename = module + "_" + language + "_template.csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + filename);
        java.io.PrintWriter writer = response.getWriter();
        writer.write('\uFEFF'); // UTF-8 BOM，Excel 打开不乱码

        LinkedHashMap<String, String> columns = TEMPLATE_COLUMNS.get(module);
        // 表头注明目标语言；导入时按 startsWith 匹配，列顺序/后缀不影响
        List<String> headers = new ArrayList<>();
        for (String v : columns.values()) headers.add(v + "(" + language + ")");
        writer.println(toCsvLine(headers));

        for (Object entity : allEntities(module)) {
            List<String> row = new ArrayList<>();
            for (String key : columns.keySet()) {
                row.add(getField(entity, key));
            }
            writer.println(toCsvLine(row));
        }
        writer.flush();
    }

    /**
     * 上传译文文件（CSV，UTF-8）。
     * POST multipart/form-data：file=文件，type=spot/route/food/culture，lang=en/ja
     */
    @PostMapping("/import")
    public Result<Map<String, Object>> importTranslations(@RequestParam("file") MultipartFile file,
                                                          @RequestParam String type,
                                                          @RequestParam String lang,
                                                          HttpSession session) throws Exception {
        if (session.getAttribute(com.redtourism.common.Constants.SESSION_USER) == null) {
            return Result.error(401, "请先登录");
        }
        String module = checkModule(type);
        String language = LangUtils.normalize(lang);
        if (LangUtils.isZh(language)) {
            throw new IllegalArgumentException("译文语言只能是 en 或 ja");
        }

        Map<String, Object> summary = translationService.importTranslations(module, language, file);
        int updated = summary.get("updated") == null ? 0 : (Integer) summary.get("updated");
        int blankCells = summary.get("blankCells") == null ? 0 : (Integer) summary.get("blankCells");
        log.info("译文导入完成：module={}, lang={}, 更新 {} 条，留空 {} 个字段", module, language, updated, blankCells);
        return Result.success("导入成功，共更新 " + updated + " 条记录", summary);
    }

    /* ==================== 模板生成 ==================== */

    private String checkModule(String type) {
        if (type == null || !TEMPLATE_COLUMNS.containsKey(type)) {
            throw new IllegalArgumentException("type 必须是 spot / route / food / culture 之一");
        }
        return type;
    }

    private List<Object> allEntities(String module) {
        List<Object> result = new ArrayList<>();
        switch (module) {
            case "spot": result.addAll(spotService.list()); break;
            case "route": result.addAll(routeService.list()); break;
            case "food": result.addAll(foodService.list()); break;
            case "culture": result.addAll(cultureService.list()); break;
            default: break;
        }
        return result;
    }

    /** 读取实体上某个字段（含 id 与中文原文字段），用于导出模板 */
    private String getField(Object entity, String key) {
        String value = "";
        if ("id".equals(key)) {
            value = String.valueOf(tryInvoke(entity, "getId"));
        } else if (entity instanceof ScenicSpot) {
            ScenicSpot s = (ScenicSpot) entity;
            switch (key) {
                case "name": value = nz(s.getName()); break;
                case "description": value = nz(s.getDescription()); break;
                case "ticketReservation": value = nz(s.getTicketReservation()); break;
                case "suggestedDuration": value = nz(s.getSuggestedDuration()); break;
                case "itemsToBring": value = nz(s.getItemsToBring()); break;
                default: break;
            }
        } else if (entity instanceof Route) {
            Route r = (Route) entity;
            if ("name".equals(key)) value = nz(r.getName());
            if ("description".equals(key)) value = nz(r.getDescription());
        } else if (entity instanceof Food) {
            Food f = (Food) entity;
            if ("name".equals(key)) value = nz(f.getName());
            if ("description".equals(key)) value = nz(f.getDescription());
        } else if (entity instanceof CultureContent) {
            CultureContent c = (CultureContent) entity;
            if ("title".equals(key)) value = nz(c.getTitle());
            if ("content".equals(key)) value = nz(c.getContent());
        }
        return value;
    }

    private String toCsvLine(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(csvEscape(values.get(i)));
        }
        return sb.toString();
    }

    private String csvEscape(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    private String nz(String s) { return s == null ? "" : s; }

    private Object tryInvoke(Object target, String getter) {
        try {
            return target.getClass().getMethod(getter).invoke(target);
        } catch (Exception e) {
            return "";
        }
    }
}
