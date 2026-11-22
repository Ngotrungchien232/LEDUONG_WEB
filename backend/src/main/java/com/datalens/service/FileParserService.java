package com.datalens.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

@Service
public class FileParserService {

    private static final Logger log = LoggerFactory.getLogger(FileParserService.class);
    private final Gson gson = new Gson();

    @Autowired
    @Lazy
    private GeminiService geminiService;

    public static class ParseResult {
        public List<String> columns = new ArrayList<>();
        public List<Map<String, Object>> rows = new ArrayList<>();
        public String sheetName;
        public String fileType;
    }

    public ParseResult parse(MultipartFile file) throws Exception {
        return parse(file, null);
    }

    public ParseResult parse(MultipartFile file, String apiKey) throws Exception {
        return parse(file, apiKey, null);
    }

    public ParseResult parse(MultipartFile file, String apiKey, String requestedSheet) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename == null) throw new IllegalArgumentException("File name is null");

        String lower = filename.toLowerCase();
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseExcel(file, requestedSheet);
        } else if (lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".txt")) {
            return parseCsv(file);
        } else if (lower.endsWith(".json")) {
            return parseJson(file);
        } else if (lower.endsWith(".docx")) {
            return parseDocx(file);
        } else if (lower.endsWith(".pdf")) {
            return parsePdf(file, apiKey);
        } else {
            throw new IllegalArgumentException("Định dạng tệp không được hỗ trợ: " + filename + ". Vui lòng sử dụng .xlsx, .xls, .csv, .json, .docx hoặc .pdf");
        }
    }

    private ParseResult parseDocx(MultipartFile file) throws Exception {
        ParseResult result = new ParseResult();
        result.fileType = "WORD";
        result.sheetName = "Table1";
        try (InputStream is = file.getInputStream();
             org.apache.poi.xwpf.usermodel.XWPFDocument doc = new org.apache.poi.xwpf.usermodel.XWPFDocument(is)) {
            List<org.apache.poi.xwpf.usermodel.XWPFTable> tables = doc.getTables();
            if (tables.isEmpty()) {
                throw new IllegalArgumentException("Tài liệu Word không chứa bảng biểu (table) dữ liệu nào.");
            }
            org.apache.poi.xwpf.usermodel.XWPFTable table = tables.get(0);
            List<org.apache.poi.xwpf.usermodel.XWPFTableRow> rows = table.getRows();
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("Bảng trong tài liệu Word rỗng.");
            }
            // Tiêu đề cột
            org.apache.poi.xwpf.usermodel.XWPFTableRow headerRow = rows.get(0);
            for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : headerRow.getTableCells()) {
                result.columns.add(cell.getText().trim());
            }
            // Dòng dữ liệu
            for (int i = 1; i < rows.size(); i++) {
                org.apache.poi.xwpf.usermodel.XWPFTableRow row = rows.get(i);
                Map<String, Object> rowMap = new LinkedHashMap<>();
                List<org.apache.poi.xwpf.usermodel.XWPFTableCell> cells = row.getTableCells();
                boolean allEmpty = true;
                for (int j = 0; j < result.columns.size(); j++) {
                    String col = result.columns.get(j);
                    String val = j < cells.size() ? cells.get(j).getText().trim() : "";
                    Object parsed = tryParseNumber(val);
                    rowMap.put(col, parsed);
                    if (parsed != null && !parsed.toString().isEmpty()) allEmpty = false;
                }
                if (!allEmpty) result.rows.add(rowMap);
            }
        }
        return result;
    }

    private ParseResult parsePdf(MultipartFile file, String passedApiKey) throws Exception {
        byte[] bytes = file.getBytes();
        ParseResult localResult = parsePdfLocal(bytes);

        if (!localResult.columns.isEmpty() && !localResult.rows.isEmpty() && localResult.columns.size() >= 2) {
            log.info("PDF parsed successfully via local engine: {} rows, {} columns", localResult.rows.size(), localResult.columns.size());
            return localResult;
        }

        // Local extraction didn't find a clean structured table -> Fall back to AI Document Extraction
        log.info("Local PDF extraction yielded {} rows and {} cols. Attempting AI Document Extraction...",
                localResult.rows.size(), localResult.columns.size());

        try {
            if (geminiService != null) {
                Map<String, Object> aiResult = geminiService.extractTableFromPdf(bytes, passedApiKey);
                if (aiResult != null && aiResult.containsKey("columns") && aiResult.containsKey("rows")) {
                    ParseResult result = new ParseResult();
                    result.fileType = "PDF";
                    result.sheetName = "Page1";

                    List<?> cols = (List<?>) aiResult.get("columns");
                    if (cols != null) {
                        for (Object c : cols) {
                            if (c != null && !c.toString().trim().isEmpty()) {
                                result.columns.add(c.toString().trim());
                            }
                        }
                    }

                    List<?> rows = (List<?>) aiResult.get("rows");
                    if (rows != null) {
                        for (Object r : rows) {
                            if (r instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> rowMap = (Map<String, Object>) r;
                                Map<String, Object> cleanRow = new LinkedHashMap<>();
                                boolean hasVal = false;
                                for (String col : result.columns) {
                                    Object val = rowMap.get(col);
                                    Object parsed = val != null ? tryParseNumber(val.toString()) : "";
                                    cleanRow.put(col, parsed);
                                    if (parsed != null && !parsed.toString().isEmpty()) hasVal = true;
                                }
                                if (hasVal) result.rows.add(cleanRow);
                            }
                        }
                    }

                    if (!result.columns.isEmpty() && !result.rows.isEmpty()) {
                        log.info("PDF parsed successfully via Gemini AI: {} rows, {} columns", result.rows.size(), result.columns.size());
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Gemini AI PDF extraction attempt failed: {}", e.getMessage());
        }

        // If local found at least something with rows, return it
        if (!localResult.columns.isEmpty() && !localResult.rows.isEmpty()) {
            return localResult;
        }

        throw new IllegalArgumentException(
                "Không thể trích xuất bảng dữ liệu từ tệp PDF này. Tệp có thể là ảnh quét mờ hoặc không chứa bảng biểu có cấu trúc. " +
                "Vui lòng cấu hình Gemini API Key ở góc trên để kích hoạt AI trích xuất thông minh, hoặc chuyển tệp sang Excel (.xlsx) / CSV."
        );
    }

    private ParseResult parsePdfLocal(byte[] pdfBytes) {
        ParseResult result = new ParseResult();
        result.fileType = "PDF";
        result.sheetName = "Page1";

        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setSortByPosition(true); // Sắp xếp vị trí tọa độ X-Y trên trang PDF để giữ cấu trúc cột
            String text = stripper.getText(document);

            if (text == null || text.trim().isEmpty()) {
                return result;
            }

            String[] rawLines = text.split("\\r?\\n");
            List<String> validLines = new ArrayList<>();
            for (String raw : rawLines) {
                String line = raw.trim();
                if (line.isEmpty()) continue;
                // Bỏ qua dòng số trang: "Trang 1/5", "Page 1 of 2"
                if (line.matches("^(?i)(trang|page)\\s*\\d+(\\s*(/|of)\\s*\\d+)?$")) continue;
                // Bỏ qua đường kẻ phân cách: "---+---", "===", "___"
                if (line.matches("^[\\-=_+*|#~]{3,}$")) continue;
                validLines.add(line);
            }

            if (validLines.isEmpty()) {
                return result;
            }

            // Chiến lược 1: Bảng phân cách bằng dấu gạch đứng Pipe (| Cột 1 | Cột 2 |)
            int pipeLines = 0;
            for (String l : validLines) {
                if (countChar(l, '|') >= 2) pipeLines++;
            }
            if (pipeLines >= 2) {
                for (String l : validLines) {
                    if (countChar(l, '|') < 2) continue;
                    String[] tokens = l.split("\\|");
                    List<String> cells = new ArrayList<>();
                    for (String t : tokens) {
                        String trimmed = t.trim();
                        if (!trimmed.isEmpty()) cells.add(trimmed);
                    }
                    if (cells.size() >= 2) {
                        if (result.columns.isEmpty()) {
                            result.columns.addAll(cells);
                        } else {
                            Map<String, Object> rowMap = new LinkedHashMap<>();
                            boolean hasVal = false;
                            for (int j = 0; j < result.columns.size(); j++) {
                                String col = result.columns.get(j);
                                String val = j < cells.size() ? cells.get(j) : "";
                                Object parsed = tryParseNumber(val);
                                rowMap.put(col, parsed);
                                if (parsed != null && !parsed.toString().isEmpty()) hasVal = true;
                            }
                            if (hasVal) result.rows.add(rowMap);
                        }
                    }
                }
                if (!result.columns.isEmpty() && !result.rows.isEmpty()) {
                    return result;
                }
                result.columns.clear();
                result.rows.clear();
            }

            // Chiến lược 2: Bảng phân cách bằng Tab (\t)
            int tabLines = 0;
            for (String l : validLines) {
                if (countChar(l, '\t') >= 1) tabLines++;
            }
            if (tabLines >= 2) {
                for (String l : validLines) {
                    String[] cells = l.split("\\t+");
                    if (cells.length >= 2) {
                        if (result.columns.isEmpty()) {
                            for (String c : cells) if (!c.trim().isEmpty()) result.columns.add(c.trim());
                        } else {
                            Map<String, Object> rowMap = new LinkedHashMap<>();
                            boolean hasVal = false;
                            for (int j = 0; j < result.columns.size(); j++) {
                                String col = result.columns.get(j);
                                String val = j < cells.length ? cells[j].trim() : "";
                                Object parsed = tryParseNumber(val);
                                rowMap.put(col, parsed);
                                if (parsed != null && !parsed.toString().isEmpty()) hasVal = true;
                            }
                            if (hasVal) result.rows.add(rowMap);
                        }
                    }
                }
                if (!result.columns.isEmpty() && !result.rows.isEmpty()) {
                    return result;
                }
                result.columns.clear();
                result.rows.clear();
            }

            // Chiến lược 3: Dò tiêu đề thông minh với khoảng trắng kép (\s{2,})
            int bestHeaderIdx = -1;
            int maxConsecutiveMatches = 0;
            int maxColumnsFound = 0;

            int scanLimit = Math.min(30, validLines.size());
            for (int i = 0; i < scanLimit; i++) {
                String[] tokens = validLines.get(i).split("\\s{2,}");
                if (tokens.length >= 2) {
                    int matches = 0;
                    for (int k = i + 1; k < Math.min(i + 10, validLines.size()); k++) {
                        String[] nextTokens = validLines.get(k).split("\\s{2,}");
                        if (nextTokens.length >= 2) matches++;
                    }
                    if (matches > maxConsecutiveMatches || (matches == maxConsecutiveMatches && tokens.length > maxColumnsFound)) {
                        maxConsecutiveMatches = matches;
                        maxColumnsFound = tokens.length;
                        bestHeaderIdx = i;
                    }
                }
            }

            if (bestHeaderIdx != -1 && maxColumnsFound >= 2) {
                String[] headerTokens = validLines.get(bestHeaderIdx).split("\\s{2,}");
                for (String h : headerTokens) {
                    String colName = h.trim();
                    if (!colName.isEmpty()) result.columns.add(colName);
                }

                for (int i = bestHeaderIdx + 1; i < validLines.size(); i++) {
                    String line = validLines.get(i);
                    String[] parts = line.split("\\s{2,}");
                    if (parts.length < 2 && result.columns.size() > 2) {
                        String[] spaceParts = line.split("\\s+");
                        if (spaceParts.length >= result.columns.size()) {
                            parts = spaceParts;
                        }
                    }
                    Map<String, Object> rowMap = new LinkedHashMap<>();
                    boolean hasVal = false;
                    for (int j = 0; j < result.columns.size(); j++) {
                        String col = result.columns.get(j);
                        String val = j < parts.length ? parts[j].trim() : "";
                        Object parsed = tryParseNumber(val);
                        rowMap.put(col, parsed);
                        if (parsed != null && !parsed.toString().isEmpty()) hasVal = true;
                    }
                    if (hasVal) result.rows.add(rowMap);
                }

                if (!result.columns.isEmpty() && !result.rows.isEmpty()) {
                    return result;
                }
                result.columns.clear();
                result.rows.clear();
            }

            // Chiến lược 4: Dò định dạng CSV (phẩy hoặc chấm phẩy)
            char delim = detectDelimiter(text);
            if (delim == ',' || delim == ';') {
                for (String line : validLines) {
                    String[] parts = line.split(String.valueOf(delim));
                    if (parts.length >= 2) {
                        if (result.columns.isEmpty()) {
                            for (String p : parts) if (!p.trim().isEmpty()) result.columns.add(p.trim());
                        } else {
                            Map<String, Object> rowMap = new LinkedHashMap<>();
                            boolean hasVal = false;
                            for (int j = 0; j < result.columns.size(); j++) {
                                String col = result.columns.get(j);
                                String val = j < parts.length ? parts[j].trim() : "";
                                Object parsed = tryParseNumber(val);
                                rowMap.put(col, parsed);
                                if (parsed != null && !parsed.toString().isEmpty()) hasVal = true;
                            }
                            if (hasVal) result.rows.add(rowMap);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Local PDF extraction encountered exception: {}", e.getMessage());
        }

        return result;
    }

    private ParseResult parseExcel(MultipartFile file, String requestedSheet) throws Exception {
        ParseResult result = new ParseResult();
        result.fileType = "EXCEL";

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet;
            if (requestedSheet != null && !requestedSheet.isBlank()) {
                sheet = workbook.getSheet(requestedSheet.trim());
                if (sheet == null) {
                    throw new IllegalArgumentException("Không tìm thấy sheet '" + requestedSheet + "' trong tệp Excel.");
                }
            } else {
                sheet = workbook.getSheetAt(0);
            }
            result.sheetName = sheet.getSheetName();

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new IllegalArgumentException("Excel file has no header row");

            // Extract column names
            for (Cell cell : headerRow) {
                result.columns.add(getCellValueAsString(cell).trim());
            }

            // Extract data rows
            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                boolean allEmpty = true;
                Map<String, Object> rowMap = new LinkedHashMap<>();
                for (int j = 0; j < result.columns.size(); j++) {
                    Cell cell = row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    Object val = getCellValue(cell, formatter);
                    rowMap.put(result.columns.get(j), val);
                    if (val != null && !val.toString().trim().isEmpty()) allEmpty = false;
                }
                if (!allEmpty) result.rows.add(rowMap);
            }
        }
        return result;
    }

    private ParseResult parseCsv(MultipartFile file) throws Exception {
        ParseResult result = new ParseResult();
        result.fileType = "CSV";
        result.sheetName = "Sheet1";

        byte[] bytes = file.getBytes();
        String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') content = content.substring(1);

        // Auto-detect delimiter
        char delimiter = detectDelimiter(content);

        try (CSVReader reader = new CSVReaderBuilder(new StringReader(content))
                .withCSVParser(new CSVParserBuilder().withSeparator(delimiter).build())
                .build()) {

            String[] headers = reader.readNext();
            if (headers == null) throw new IllegalArgumentException("CSV file is empty");

            for (String h : headers) result.columns.add(h.trim());

            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, Object> rowMap = new LinkedHashMap<>();
                boolean allEmpty = true;
                for (int i = 0; i < result.columns.size(); i++) {
                    String val = i < line.length ? line[i].trim() : "";
                    Object parsedVal = tryParseNumber(val);
                    rowMap.put(result.columns.get(i), parsedVal);
                    if (!val.isEmpty()) allEmpty = false;
                }
                if (!allEmpty) result.rows.add(rowMap);
            }
        }
        return result;
    }

    private ParseResult parseJson(MultipartFile file) throws Exception {
        ParseResult result = new ParseResult();
        result.fileType = "JSON";
        result.sheetName = "Sheet1";

        String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();

        // Try as array of objects
        if (content.startsWith("[")) {
            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> rows = gson.fromJson(content, listType);
            if (rows != null && !rows.isEmpty()) {
                result.columns.addAll(rows.get(0).keySet());
                result.rows = rows;
            }
        } else if (content.startsWith("{")) {
            // Try as object with "data" array
            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            Map<String, Object> obj = gson.fromJson(content, mapType);
            for (String key : obj.keySet()) {
                Object val = obj.get(key);
                if (val instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) val;
                    if (!rows.isEmpty()) {
                        result.columns.addAll(rows.get(0).keySet());
                        result.rows = rows;
                        break;
                    }
                }
            }
        }

        return result;
    }

    private char detectDelimiter(String content) {
        String firstLine = content.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        int commas = countUnquoted(firstLine, ',');
        int semicolons = countUnquoted(firstLine, ';');
        int tabs = countUnquoted(firstLine, '\t');
        int pipes = countUnquoted(firstLine, '|');

        int max = Math.max(Math.max(commas, semicolons), Math.max(tabs, pipes));
        if (max == commas) return ',';
        if (max == semicolons) return ';';
        if (max == tabs) return '\t';
        if (max == pipes) return '|';
        return ',';
    }

    private int countUnquoted(String value, char delimiter) {
        int count = 0;
        boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < value.length() && value.charAt(i + 1) == '"') i++;
                else quoted = !quoted;
            } else if (!quoted && ch == delimiter) {
                count++;
            }
        }
        return count;
    }

    private int countChar(String s, char c) {
        int count = 0;
        for (char ch : s.toCharArray()) if (ch == c) count++;
        return count;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell);
    }

    private Object getCellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return formatter.formatCellValue(cell);
                }
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                    return (long) d;
                }
                return d;
            case STRING:
                String s = cell.getStringCellValue().trim();
                return tryParseNumber(s);
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                try {
                    return cell.getNumericCellValue();
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            case BLANK:
            case _NONE:
            case ERROR:
            default:
                return null;
        }
    }

    private Object tryParseNumber(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        return s;
    }

    public String toJson(Object obj) {
        return gson.toJson(obj);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fromJson(String json) {
        Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
        return gson.fromJson(json, listType);
    }

    @SuppressWarnings("unchecked")
    public List<String> fromJsonStringList(String json) {
        Type listType = new TypeToken<List<String>>(){}.getType();
        return gson.fromJson(json, listType);
    }
}
