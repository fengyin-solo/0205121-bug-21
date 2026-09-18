package com.redtourism.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.redtourism.common.LangUtils;
import com.redtourism.common.Result;
import com.redtourism.entity.ScenicSpot;
import com.redtourism.entity.ScenicSpotImage;
import com.redtourism.service.ScenicSpotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spot")
public class ScenicSpotController {

    @Autowired
    private ScenicSpotService spotService;

    @GetMapping("/list")
    public Result<IPage<ScenicSpot>> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String region,
                                           @RequestParam(required = false) String theme,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String orderBy,
                                           @RequestParam(required = false, defaultValue = "zh") String lang) {
        IPage<ScenicSpot> result = spotService.listSpots(page, size, region, theme, status, keyword, orderBy);
        result.getRecords().forEach(s -> applyLang(s, lang));
        return Result.success(result);
    }

    @GetMapping("/detail")
    public Result<ScenicSpot> detail(@RequestParam Long id,
                                      @RequestParam(required = false, defaultValue = "zh") String lang,
                                      @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        // refresh=false（如切换语言重新拉取）不累加浏览量，只有首次进详情才计数
        ScenicSpot spot = refresh ? spotService.getDetail(id) : spotService.getById(id);
        if (spot != null) applyLang(spot, lang);
        return Result.success(spot);
    }

    @GetMapping("/images")
    public Result<List<ScenicSpotImage>> images(@RequestParam Long spotId) {
        return Result.success(spotService.getImages(spotId));
    }

    @GetMapping("/carousel")
    public Result<List<ScenicSpot>> carousel(@RequestParam(required = false, defaultValue = "zh") String lang) {
        List<ScenicSpot> list = spotService.getCarousel();
        list.forEach(s -> applyLang(s, lang));
        return Result.success(list);
    }

    @GetMapping("/related")
    public Result<List<ScenicSpot>> related(@RequestParam Long id,
                                            @RequestParam(required = false, defaultValue = "zh") String lang) {
        List<ScenicSpot> list = spotService.getRelated(id);
        list.forEach(s -> applyLang(s, lang));
        return Result.success(list);
    }

    @GetMapping("/hot")
    public Result<List<ScenicSpot>> hot(@RequestParam(defaultValue = "10") int limit,
                                        @RequestParam(required = false, defaultValue = "zh") String lang) {
        List<ScenicSpot> list = spotService.getHot(limit);
        list.forEach(s -> applyLang(s, lang));
        return Result.success(list);
    }

    @GetMapping("/search")
    public Result<IPage<ScenicSpot>> search(@RequestParam String keyword,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size,
                                             @RequestParam(required = false) String orderBy,
                                             @RequestParam(required = false, defaultValue = "zh") String lang) {
        IPage<ScenicSpot> result = spotService.listSpots(page, size, null, null, null, keyword, orderBy);
        result.getRecords().forEach(s -> applyLang(s, lang));
        return Result.success(result);
    }

    @GetMapping("/regions")
    public Result<List<String>> regions() {
        List<String> regions = java.util.Arrays.asList(
                "贵阳市", "遵义市", "六盘水市", "安顺市", "毕节市",
                "铜仁市", "黔东南州", "黔南州", "黔西南州");
        return Result.success(regions);
    }

    @GetMapping("/themes")
    public Result<List<String>> themes() {
        List<String> themes = java.util.Arrays.asList(
                "红色研学", "经典打卡", "革命遗址", "纪念馆", "战役遗址", "伟人故居");
        return Result.success(themes);
    }

    /**
     * 按请求语言回填景点的可翻译字段。
     * 译文缺失时保留中文原值并将 langFallback 置为 true，由前端统一注明。
     */
    public static void applyLang(ScenicSpot s, String lang) {
        if (s == null) return;
        if (LangUtils.isZh(lang)) { s.setLangFallback(false); return; }
        boolean en = LangUtils.EN.equals(LangUtils.normalize(lang));
        boolean fallback = false;
        boolean[] fb = new boolean[1];

        s.setName(LangUtils.pick(lang, s.getName(), en ? s.getNameEn() : s.getNameJa(), fb));
        fallback |= fb[0];
        s.setDescription(LangUtils.pick(lang, s.getDescription(), en ? s.getDescriptionEn() : s.getDescriptionJa(), fb));
        fallback |= fb[0];
        s.setTicketReservation(LangUtils.pick(lang, s.getTicketReservation(), en ? s.getTicketReservationEn() : s.getTicketReservationJa(), fb));
        fallback |= fb[0];
        s.setSuggestedDuration(LangUtils.pick(lang, s.getSuggestedDuration(), en ? s.getSuggestedDurationEn() : s.getSuggestedDurationJa(), fb));
        fallback |= fb[0];
        s.setItemsToBring(LangUtils.pick(lang, s.getItemsToBring(), en ? s.getItemsToBringEn() : s.getItemsToBringJa(), fb));
        fallback |= fb[0];

        s.setLangFallback(fallback);
    }
}
