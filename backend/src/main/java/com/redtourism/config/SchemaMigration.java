package com.redtourism.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 轻量数据库结构迁移：
 * schema.sql 只在数据库首次初始化时执行，对于已存在的卷（mysql-data volume），
 * 后续新增的多语言列不会自动创建。这里在启动时检查并补齐缺失列。
 */
@Slf4j
@Component
public class SchemaMigration implements ApplicationRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // food 表新增的多语言列
        Map<String, String> foodColumns = new LinkedHashMap<>();
        foodColumns.put("name_en", "ALTER TABLE food ADD COLUMN name_en VARCHAR(200) AFTER name");
        foodColumns.put("name_ja", "ALTER TABLE food ADD COLUMN name_ja VARCHAR(200) AFTER name_en");
        foodColumns.put("description_en", "ALTER TABLE food ADD COLUMN description_en TEXT AFTER description");
        foodColumns.put("description_ja", "ALTER TABLE food ADD COLUMN description_ja TEXT AFTER description_en");
        addMissingColumns("food", foodColumns);
    }

    private void addMissingColumns(String table, Map<String, String> alterSql) {
        try (java.sql.Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            Set<String> existing = new HashSet<>();
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(conn.getCatalog(), null, table, null)) {
                while (rs.next()) {
                    existing.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
            }
            alterSql.forEach((column, sql) -> {
                if (!existing.contains(column.toLowerCase())) {
                    jdbcTemplate.execute(sql);
                    log.info("迁移：表 {} 新增列 {}", table, column);
                }
            });
        } catch (Exception e) {
            log.warn("数据库迁移检查失败（可忽略，若是首次初始化）: {}", e.getMessage());
        }
    }
}
