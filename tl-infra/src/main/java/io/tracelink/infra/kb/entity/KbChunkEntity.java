package io.tracelink.infra.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("kb_chunk")
public class KbChunkEntity {

    @TableId(type = IdType.AUTO)
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
