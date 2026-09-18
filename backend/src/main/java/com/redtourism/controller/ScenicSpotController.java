package com.redtourism.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.redtourism.common.I18nUtil;
import com.redtourism.common.Result;
import com.redtourism.entity.ScenicSpot;
import com.redtourism.entity.ScenicSpotImage;
import com.redtourism.service.ScenicSpotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
                                      @RequestParam(required = false, defaultValue = "zh") String lang) {
        ScenicSpot spot = spotService.getDetail(id);
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
     * 统一多语言口径：译文非空则覆盖中文字段；译文缺失则保留中文并记录回退标记，
     * 由前端在对应字段上标注“中文”。列表、详情共用同一套规则。
     */
    static void applyLang(ScenicSpot s, String lang) {
        I18nUtil.applyFields(s, lang,
                new String[]{"name", "nameEn", "nameJa"},
                new String[]{"description", "descriptionEn", "descriptionJa"},
                new String[]{"ticketReservation", "ticketReservationEn", "ticketReservationJa"},
                new String[]{"suggestedDuration", "suggestedDurationEn", "suggestedDurationJa"},
                new String[]{"itemsToBring", "itemsToBringEn", "itemsToBringJa"});
    }
}
