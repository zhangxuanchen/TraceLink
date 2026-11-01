package io.tracelink.infra.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.tracelink.infra.kb.entity.KbChunkEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface KbChunkMapper extends BaseMapper<KbChunkEntity> {

    /** ngram 全文检索，只取有效切片；score 为原始相关度，归一在仓储层完成 */
    @Select("""
            <script>
            SELECT c.id, c.doc_id, c.seq, c.external_chunk_id, c.content, c.page_no, c.token_cnt, c.meta,
                   MATCH(c.content) AGAINST(#{q} IN NATURAL LANGUAGE MODE) AS score
            FROM kb_chunk c
            WHERE c.superseded = 0
              AND MATCH(c.content) AGAINST(#{q} IN NATURAL LANGUAGE MODE) > 0
              AND c.doc_id IN
              <foreach collection="docIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY score DESC
            LIMIT #{limit}
            </script>
            """)
    List<KbChunkScoreRow> searchFulltext(@Param("docIds") List<Long> docIds,
                                        @Param("q") String question,
                                        @Param("limit") int limit);

    /** 全文索引不可用或未命中时的兜底：按关键词逐个子串匹配 */
    @Select("""
            <script>
            SELECT c.id, c.doc_id, c.seq, c.external_chunk_id, c.content, c.page_no, c.token_cnt, c.meta,
                   0.05 AS score
            FROM kb_chunk c
            WHERE c.superseded = 0
              AND c.doc_id IN
              <foreach collection="docIds" item="id" open="(" separator="," close=")">#{id}</foreach>
              AND
              <foreach collection="terms" item="t" separator=" OR ">
                c.content LIKE CONCAT('%', #{t}, '%')
              </foreach>
            ORDER BY c.doc_id, c.seq
            LIMIT #{limit}
            </script>
            """)
    List<KbChunkScoreRow> searchLike(@Param("docIds") List<Long> docIds,
                                     @Param("terms") List<String> terms,
                                     @Param("limit") int limit);
}
