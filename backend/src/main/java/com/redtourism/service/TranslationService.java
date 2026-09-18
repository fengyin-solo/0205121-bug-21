package com.redtourism.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 译文批量导入服务。
 * 整批导入在单个事务内完成：先解析校验、再落库，任何一步失败整体回滚。
 */
public interface TranslationService {

    /**
     * 导入 CSV 译文。
     *
     * @param module 模块：spot / route / food / culture
     * @param lang   目标语言：en / ja
     * @param file   UTF-8 编码的 CSV 文件
     * @return 导入结果摘要（updated / blankCells 等）
     */
    Map<String, Object> importTranslations(String module, String lang, MultipartFile file) throws Exception;
}
