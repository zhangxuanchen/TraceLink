-- M4 知识库：文档、切片快照与问答权限点
-- 与 docs/DESIGN.md 4.7 与 6.2 对齐。解析与检索的真相源在 RAGFlow，
-- kb_chunk 存切片正文快照，供引用展示与本地全文降级检索。

CREATE TABLE kb_document (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  title           VARCHAR(255) NOT NULL,
  domain          VARCHAR(32)  NOT NULL COMMENT 'HISTORY POLICY PROCESS CUSTOMER MEETING',
  dept_id         BIGINT       NULL COMMENT '归属部门 空表示全员可见',
  dept_code       VARCHAR(32)  NULL COMMENT '同步到 RAGFlow 的部门标签 空表示全员可见',
  visibility      VARCHAR(16)  NOT NULL DEFAULT 'ALL' COMMENT 'ALL DEPT DEPT_TREE',
  file_key        VARCHAR(255) NOT NULL COMMENT '原件在文件卷的相对路径',
  file_hash       CHAR(64)     NOT NULL,
  source_type     VARCHAR(16)  NOT NULL DEFAULT 'UPLOAD' COMMENT 'UPLOAD TASK_PAIR',
  source_ref      VARCHAR(128) NULL COMMENT '沉淀来源 工单号或批次号',
  dataset_id      VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT 'RAGFlow 知识库标识',
  external_doc_id VARCHAR(64)  NULL COMMENT 'RAGFlow 文档标识',
  parser_id       VARCHAR(64)  NULL COMMENT 'RAGFlow 切片模板标识',
  embedding_model VARCHAR(64)  NULL COMMENT '解析时使用的向量模型 换代对照用',
  chunk_cnt       INT          NOT NULL DEFAULT 0,
  sync_status     VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING PARSING READY FAILED',
  sync_msg        VARCHAR(500) NULL,
  synced_at       DATETIME     NULL,
  created_by      BIGINT       NOT NULL,
  deleted         TINYINT      NOT NULL DEFAULT 0,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_doc_hash (file_hash),
  KEY idx_doc_domain_dept (domain, dept_id),
  KEY idx_doc_sync (sync_status),
  KEY idx_doc_ext (dataset_id, external_doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档';

CREATE TABLE kb_chunk (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  doc_id            BIGINT      NOT NULL,
  seq               INT         NOT NULL,
  external_chunk_id VARCHAR(64) NOT NULL COMMENT 'RAGFlow 切片标识 引用回溯用',
  content           TEXT        NOT NULL COMMENT '正文快照 引用展示与降级检索用',
  page_no           INT         NULL,
  token_cnt         INT         NULL,
  meta              JSON        NULL COMMENT '知识域 部门 来源工单',
  superseded        TINYINT     NOT NULL DEFAULT 0 COMMENT '1 表示被重新解析替换 仍可按 ID 回溯原文',
  created_at        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FULLTEXT KEY ft_chunk (content) WITH PARSER ngram,
  UNIQUE KEY uk_chunk_ext (doc_id, external_chunk_id),
  KEY idx_chunk_doc (doc_id, superseded)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识切片快照 检索只取 superseded=0';

INSERT INTO permission (code, name, module) VALUES ('kb:ask', '知识问答', 'KB');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code = 'kb:ask';
