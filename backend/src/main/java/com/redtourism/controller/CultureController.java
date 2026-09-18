package com.redtourism.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.redtourism.common.I18nUtil;
import com.redtourism.common.Result;
import com.redtourism.entity.CultureCategory;
import com.redtourism.entity.CultureContent;
import com.redtourism.service.CultureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/culture")
public class CultureController {

    @Autowired
    private CultureService cultureService;

    @GetMapping("/list")
    public Result<IPage<CultureContent>> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) Long categoryId,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false, defaultValue = "zh") String lang) {
        IPage<CultureContent> result = cultureService.listContents(page, size, categoryId, keyword);
        result.getRecords().forEach(c -> applyLang(c, lang));
        return Result.success(result);
    }

    @GetMapping("/detail")
    public Result<CultureContent> detail(@RequestParam Long id,
                                          @RequestParam(required = false, defaultValue = "zh") String lang) {
        CultureContent c = cultureService.getDetail(id);
        if (c != null) applyLang(c, lang);
        return Result.success(c);
    }

    @GetMapping("/categories")
    public Result<List<CultureCategory>> categories() {
        return Result.success(cultureService.listCategories());
    }

    @GetMapping("/categoriesByParent")
    public Result<List<CultureCategory>> categoriesByParent(@RequestParam Long parentId) {
        return Result.success(cultureService.listCategoriesByParent(parentId));
    }

    /** 统一多语言口径，与景点/线路/美食一致：缺译文回退中文并标记。 */
    static void applyLang(CultureContent c, String lang) {
        I18nUtil.applyFields(c, lang,
                new String[]{"title", "titleEn", "titleJa"},
                new String[]{"content", "contentEn", "contentJa"});
    }
}
