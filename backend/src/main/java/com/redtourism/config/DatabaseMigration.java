package com.redtourism.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据库结构幂等迁移：
 * MySQL 官方镜像只在数据卷首次创建时执行 /docker-entrypoint-initdb.d 下的脚本，
 * 老环境升级后缺少的列需要在应用启动时补齐。
 */
@Slf4j
@Component
public class DatabaseMigration implements ApplicationRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfMissing("food", "name_en", "VARCHAR(200)");
        addColumnIfMissing("food", "name_ja", "VARCHAR(200)");
        addColumnIfMissing("food", "description_en", "TEXT");
        addColumnIfMissing("food", "description_ja", "TEXT");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        try {
            List<Long> exists = jdbcTemplate.queryForList(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                    Long.class, table, column);
            long count = exists.isEmpty() ? 0L : exists.get(0);
            if (count == 0) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                log.info("数据库迁移：{}.{} 列已添加", table, column);
            }
        } catch (Exception e) {
            log.error("数据库迁移失败：{}.{} - {}", table, column, e.getMessage(), e);
        }
    }
}
