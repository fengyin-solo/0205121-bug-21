package com.redtourism.controller;

import com.redtourism.common.Constants;
import com.redtourism.common.I18nUtil;
import com.redtourism.common.Result;
import com.redtourism.entity.User;
import com.redtourism.service.TranslationImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 管理端多语言译文批量上传。
 *
 * 约束：
 * 1. CSV 按列头 id 对齐数据，与文件中的行顺序无关，避免译文错位；
 * 2. 上传为“整份快照”语义：同一类型 + 语言再次上传将整体覆盖上一次结果，
 *    先在单个事务内清空该语言的全部目标列，再逐行写入，中途失败整体回滚，不留半份；
 * 3. 仅 ADMIN 可操作（见 SecurityConfig 中 /api/admin/** 规则）。
 */
@RestController
@RequestMapping("/api/admin/translation")
public class TranslationController {

    @Autowired
    private TranslationImportService translationImportService;

    @PostMapping("/upload")
    public Result<TranslationImportService.ImportResult> upload(@RequestParam("file") MultipartFile file,
                                                                @RequestParam String targetType,
                                                                @RequestParam String lang,
                                                                HttpSession session) {
        User operator = (User) session.getAttribute(Constants.SESSION_USER);
        if (operator == null) return Result.error(401, "请先登录");
        if (!Constants.ROLE_ADMIN.equals(operator.getRole())) {
            return Result.error(403, "仅管理员可上传译文");
        }
        String lng = I18nUtil.normalize(lang);
        if (I18nUtil.ZH.equals(lng)) {
            return Result.error("中文为源语言，无需上传译文");
        }
        if (file == null || file.isEmpty()) {
            return Result.error("请选择要上传的译文文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return Result.error("仅支持 CSV 格式译文文件");
        }
        try {
            TranslationImportService.ImportResult result =
                    translationImportService.importCsv(targetType, lng, file.getBytes());
            return Result.success("译文上传成功", result);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("译文上传失败，已回滚，未保留任何数据：" + e.getMessage());
        }
    }

    /** 下载列头模板（按现有中文数据生成，管理员把译文填入对应列即可）。 */
    @GetMapping("/template")
    public void template(@RequestParam String targetType,
                          @RequestParam String lang,
                          HttpServletResponse response,
                          HttpSession session) throws Exception {
        User operator = (User) session.getAttribute(Constants.SESSION_USER);
        if (operator == null) {
            response.sendError(401, "请先登录");
            return;
        }
        String lng = I18nUtil.normalize(lang);
        if (I18nUtil.ZH.equals(lng)) {
            response.sendError(400, "中文为源语言，无需译文模板");
            return;
        }
        String csv = translationImportService.buildTemplate(targetType, lng);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + targetType + "-" + lng + "-template.csv");
        PrintWriter writer = response.getWriter();
        writer.write('﻿'); // UTF-8 BOM，便于 Excel 正确识别中文
        writer.write(csv);
        writer.flush();
    }
}
