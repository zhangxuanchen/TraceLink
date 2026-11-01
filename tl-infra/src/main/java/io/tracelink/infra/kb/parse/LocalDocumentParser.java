package io.tracelink.infra.kb.parse;

import io.tracelink.domain.kb.parse.DocumentParseException;
import io.tracelink.domain.kb.parse.DocumentParser;
import io.tracelink.domain.kb.parse.ParsedDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * RAGFlow 不可用时的本地解析：只做纯文本抽取与按结构切片，不做向量。
 * 制度按标题与空行分节，切片目标 400 至 600 字、重叠 15%，与 DESIGN 4.7 的切片口径一致；
 * 表格识别与版面还原留给 RAGFlow。
 */
@Component
public class LocalDocumentParser implements DocumentParser {

    private static final int MAX_CHUNKS = 500;
    private static final Pattern HEADING = Pattern.compile("^#+\\s+.*");

    private final int targetSize;
    private final double overlapRatio;

    public LocalDocumentParser(@Value("${tracelink.kb.chunk.target-size:500}") int targetSize,
                               @Value("${tracelink.kb.chunk.overlap-ratio:0.15}") double overlapRatio) {
        this.targetSize = Math.max(200, targetSize);
        this.overlapRatio = Math.min(0.5, Math.max(0, overlapRatio));
    }

    public ParsedDocument parse(Path file, String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return parsePdf(file);
        }
        if (lower.endsWith(".docx")) {
            return parseDocx(file);
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt")) {
            return parseTextFile(file);
        }
        throw new IllegalArgumentException("暂不支持该文件类型：" + fileName + "，可上传 pdf、docx、md、txt");
    }

    private ParsedDocument parsePdf(Path file) {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<ParsedDocument.ParsedChunk> chunks = new ArrayList<>();
            int pages = document.getNumberOfPages();
            for (int page = 1; page <= pages && chunks.size() < MAX_CHUNKS; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalize(stripper.getText(document));
                if (text.isEmpty()) {
                    continue;
                }
                for (String piece : splitSections(text)) {
                    chunks.addAll(chunk(piece, page));
                }
            }
            return result(chunks);
        } catch (IOException e) {
            throw new DocumentParseException("PDF 读取失败：" + e.getMessage());
        }
    }

    private ParsedDocument parseDocx(Path file) {
        try (InputStream in = Files.newInputStream(file); XWPFDocument document = new XWPFDocument(in)) {
            List<String> lines = new ArrayList<>();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText() == null ? "" : paragraph.getText().trim();
                if (text.isEmpty()) {
                    lines.add("");
                    continue;
                }
                String style = paragraph.getStyle();
                if (style != null && (style.toLowerCase().contains("heading") || style.contains("标题"))) {
                    lines.add("");
                    lines.add("# " + text);
                } else {
                    lines.add(text);
                }
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText() == null ? "" : cell.getText().replaceAll("\\s+", " ").trim();
                        if (!cellText.isEmpty()) {
                            cells.add(cellText);
                        }
                    }
                    if (!cells.isEmpty()) {
                        lines.add(String.join(" | ", cells));
                    }
                }
            }
            return fromLines(lines, null);
        } catch (IOException e) {
            throw new DocumentParseException("DOCX 读取失败：" + e.getMessage());
        }
    }

    private ParsedDocument parseTextFile(Path file) {
        try {
            return fromLines(Files.readAllLines(file, StandardCharsets.UTF_8), null);
        } catch (IOException e) {
            throw new DocumentParseException("文本读取失败：" + e.getMessage());
        }
    }

    private ParsedDocument fromLines(List<String> lines, Integer pageNo) {
        StringBuilder block = new StringBuilder();
        List<ParsedDocument.ParsedChunk> chunks = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            boolean heading = HEADING.matcher(trimmed).matches();
            if (heading && block.length() > 0) {
                addSection(chunks, block.toString(), pageNo);
                block.setLength(0);
            }
            if (trimmed.isEmpty()) {
                if (block.length() > 0) {
                    block.append('\n');
                }
                continue;
            }
            block.append(trimmed).append('\n');
        }
        if (block.length() > 0) {
            addSection(chunks, block.toString(), pageNo);
        }
        return result(chunks);
    }

    private void addSection(List<ParsedDocument.ParsedChunk> chunks, String section, Integer pageNo) {
        if (chunks.size() >= MAX_CHUNKS) {
            return;
        }
        for (String piece : splitSections(normalize(section))) {
            chunks.addAll(chunk(piece, pageNo));
        }
    }

    private List<ParsedDocument.ParsedChunk> chunk(String section, Integer pageNo) {
        List<ParsedDocument.ParsedChunk> chunks = new ArrayList<>();
        if (section.isEmpty()) {
            return chunks;
        }
        if (section.length() <= targetSize + targetSize / 5) {
            chunks.add(new ParsedDocument.ParsedChunk(section, pageNo));
            return chunks;
        }
        int step = Math.max(1, targetSize - (int) Math.round(targetSize * overlapRatio));
        int start = 0;
        while (start < section.length() && chunks.size() < MAX_CHUNKS) {
            int end = Math.min(section.length(), start + targetSize);
            if (end < section.length()) {
                int boundary = boundary(section, start, end);
                if (boundary > start + targetSize / 2) {
                    end = boundary;
                }
            }
            String piece = section.substring(start, end).trim();
            if (piece.length() >= 10) {
                chunks.add(new ParsedDocument.ParsedChunk(piece, pageNo));
            }
            if (end >= section.length()) {
                break;
            }
            start = Math.max(start + 1, end - (int) Math.round(targetSize * overlapRatio));
        }
        return chunks;
    }

    /** 在窗口尾部找最近的句读，避免把一句话切断 */
    private int boundary(String section, int start, int end) {
        int floor = Math.max(start + targetSize / 2, end - targetSize / 5);
        for (int i = end - 1; i >= floor; i--) {
            char c = section.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n' || c == '.' || c == '!' || c == '?') {
                return i + 1;
            }
        }
        return end;
    }

    private List<String> splitSections(String text) {
        String[] parts = text.split("\\n\\s*\\n");
        List<String> sections = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                sections.add(part.trim());
            }
        }
        return sections;
    }

    private String normalize(String text) {
        return text.replace("\r", "")
                .replaceAll("[ \\t\\u3000]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private ParsedDocument result(List<ParsedDocument.ParsedChunk> chunks) {
        if (chunks.isEmpty()) {
            return new ParsedDocument(List.of(), "未提取到文本，请确认文件不是扫描件或空文档");
        }
        if (chunks.size() > MAX_CHUNKS) {
            return new ParsedDocument(chunks.subList(0, MAX_CHUNKS), "切片过多，已截断到 " + MAX_CHUNKS + " 片");
        }
        return new ParsedDocument(chunks, null);
    }
}
