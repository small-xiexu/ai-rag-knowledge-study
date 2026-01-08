package com.xbk.xfg.dev.tech.domain.repository.impl;

import com.xbk.xfg.dev.tech.domain.repository.VectorStoreRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 向量存储仓储实现
 * 封装所有与 PostgreSQL (pgvector) 相关的 SQL 操作
 *
 * <h2>为什么需要 Repository？</h2>
 * 1. SQL 集中管理 - 所有 SQL 都在这个类中，便于维护
 * 2. 职责分离 - Service 只关心业务逻辑，不关心 SQL 细节
 * 3. 易于测试 - 可以 Mock Repository，不需要真实数据库
 * 4. 符合 DDD - Repository 是领域层和基础设施层的桥梁
 *
 * <h2>SQL 命名规范</h2>
 * - 查询：SELECT_XXX
 * - 删除：DELETE_XXX
 * - 插入：INSERT_XXX
 * - 统计：COUNT_XXX
 *
 * @author xiexu
 */
@Slf4j
@Repository
public class VectorStoreRepositoryImpl implements VectorStoreRepository {

    @Resource
    private JdbcTemplate jdbcTemplate;

    // ==================== SQL 常量定义 ====================

    /**
     * 根据知识库标签删除向量数据
     * JSONB 操作符说明：
     * - ->  : 获取 JSON 对象字段（返回 JSONB）
     * - ->> : 获取 JSON 对象字段（返回 TEXT）
     */
    private static final String DELETE_BY_RAG_TAG =
            "DELETE FROM vector_store WHERE metadata->>'knowledge' = ?";

    /**
     * 根据知识库标签统计向量数量
     */
    private static final String COUNT_BY_RAG_TAG =
            "SELECT COUNT(*) FROM vector_store WHERE metadata->>'knowledge' = ?";

    /**
     * 查询所有不重复的知识库标签
     * DISTINCT 去重
     */
    private static final String SELECT_ALL_RAG_TAGS =
            "SELECT DISTINCT metadata->>'knowledge' AS rag_tag " +
            "FROM vector_store " +
            "WHERE metadata->>'knowledge' IS NOT NULL " +
            "ORDER BY rag_tag";

    /**
     * 根据知识库标签查询所有文档（不含向量）
     * 注意：embedding 字段很大（1536维），通常不需要查询出来
     */
    private static final String SELECT_DOCUMENTS_BY_RAG_TAG =
            "SELECT id, content, metadata " +
            "FROM vector_store " +
            "WHERE metadata->>'knowledge' = ?";

    /**
     * 清空表（危险操作）
     */
    private static final String TRUNCATE_TABLE =
            "TRUNCATE TABLE vector_store";

    /**
     * 统计总向量条目
     */
    private static final String COUNT_ALL =
            "SELECT COUNT(*) FROM vector_store";

    // ==================== 实现方法 ====================

    @Override
    public int deleteByRagTag(String ragTag) {
        try {
            int deletedRows = jdbcTemplate.update(DELETE_BY_RAG_TAG, ragTag);
            log.info("【Repository】删除知识库 '{}' 的向量数据，共 {} 条", ragTag, deletedRows);
            return deletedRows;
        } catch (Exception e) {
            log.error("【Repository】删除知识库 '{}' 失败", ragTag, e);
            throw new RuntimeException("删除向量数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public long countByRagTag(String ragTag) {
        try {
            return jdbcTemplate.queryForObject(COUNT_BY_RAG_TAG, Long.class, ragTag);
        } catch (Exception e) {
            log.error("【Repository】统计知识库 '{}' 向量数量失败", ragTag, e);
            return 0L;
        }
    }

    @Override
    public List<String> findAllRagTags() {
        try {
            List<String> ragTags = jdbcTemplate.queryForList(SELECT_ALL_RAG_TAGS, String.class);
            log.info("【Repository】查询到 {} 个知识库标签", ragTags.size());
            return ragTags;
        } catch (Exception e) {
            log.error("【Repository】查询知识库标签列表失败", e);
            return List.of();
        }
    }

    @Override
    public List<Document> findDocumentsByRagTag(String ragTag) {
        try {
            // TODO: 如果需要实现，需要手动映射结果集到 Document 对象
            throw new UnsupportedOperationException("暂未实现，请使用 PgVectorStore.similaritySearch()");
        } catch (Exception e) {
            log.error("【Repository】查询知识库 '{}' 的文档失败", ragTag, e);
            return List.of();
        }
    }

    @Override
    public int batchSave(List<Document> documents) {
        // TODO: 如果需要实现，建议使用 PgVectorStore.accept() 方法
        // 因为它会自动处理向量化（Embedding）
        throw new UnsupportedOperationException("请使用 PgVectorStore.accept() 保存文档");
    }

    @Override
    public boolean truncate() {
        try {
            jdbcTemplate.execute(TRUNCATE_TABLE);
            log.warn("【Repository】已清空 vector_store 表（危险操作）");
            return true;
        } catch (Exception e) {
            log.error("【Repository】清空表失败", e);
            return false;
        }
    }

    @Override
    public long countAll() {
        try {
            return jdbcTemplate.queryForObject(COUNT_ALL, Long.class);
        } catch (Exception e) {
            log.error("【Repository】统计向量总数失败", e);
            return 0L;
        }
    }
}
