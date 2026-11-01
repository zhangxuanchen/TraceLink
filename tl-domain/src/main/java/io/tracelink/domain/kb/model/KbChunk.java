package io.tracelink.domain.kb.model;

import java.time.LocalDateTime;

/**
 * 切片正文快照。superseded 为真表示被重新解析替换，检索不再取用，
 * 但按 ID 仍能取到原文，保证历史引用可回溯。
 */
public class KbChunk {

    public Long id;
    public Long docId;
    public Integer seq;
    public String externalChunkId;
    public String content;
    public Integer pageNo;
    public Integer tokenCnt;
    public String meta;
    public Boolean superseded;
    public LocalDateTime createdAt;
}
