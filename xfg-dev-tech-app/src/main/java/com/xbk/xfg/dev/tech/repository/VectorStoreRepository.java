package com.xbk.xfg.dev.tech.repository;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 向量存储仓储接口
 * 类似于 MyBatis 的 Mapper 接口，封装所有向量数据库的操作
 *
 * <h2>DDD 架构中的 Repository</h2>
 * - Repository 是领域层和基础设施层的桥梁
 * - 提供领域对象的持久化和检索接口
 * - 隔离数据访问细节，使领域服务不直接依赖数据库
 *
 * @author xiexu
 */
public interface VectorStoreRepository {

    /**
     * 根据知识库标签删除向量数据
     * 对应 SQL: DELETE FROM vector_store WHERE metadata->>'knowledge' = ?
     *
     * @param ragTag 知识库标签
     * @return 删除的行数
     */
    int deleteByRagTag(String ragTag);

    /**
     * 根据知识库标签统计向量数量
     * 对应 SQL: SELECT COUNT(*) FROM vector_store WHERE metadata->>'knowledge' = ?
     *
     * @param ragTag 知识库标签
     * @return 向量数量
     */
    long countByRagTag(String ragTag);

    /**
     * 查询所有不重复的知识库标签
     * 对应 SQL: SELECT DISTINCT metadata->>'knowledge' FROM vector_store
     *
     * @return 知识库标签列表
     */
    List<String> findAllRagTags();

    /**
     * 根据知识库标签查询所有文档
     * 对应 SQL: SELECT * FROM vector_store WHERE metadata->>'knowledge' = ?
     *
     * @param ragTag 知识库标签
     * @return 文档列表
     */
    List<Document> findDocumentsByRagTag(String ragTag);

    /**
     * 批量保存文档（带向量）
     * 对应 SQL: INSERT INTO vector_store (content, metadata, embedding) VALUES (?, ?, ?)
     *
     * @param documents 文档列表
     * @return 保存成功的数量
     */
    int batchSave(List<Document> documents);

    /**
     * 清空所有向量数据（危险操作，谨慎使用）
     * 对应 SQL: TRUNCATE TABLE vector_store
     *
     * @return 是否成功
     */
    boolean truncate();
}
