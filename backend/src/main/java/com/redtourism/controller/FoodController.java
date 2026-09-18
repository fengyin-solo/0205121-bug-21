package com.redtourism.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.redtourism.common.LangUtils;
import com.redtourism.common.Result;
import com.redtourism.entity.Food;
import com.redtourism.entity.FoodStore;
import com.redtourism.service.FoodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/food")
public class FoodController {

    @Autowired
    private FoodService foodService;

    @GetMapping("/list")
    public Result<IPage<Food>> list(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String orderBy,
                                     @RequestParam(required = false, defaultValue = "zh") String lang) {
        IPage<Food> result = foodService.listFoods(page, size, category, keyword, orderBy);
        result.getRecords().forEach(f -> applyLang(f, lang));
        return Result.success(result);
    }

    @GetMapping("/detail")
    public Result<Food> detail(@RequestParam Long id,
                               @RequestParam(required = false, defaultValue = "zh") String lang) {
        Food food = foodService.getDetail(id);
        if (food != null) applyLang(food, lang);
        return Result.success(food);
    }

    @GetMapping("/stores")
    public Result<List<FoodStore>> stores(@RequestParam(required = false) String keyword) {
        return Result.success(foodService.listStores(keyword));
    }

    @GetMapping("/storeDetail")
    public Result<FoodStore> storeDetail(@RequestParam Long id) {
        return Result.success(foodService.getStoreDetail(id));
    }

    @GetMapping("/categories")
    public Result<List<String>> categories() {
        List<String> categories = java.util.Arrays.asList(
                "酸汤系列", "辣子系列", "烧烤", "米粉面食", "小吃", "特色火锅", "民族菜");
        return Result.success(categories);
    }

    /**
     * 按请求语言回填美食名称与简介；译文缺失回退中文并标记 langFallback。
     */
    public static void applyLang(Food f, String lang) {
        if (f == null) return;
        if (LangUtils.isZh(lang)) { f.setLangFallback(false); return; }
        boolean en = LangUtils.EN.equals(LangUtils.normalize(lang));
        boolean[] fb = new boolean[1];
        boolean fallback = false;
        f.setName(LangUtils.pick(lang, f.getName(), en ? f.getNameEn() : f.getNameJa(), fb));
        fallback |= fb[0];
        f.setDescription(LangUtils.pick(lang, f.getDescription(), en ? f.getDescriptionEn() : f.getDescriptionJa(), fb));
        fallback |= fb[0];
        f.setLangFallback(fallback);
    }
}
