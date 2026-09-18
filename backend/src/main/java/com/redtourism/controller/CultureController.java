package com.redtourism.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.redtourism.common.LangUtils;
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
                                          @RequestParam(required = false, defaultValue = "zh") String lang,
                                          @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        // refresh=false（如切换语言重新拉取）不累加浏览量
        CultureContent c = refresh ? cultureService.getDetail(id) : cultureService.getById(id);
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

    private void applyLang(CultureContent c, String lang) {
        if (c == null) return;
        if (LangUtils.isZh(lang)) { c.setLangFallback(false); return; }
        boolean en = LangUtils.EN.equals(LangUtils.normalize(lang));
        boolean[] fb = new boolean[1];
        boolean fallback = false;
        c.setTitle(LangUtils.pick(lang, c.getTitle(), en ? c.getTitleEn() : c.getTitleJa(), fb));
        fallback |= fb[0];
        c.setContent(LangUtils.pick(lang, c.getContent(), en ? c.getContentEn() : c.getContentJa(), fb));
        fallback |= fb[0];
        c.setLangFallback(fallback);
    }
}
