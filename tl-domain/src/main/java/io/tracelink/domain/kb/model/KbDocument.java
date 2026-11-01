package io.tracelink.domain.kb.model;

import java.time.LocalDateTime;

/**
 * 知识文档。file_hash 唯一，同一文件重复上传返回原文档；
 * external_doc_id 与 dataset_id 指向 RAGFlow，解析成功后把切片正文回写 kb_chunk。
 */
public class KbDocument {

    public Long id;
    public String title;
    public KbDomain domain;
    public Long deptId;
    public String deptCode;
    public KbVisibility visibility;
    public String fileKey;
    public String fileHash;
    public String sourceType;
    public String sourceRef;
    public String datasetId;
    public String externalDocId;
    public String parserId;
    public String embeddingModel;
    public Integer chunkCnt;
    public KbSyncStatus syncStatus;
    public String syncMsg;
    public LocalDateTime syncedAt;
    public Long createdBy;
    public Boolean deleted;
    public LocalDateTime createdAt;
}
