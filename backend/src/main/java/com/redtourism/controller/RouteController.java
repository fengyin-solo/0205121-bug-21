package com.redtourism.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.redtourism.common.I18nUtil;
import com.redtourism.common.Result;
import com.redtourism.entity.Route;
import com.redtourism.entity.RouteSpot;
import com.redtourism.service.RouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/route")
public class RouteController {

    @Autowired
    private RouteService routeService;

    @GetMapping("/list")
    public Result<IPage<Route>> list(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size,
                                      @RequestParam(required = false) Integer days,
                                      @RequestParam(required = false) String theme,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false, defaultValue = "zh") String lang) {
        IPage<Route> result = routeService.listRoutes(page, size, days, theme, keyword);
        result.getRecords().forEach(r -> applyLang(r, lang));
        return Result.success(result);
    }

    @GetMapping("/detail")
    public Result<Route> detail(@RequestParam Long id,
                                 @RequestParam(required = false, defaultValue = "zh") String lang) {
        Route r = routeService.getDetail(id);
        if (r != null) applyLang(r, lang);
        return Result.success(r);
    }

    @GetMapping("/spots")
    public Result<List<RouteSpot>> spots(@RequestParam Long routeId,
                                          @RequestParam(required = false, defaultValue = "zh") String lang) {
        List<RouteSpot> list = routeService.getRouteSpots(routeId);
        list.forEach(rs -> I18nUtil.applyFields(rs, lang,
                new String[]{"spotName", "spotNameEn", "spotNameJa"}));
        return Result.success(list);
    }

    @GetMapping("/themes")
    public Result<List<String>> themes() {
        List<String> themes = java.util.Arrays.asList(
                "红色研学", "经典打卡", "小众探秘", "亲子游", "红色+自然");
        return Result.success(themes);
    }

    /** 统一多语言口径，与景点/美食列表、详情一致：缺译文回退中文并标记。 */
    static void applyLang(Route r, String lang) {
        I18nUtil.applyFields(r, lang,
                new String[]{"name", "nameEn", "nameJa"},
                new String[]{"description", "descriptionEn", "descriptionJa"});
    }
}
