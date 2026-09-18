package com.redtourism.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.redtourism.common.LangUtils;
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
                                 @RequestParam(required = false, defaultValue = "zh") String lang,
                                 @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        // refresh=false（如切换语言重新拉取）不累加浏览量
        Route r = refresh ? routeService.getDetail(id) : routeService.getById(id);
        if (r != null) applyLang(r, lang);
        return Result.success(r);
    }

    @GetMapping("/spots")
    public Result<List<RouteSpot>> spots(@RequestParam Long routeId,
                                         @RequestParam(required = false, defaultValue = "zh") String lang) {
        List<RouteSpot> list = routeService.getRouteSpots(routeId);
        list.forEach(rs -> applySpotLang(rs, lang));
        return Result.success(list);
    }

    @GetMapping("/themes")
    public Result<List<String>> themes() {
        List<String> themes = java.util.Arrays.asList(
                "红色研学", "经典打卡", "小众探秘", "亲子游", "红色+自然");
        return Result.success(themes);
    }

    /**
     * 按请求语言回填线路的可翻译字段；译文缺失回退中文并标记 langFallback。
     */
    public static void applyLang(Route r, String lang) {
        if (r == null) return;
        if (LangUtils.isZh(lang)) { r.setLangFallback(false); return; }
        boolean en = LangUtils.EN.equals(LangUtils.normalize(lang));
        boolean[] fb = new boolean[1];
        boolean fallback = false;
        r.setName(LangUtils.pick(lang, r.getName(), en ? r.getNameEn() : r.getNameJa(), fb));
        fallback |= fb[0];
        r.setDescription(LangUtils.pick(lang, r.getDescription(), en ? r.getDescriptionEn() : r.getDescriptionJa(), fb));
        fallback |= fb[0];
        r.setLangFallback(fallback);
    }

    /** 行程内景点名称同样按语言回填，保证与景点详情同语言呈现 */
    private void applySpotLang(RouteSpot rs, String lang) {
        if (rs == null) return;
        if (LangUtils.isZh(lang)) { rs.setLangFallback(false); return; }
        boolean en = LangUtils.EN.equals(LangUtils.normalize(lang));
        boolean[] fb = new boolean[1];
        rs.setSpotName(LangUtils.pick(lang, rs.getSpotName(), en ? rs.getSpotNameEn() : rs.getSpotNameJa(), fb));
        rs.setLangFallback(fb[0]);
    }
}
