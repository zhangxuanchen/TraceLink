package io.tracelink.infra.exporting.writer;

import io.tracelink.common.error.BizException;
import io.tracelink.common.error.ErrorCode;
import io.tracelink.domain.exporting.model.ExportSheet;
import io.tracelink.domain.exporting.model.ExportWorkbook;
import io.tracelink.domain.exporting.port.WorkbookWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Component
public class PoiWorkbookWriter implements WorkbookWriter {

    private static final int MAX_NAME = 28;

    @Override
    public byte[] write(ExportWorkbook workbook) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setWrapText(true);

            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle bodyStyle = wb.createCellStyle();
            bodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            bodyStyle.setWrapText(true);
            bodyStyle.setBorderBottom(BorderStyle.THIN);
            bodyStyle.setBorderTop(BorderStyle.THIN);
            bodyStyle.setBorderLeft(BorderStyle.THIN);
            bodyStyle.setBorderRight(BorderStyle.THIN);

            int index = 0;
            for (ExportSheet source : workbook.sheets) {
                Sheet sheet = wb.createSheet(safeName(source.name, index++));
                int rowIndex = 0;
                if (source.title != null) {
                    Row row = sheet.createRow(rowIndex++);
                    writeCell(row, 0, source.title, titleStyle);
                    int span = Math.max(1, source.headers.size());
                    sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, span - 1));
                    row.setHeightInPoints(28);
                }
                rowIndex = writeRows(sheet, rowIndex, source.prefixRows, bodyStyle);
                if (!source.headers.isEmpty()) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int c = 0; c < source.headers.size(); c++) {
                        writeCell(row, c, source.headers.get(c), source.boldHeader ? headerStyle : bodyStyle);
                    }
                }
                for (List<String> data : source.rows) {
                    Row row = sheet.createRow(rowIndex++);
                    for (int c = 0; c < data.size(); c++) {
                        writeCell(row, c, data.get(c), bodyStyle);
                    }
                }
                rowIndex = writeRows(sheet, rowIndex, source.footerRows, bodyStyle);
                applyWidths(sheet, source);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "导出文件生成失败：" + e.getMessage());
        }
    }

    private int writeRows(Sheet sheet, int start, List<List<String>> rows, CellStyle style) {
        int rowIndex = start;
        for (List<String> values : rows) {
            Row row = sheet.createRow(rowIndex++);
            for (int c = 0; c < values.size(); c++) {
                writeCell(row, c, values.get(c), style);
            }
        }
        return rowIndex;
    }

    private void writeCell(Row row, int col, String value, CellStyle style) {
        if (value == null) {
            return;
        }
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void applyWidths(Sheet sheet, ExportSheet source) {
        int cols = Math.max(source.headers.size(), 10);
        for (int c = 0; c < cols; c++) {
            int width = 16;
            if (source.columnWidths != null && c < source.columnWidths.length) {
                width = source.columnWidths[c];
            } else if (c == 1) {
                width = 34;
            }
            sheet.setColumnWidth(c, width * 256);
        }
    }

    private String safeName(String name, int index) {
        String base = name == null || name.isBlank() ? "Sheet" + (index + 1) : name;
        base = base.replaceAll("[\\\\/:*?\\[\\]<>]", "_");
        return base.length() > MAX_NAME ? base.substring(0, MAX_NAME) : base;
    }
}
