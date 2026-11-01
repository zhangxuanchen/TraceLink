package io.tracelink.infra.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.tracelink.domain.kb.model.KbDomain;
import io.tracelink.domain.kb.model.KbSyncStatus;
import io.tracelink.domain.kb.model.KbVisibility;

import java.time.LocalDateTime;

@TableName("kb_document")
public class KbDocumentEntity {

    @TableId(type = IdType.AUTO)
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
