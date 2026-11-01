package io.tracelink.domain.importing.port;

import java.util.ArrayList;
import java.util.List;

/** 明细行原始内容。续行已按「序号为空即上一条」的规则合并过 */
public class ParsedRow {

    public int rowNo;
    public Integer seq;
    public String content;
    public String timeText;
    public String ownerText;
    public String assigneeText;
    public String participantsText;
    public String problemText;
    public String solutionText;
    public String resultText;
    public String remark;
    public List<String> rawCells = new ArrayList<>();
    /** 行指纹：工作表名 + 序号 + 正文摘要的哈希，用于二次导入去重 */
    public String fingerprint;

    public boolean hasContent() {
        return content != null && !content.isBlank();
    }
}
