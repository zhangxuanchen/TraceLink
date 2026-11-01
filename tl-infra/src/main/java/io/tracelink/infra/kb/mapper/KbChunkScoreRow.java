package io.tracelink.infra.kb.mapper;

    /** 检索行：切片字段加相关度 */
public class KbChunkScoreRow {
    public Long id;
    public Long docId;
    public Integer seq;
    public String externalChunkId;
    public String content;
    public Integer pageNo;
    public Integer tokenCnt;
    public String meta;
    public Double score;
}
