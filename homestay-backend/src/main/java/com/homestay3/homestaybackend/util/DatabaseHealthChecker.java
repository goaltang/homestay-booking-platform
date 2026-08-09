package com.homestay3.homestaybackend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 数据库健康检查器
 * 在应用启动时检查数据库连接状态和表结构
 */
@Component
public class DatabaseHealthChecker implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseHealthChecker.class);
    
    @Autowired
    private DataSource dataSource;
    
    @Override
    public void run(String... args) throws Exception {
        checkDatabaseConnection();
        checkRequiredTables();
    }
    
    /**
     * 检查数据库连接
     */
    private void checkDatabaseConnection() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            logger.info("数据库连接成功:");
            logger.info("- 数据库产品: {}", metaData.getDatabaseProductName());
            logger.info("- 数据库版本: {}", metaData.getDatabaseProductVersion());
            logger.info("- 驱动名称: {}", metaData.getDriverName());
            logger.info("- 驱动版本: {}", metaData.getDriverVersion());
            logger.info("- 连接URL: {}", metaData.getURL());
            logger.info("- 用户名: {}", metaData.getUserName());
            
        } catch (SQLException e) {
            logger.error("数据库连接失败: {}", e.getMessage(), e);
            logger.error("请检查:");
            logger.error("1. MySQL服务是否启动");
            logger.error("2. 数据库 homestay_db 是否存在");
            logger.error("3. 用户名密码是否正确");
            logger.error("4. 网络连接是否正常");
        }
    }
    
    /**
     * 检查必需的表是否存在
     */
    private void checkRequiredTables() {
        String[] requiredTables = {
            "homestays", "users", "amenities", "homestay_amenity", 
            "homestay_types", "homestay_audit_logs", "orders"
        };
        
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            logger.info("检查数据库表结构:");
            for (String tableName : requiredTables) {
                try (ResultSet tables = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
                    if (tables.next()) {
                        logger.info("✓ 表 {} 存在", tableName);
                        
                        // 检查表的列数
                        try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
                            int columnCount = 0;
                            while (columns.next()) {
                                columnCount++;
                            }
                            logger.debug("  - 列数: {}", columnCount);
                        }
                    } else {
                        logger.warn("✗ 表 {} 不存在", tableName);
                    }
                }
            }
            
        } catch (SQLException e) {
            logger.error("检查数据库表结构时发生错误: {}", e.getMessage(), e);
        }
    }
} 