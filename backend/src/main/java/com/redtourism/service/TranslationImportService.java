package com.redtourism.service;

/**
 * 管理端译文批量导入服务。
 */
public interface TranslationImportService {

    /** 导入结果统计。 */
    class ImportResult {
        private String targetType;
        private String lang;
        private int totalRows;
        private int updatedRows;
        private int skippedRows;
        private String message;

        public String getTargetType() { return targetType; }
        public void setTargetType(String targetType) { this.targetType = targetType; }
        public String getLang() { return lang; }
        public void setLang(String lang) { this.lang = lang; }
        public int getTotalRows() { return totalRows; }
        public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
        public int getUpdatedRows() { return updatedRows; }
        public void setUpdatedRows(int updatedRows) { this.updatedRows = updatedRows; }
        public int getSkippedRows() { return skippedRows; }
        public void setSkippedRows(int skippedRows) { this.skippedRows = skippedRows; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /**
     * 整份导入某类型某语言的译文。整体在一个事务内完成，失败回滚。
     *
     * @param targetType spot / route / culture / food
     * @param lang       en / ja
     * @param csvBytes   UTF-8 编码的 CSV 文件字节
     */
    ImportResult importCsv(String targetType, String lang, byte[] csvBytes) throws Exception;

    /** 依据当前中文数据生成 CSV 模板（含表头与源语言列）。 */
    String buildTemplate(String targetType, String lang) throws Exception;
}
