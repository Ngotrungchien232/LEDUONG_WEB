package com.datalens.service;

import com.datalens.model.AuditLog;
import com.datalens.model.Dataset;
import com.datalens.repository.AuditLogRepository;
import com.datalens.repository.AnalysisReportRepository;
import com.datalens.repository.DatasetRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final DatasetRepository datasetRepo;
    private final FileParserService parserService;
    private final AuditLogRepository auditRepo;
    private final AnalysisReportRepository reportRepo;
    private final Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

    public ExportService(DatasetRepository datasetRepo,
                         FileParserService parserService,
                         AuditLogRepository auditRepo,
                         AnalysisReportRepository reportRepo) {
        this.datasetRepo = datasetRepo;
        this.parserService = parserService;
        this.auditRepo = auditRepo;
        this.reportRepo = reportRepo;
    }

    /**
     * Xuất tệp Excel (.xlsx) chuẩn doanh nghiệp gồm:
     * - Sheet 1: Dữ liệu sạch (Cleaned Data) có định dạng chuẩn số, căn lề, viền và thanh lọc tự động.
     * - Sheet 2: Nhật ký kiểm toán (Audit Trail) ghi nhận chi tiết các thao tác làm sạch.
     * - Sheet 3: Thông tin tệp & Thống kê (Metadata & Summary KPIs).
     */
    public byte[] exportExcel(Long datasetId) throws IOException {
        Dataset dataset = getDataset(datasetId);
        List<Map<String, Object>> rows = parserService.fromJson(dataset.getRawDataJson());
        if (rows == null) rows = Collections.emptyList();
        Map<String, Object> quality = qualitySummary(rows);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            DataFormat dataFormat = workbook.createDataFormat();

            // Font & Colors
            XSSFFont fontBold = workbook.createFont();
            fontBold.setFontName("Segoe UI");
            fontBold.setFontHeightInPoints((short) 10);
            fontBold.setBold(true);

            XSSFFont fontNormal = workbook.createFont();
            fontNormal.setFontName("Segoe UI");
            fontNormal.setFontHeightInPoints((short) 10);

            XSSFFont fontHeader = workbook.createFont();
            fontHeader.setFontName("Segoe UI");
            fontHeader.setFontHeightInPoints((short) 10);
            fontHeader.setBold(true);
            fontHeader.setColor(IndexedColors.WHITE.getIndex());

            // Header Style (Indigo Theme: #312E81)
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(fontHeader);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 49, (byte) 46, (byte) 129}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.LEFT);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(headerStyle, BorderStyle.THIN, IndexedColors.GREY_40_PERCENT.getIndex());

            // Cell Styles: Text
            XSSFCellStyle cellTextEven = createRowStyle(workbook, fontNormal, IndexedColors.WHITE.getIndex());
            XSSFCellStyle cellTextOdd = createRowStyle(workbook, fontNormal, new byte[]{(byte) 248, (byte) 250, (byte) 252});

            // Cell Styles: Integer Number (#,##0)
            XSSFCellStyle cellIntEven = createRowStyle(workbook, fontNormal, IndexedColors.WHITE.getIndex());
            cellIntEven.setDataFormat(dataFormat.getFormat("#,##0"));
            cellIntEven.setAlignment(HorizontalAlignment.RIGHT);

            XSSFCellStyle cellIntOdd = createRowStyle(workbook, fontNormal, new byte[]{(byte) 248, (byte) 250, (byte) 252});
            cellIntOdd.setDataFormat(dataFormat.getFormat("#,##0"));
            cellIntOdd.setAlignment(HorizontalAlignment.RIGHT);

            // Cell Styles: Decimal Number (#,##0.00)
            XSSFCellStyle cellDecEven = createRowStyle(workbook, fontNormal, IndexedColors.WHITE.getIndex());
            cellDecEven.setDataFormat(dataFormat.getFormat("#,##0.00"));
            cellDecEven.setAlignment(HorizontalAlignment.RIGHT);

            XSSFCellStyle cellDecOdd = createRowStyle(workbook, fontNormal, new byte[]{(byte) 248, (byte) 250, (byte) 252});
            cellDecOdd.setDataFormat(dataFormat.getFormat("#,##0.00"));
            cellDecOdd.setAlignment(HorizontalAlignment.RIGHT);

            // ── SHEET 1: Dữ liệu sạch (Cleaned Data) ───────────────────────────
            String cleanSheetName = "Dữ liệu sạch";
            XSSFSheet sheetData = workbook.createSheet(cleanSheetName);
            sheetData.setDisplayGridlines(true);

            List<String> columns = new ArrayList<>();
            if (!rows.isEmpty()) {
                columns = rows.get(0).keySet().stream()
                        .filter(k -> !k.startsWith("__"))
                        .toList();

                // Header Row
                Row headerRow = sheetData.createRow(0);
                headerRow.setHeightInPoints(26);
                for (int i = 0; i < columns.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(columns.get(i));
                    cell.setCellStyle(headerStyle);
                }

                // Data Rows
                for (int r = 0; r < rows.size(); r++) {
                    Row row = sheetData.createRow(r + 1);
                    row.setHeightInPoints(20);
                    Map<String, Object> rowData = rows.get(r);
                    boolean isEven = (r % 2 == 0);

                    for (int c = 0; c < columns.size(); c++) {
                        Cell cell = row.createCell(c);
                        Object val = rowData.get(columns.get(c));
                        setCellValueFormatted(cell, val,
                                isEven ? cellIntEven : cellIntOdd,
                                isEven ? cellDecEven : cellDecOdd,
                                isEven ? cellTextEven : cellTextOdd);
                    }
                }

                // Enable AutoFilter & Freeze Top Header Row
                sheetData.setAutoFilter(new CellRangeAddress(0, rows.size(), 0, columns.size() - 1));
                sheetData.createFreezePane(0, 1);

                // Auto-fit Column Widths with comfortable padding
                for (int i = 0; i < columns.size(); i++) {
                    sheetData.autoSizeColumn(i);
                    int currentWidth = sheetData.getColumnWidth(i);
                    sheetData.setColumnWidth(i, Math.min(256 * 60, Math.max(currentWidth + 1400, 256 * 14)));
                }
            } else {
                Row emptyRow = sheetData.createRow(0);
                emptyRow.createCell(0).setCellValue("Không có dữ liệu trong tệp này.");
            }

            // ── SHEET 2: Nhật ký kiểm toán (Audit Trail) ───────────────────────
            XSSFSheet sheetAudit = workbook.createSheet("Nhật ký kiểm toán");
            sheetAudit.setDisplayGridlines(true);

            XSSFCellStyle auditHeaderStyle = workbook.createCellStyle();
            auditHeaderStyle.setFont(fontHeader);
            auditHeaderStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 51, (byte) 65, (byte) 85}, null)); // Slate #334155
            auditHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            auditHeaderStyle.setAlignment(HorizontalAlignment.LEFT);
            auditHeaderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            setBorders(auditHeaderStyle, BorderStyle.THIN, IndexedColors.GREY_40_PERCENT.getIndex());

            String[] auditHeaders = {"STT", "Dòng dữ liệu", "Cột / Trường", "Giá trị trước làm sạch", "Giá trị sau làm sạch", "Quy tắc áp dụng", "Thời điểm ghi nhận"};
            Row auditHeaderRow = sheetAudit.createRow(0);
            auditHeaderRow.setHeightInPoints(24);
            for (int i = 0; i < auditHeaders.length; i++) {
                Cell cell = auditHeaderRow.createCell(i);
                cell.setCellValue(auditHeaders[i]);
                cell.setCellStyle(auditHeaderStyle);
            }

            List<AuditLog> auditLogs = auditRepo.findByDatasetIdOrderByCreatedAtDesc(datasetId);
            Map<?, ?> latestReport = latestReport(datasetId);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

            for (int i = 0; i < auditLogs.size(); i++) {
                AuditLog logItem = auditLogs.get(i);
                Row row = sheetAudit.createRow(i + 1);
                row.setHeightInPoints(19);
                boolean isEven = (i % 2 == 0);
                XSSFCellStyle rowStyle = isEven ? cellTextEven : cellTextOdd;

                createCellWithStyle(row, 0, i + 1, isEven ? cellIntEven : cellIntOdd);
                createCellWithStyle(row, 1, logItem.getRowNumber(), isEven ? cellIntEven : cellIntOdd);
                createCellWithStyle(row, 2, logItem.getColumnName(), rowStyle);
                createCellWithStyle(row, 3, logItem.getBeforeValue(), rowStyle);
                createCellWithStyle(row, 4, logItem.getAfterValue(), rowStyle);
                createCellWithStyle(row, 5, logItem.getChangeReason(), rowStyle);
                createCellWithStyle(row, 6, logItem.getCreatedAt() != null ? logItem.getCreatedAt().format(dtf) : "", rowStyle);
            }

            sheetAudit.createFreezePane(0, 1);
            for (int i = 0; i < auditHeaders.length; i++) {
                sheetAudit.autoSizeColumn(i);
                int currentWidth = sheetAudit.getColumnWidth(i);
                sheetAudit.setColumnWidth(i, Math.max(currentWidth + 1200, 256 * 14));
            }

            // ── SHEET 3: Thông tin tệp & Thống kê (Summary & KPIs) ─────────────
            XSSFSheet sheetSummary = workbook.createSheet("Thông tin & Thống kê");
            sheetSummary.setDisplayGridlines(true);

            // Title block
            XSSFFont fontTitle = workbook.createFont();
            fontTitle.setFontName("Segoe UI");
            fontTitle.setFontHeightInPoints((short) 13);
            fontTitle.setBold(true);
            fontTitle.setColor(new XSSFColor(new byte[]{(byte) 30, (byte) 27, (byte) 75}, null));

            XSSFCellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(fontTitle);
            titleStyle.setAlignment(HorizontalAlignment.LEFT);

            Row titleRow = sheetSummary.createRow(1);
            Cell titleCell = titleRow.createCell(1);
            titleCell.setCellValue("BÁO CÁO DỮ LIỆU ĐÃ LÀM SẠCH – DATALENS STUDIO");
            titleCell.setCellStyle(titleStyle);

            // Metadata items
            String[][] metaData = {
                    {"Tên tệp gốc:", dataset.getOriginalFileName()},
                    {"Định dạng nguồn:", dataset.getFileType() != null ? dataset.getFileType() : "N/A"},
                    {"Tổng số bản ghi (dòng):", String.valueOf(rows.size())},
                    {"Tổng số thuộc tính (cột):", String.valueOf(columns.size())},
                    {"Số thao tác làm sạch & kiểm toán:", String.valueOf(auditLogs.size())},
                    {"Số dòng bị ảnh hưởng:", String.valueOf(auditLogs.stream().map(AuditLog::getRowNumber).filter(Objects::nonNull).distinct().count())},
                    {"Số cột bị ảnh hưởng:", String.valueOf(auditLogs.stream().map(AuditLog::getColumnName).filter(Objects::nonNull).distinct().count())},
                    {"Độ đầy đủ dữ liệu:", String.valueOf(quality.get("completenessPercent")) + "%"},
                    {"Ô dữ liệu còn trống:", String.valueOf(quality.get("missingCells"))},
                    {"Chỉ số phân tích:", Objects.toString(latestReport.get("metricCol"), "Chưa chọn")},
                    {"Phân nhóm theo:", Objects.toString(latestReport.get("categoryCol"), "Chưa chọn")},
                    {"Bộ lọc:", filterSummary(latestReport)},
                    {"Bối cảnh:", Objects.toString(dataset.getBusinessContext(), "Chưa cung cấp")},
                    {"Mục tiêu:", Objects.toString(dataset.getDataGoal(), "Chưa cung cấp")},
                    {"Người tạo báo cáo:", "DataLens Studio"},
                    {"Trạng thái dữ liệu:", Objects.toString(dataset.getStatus(), "UPLOADED")},
                    {"Thời điểm xuất báo cáo:", LocalDateTime.now().format(dtf)}
            };

            for (int i = 0; i < metaData.length; i++) {
                Row r = sheetSummary.createRow(3 + i);
                r.setHeightInPoints(20);

                Cell labelCell = r.createCell(1);
                labelCell.setCellValue(metaData[i][0]);
                labelCell.setCellStyle(cellTextOdd);

                Cell valCell = r.createCell(2);
                valCell.setCellValue(metaData[i][1]);
                valCell.setCellStyle(cellTextEven);
            }

            sheetSummary.setColumnWidth(1, 256 * 32);
            sheetSummary.setColumnWidth(2, 256 * 45);

            // ── SHEET 4: Bảng tổng hợp theo phân nhóm + biểu đồ ───────────────
            XSSFSheet sheetPivot = workbook.createSheet("Tổng hợp & Biểu đồ");
            sheetPivot.createFreezePane(0, 1);
            String metric = Objects.toString(latestReport.get("metricCol"), "");
            String category = Objects.toString(latestReport.get("categoryCol"), "");
            List<Map<String, Object>> aggregate = mapList(latestReport.get("aggregatedData"));
            if (aggregate.isEmpty() && !rows.isEmpty()) {
                Map<String, Double> grouped = new TreeMap<>();
                for (Map<String, Object> dataRow : rows) {
                    String key = category.isBlank() ? "Tất cả dữ liệu" : Objects.toString(dataRow.get(category), "(Trống)");
                    double value = metric.isBlank() ? 1d : parseNumeric(dataRow.get(metric));
                    grouped.merge(key, value, Double::sum);
                }
                for (Map.Entry<String, Double> entry : grouped.entrySet()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("category", entry.getKey());
                    item.put("value", entry.getValue());
                    aggregate.add(item);
                }
            }
            Row pivotHeader = sheetPivot.createRow(0);
            String pivotCategory = category.isBlank() ? "Nhóm" : category;
            String pivotMetric = metric.isBlank() ? "Số bản ghi" : metric;
            for (int c = 0; c < 3; c++) {
                Cell cell = pivotHeader.createCell(c);
                cell.setCellValue(c == 0 ? pivotCategory : c == 1 ? pivotMetric : "Tỷ trọng (%)");
                cell.setCellStyle(headerStyle);
            }
            double aggregateTotal = aggregate.stream().mapToDouble(item -> parseNumeric(item.get("value"))).sum();
            XSSFCellStyle shareStyle = workbook.createCellStyle();
            shareStyle.cloneStyleFrom(cellDecEven);
            shareStyle.setDataFormat(dataFormat.getFormat("0.00%"));
            int pivotRowIndex = 1;
            for (Map<String, Object> item : aggregate) {
                Row r = sheetPivot.createRow(pivotRowIndex++);
                createCellWithStyle(r, 0, item.get("category"), cellTextEven);
                createCellWithStyle(r, 1, item.get("value"), cellDecEven);
                Cell share = r.createCell(2);
                share.setCellValue(aggregateTotal == 0 ? 0 : parseNumeric(item.get("value")) / aggregateTotal);
                share.setCellStyle(shareStyle);
            }
            if (!aggregate.isEmpty()) {
                sheetPivot.setAutoFilter(new CellRangeAddress(0, pivotRowIndex - 1, 0, 2));
                XSSFDrawing drawing = sheetPivot.createDrawingPatriarch();
                XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 4, 1, 13, 18);
                XSSFChart chart = drawing.createChart(anchor);
                chart.setTitleText("" + pivotMetric + " theo " + pivotCategory);
                chart.setTitleOverlay(false);
                XDDFCategoryAxis xAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
                xAxis.setTitle(pivotCategory);
                XDDFValueAxis yAxis = chart.createValueAxis(AxisPosition.LEFT);
                yAxis.setTitle(pivotMetric);
                yAxis.setCrosses(AxisCrosses.AUTO_ZERO);
                XDDFDataSource<String> categoryData = XDDFDataSourcesFactory.fromStringCellRange(sheetPivot, new CellRangeAddress(1, pivotRowIndex - 1, 0, 0));
                XDDFNumericalDataSource<Double> valueData = XDDFDataSourcesFactory.fromNumericCellRange(sheetPivot, new CellRangeAddress(1, pivotRowIndex - 1, 1, 1));
                XDDFChartData chartData = chart.createData(ChartTypes.BAR, xAxis, yAxis);
                ((XDDFBarChartData) chartData).setBarDirection(BarDirection.COL);
                chartData.addSeries(categoryData, valueData).setTitle(pivotMetric, null);
                chart.plot(chartData);
            } else {
                sheetPivot.createRow(1).createCell(0).setCellValue("Chưa có dữ liệu tổng hợp để vẽ biểu đồ.");
            }
            sheetPivot.setColumnWidth(0, 256 * 32);
            sheetPivot.setColumnWidth(1, 256 * 20);
            sheetPivot.setColumnWidth(2, 256 * 20);

            // ── SHEET 5: Thống kê từng cột + phân tích ────────────────────────
            XSSFSheet sheetStats = workbook.createSheet("Thống kê & Phân tích");
            String[] statHeaders = {"Chỉ số", "Giá trị"};
            Row statHeader = sheetStats.createRow(0);
            for (int i = 0; i < statHeaders.length; i++) {
                statHeader.createCell(i).setCellValue(statHeaders[i]);
                statHeader.getCell(i).setCellStyle(headerStyle);
            }
            int statRow = 1;
            for (Map.Entry<String, Map<String, Object>> entry : mapMap(latestReport.get("columnStats")).entrySet()) {
                for (Map.Entry<String, Object> stat : entry.getValue().entrySet()) {
                    Row r = sheetStats.createRow(statRow++);
                    createCellWithStyle(r, 0, entry.getKey() + " · " + stat.getKey(), cellTextEven);
                    createCellWithStyle(r, 1, stat.getValue(), stat.getValue() instanceof Number ? cellDecEven : cellTextEven);
                }
            }
            appendNarrativeRows(sheetStats, statRow + 1, "Nhận định", stringList(latestReport.get("insights")), cellTextEven);
            appendNarrativeRows(sheetStats, statRow + 5 + stringList(latestReport.get("insights")).size(), "Khuyến nghị", stringList(latestReport.get("recommendations")), cellTextEven);
            sheetStats.setColumnWidth(0, 256 * 42);
            sheetStats.setColumnWidth(1, 256 * 28);
            sheetStats.createFreezePane(0, 1);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Xuất tệp CSV chuẩn UTF-8 có đính kèm UTF-8 BOM (0xEF, 0xBB, 0xBF).
     * Đảm bảo khi mở bằng Microsoft Excel trên Windows / macOS không bao giờ bị lỗi font tiếng Việt.
     */
    public byte[] exportCsv(Long datasetId) throws IOException {
        Dataset dataset = getDataset(datasetId);
        List<Map<String, Object>> rows = parserService.fromJson(dataset.getRawDataJson());
        if (rows == null) rows = Collections.emptyList();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // UTF-8 BOM so Excel opens Vietnamese characters with 100% fidelity
        baos.write(0xEF);
        baos.write(0xBB);
        baos.write(0xBF);

        try (OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            if (!rows.isEmpty()) {
                List<String> columns = rows.get(0).keySet().stream()
                        .filter(k -> !k.startsWith("__"))
                        .toList();

                // Header
                writer.write(String.join(",", columns.stream().map(col -> csvEscape(col, true)).toList()) + "\r\n");

                // Rows
                for (Map<String, Object> row : rows) {
                    List<String> vals = new ArrayList<>();
                    for (String col : columns) {
                        Object v = row.get(col);
                        vals.add(csvEscape(formatNumberForCsv(v), !(v instanceof Number)));
                    }
                    writer.write(String.join(",", vals) + "\r\n");
                }
            }
        }
        return baos.toByteArray();
    }

    /**
     * Xuất tệp JSON chuẩn hóa, thụt lề định dạng đẹp mắt (Pretty-Print) kèm thông tin metadata.
     */
    public byte[] exportJson(Long datasetId) throws IOException {
        Dataset dataset = getDataset(datasetId);
        List<Map<String, Object>> rows = parserService.fromJson(dataset.getRawDataJson());
        if (rows == null) rows = Collections.emptyList();

        List<String> columns = rows.isEmpty() ? Collections.emptyList() :
                rows.get(0).keySet().stream().filter(k -> !k.startsWith("__")).toList();

        List<Map<String, Object>> cleanRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (String col : columns) {
                r.put(col, row.get(col));
            }
            cleanRows.add(r);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("metadata", Map.of(
                "fileName", dataset.getOriginalFileName(),
                "fileType", dataset.getFileType() != null ? dataset.getFileType() : "DATASET",
                "rowCount", cleanRows.size(),
                "columnCount", columns.size(),
                "exportedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "createdBy", "DataLens Studio",
                "quality", qualitySummary(rows),
                "analysis", Map.of("metric", Objects.toString(latestReport(datasetId).get("metricCol"), ""),
                        "category", Objects.toString(latestReport(datasetId).get("categoryCol"), ""),
                        "filters", filterSummary(latestReport(datasetId)))
        ));
        payload.put("columns", columns);
        payload.put("data", cleanRows);

        return prettyGson.toJson(payload).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Xuất Dashboard Báo cáo HTML độc lập, mở offline không cần mạng/server,
     * thiết kế giao diện hiện đại theo chuẩn SaaS Enterprise, có thanh tìm kiếm trực tiếp và nút tải CSV.
     */
    public byte[] exportHtml(Long datasetId) throws IOException {
        Dataset dataset = getDataset(datasetId);
        List<Map<String, Object>> rows = parserService.fromJson(dataset.getRawDataJson());
        if (rows == null) rows = Collections.emptyList();

        List<String> columns = rows.isEmpty() ? Collections.emptyList() :
                rows.get(0).keySet().stream().filter(k -> !k.startsWith("__")).toList();

        List<AuditLog> auditLogs = auditRepo.findByDatasetIdOrderByCreatedAtDesc(datasetId);
        Map<String, Object> quality = qualitySummary(rows);
        Map<?, ?> latestReport = latestReport(datasetId);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
            .append("<html lang='vi'>\n<head>\n<meta charset='UTF-8'>\n")
            .append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>\n")
            .append("<title>Báo cáo Dữ liệu – ").append(htmlEscape(dataset.getOriginalFileName())).append("</title>\n")
            .append("<style>\n")
            .append("  :root {\n")
            .append("    --primary: #4F46E5; --primary-dark: #3730A3; --surface: #FFFFFF; --bg: #F8FAFC;\n")
            .append("    --text-main: #0F172A; --text-muted: #64748B; --border: #E2E8F0;\n")
            .append("  }\n")
            .append("  * { box-sizing: border-box; margin: 0; padding: 0; }\n")
            .append("  body { font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, sans-serif; background: var(--bg); color: var(--text-main); padding: 32px 24px; line-height: 1.5; }\n")
            .append("  .container { max-width: 1400px; margin: 0 auto; }\n")
            .append("  .header-card { background: linear-gradient(135deg, #1E1B4B 0%, #312E81 100%); color: white; padding: 30px; border-radius: 12px; margin-bottom: 24px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); }\n")
            .append("  .header-badge { display: inline-block; background: rgba(255,255,255,0.15); color: #C7D2FE; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.8px; padding: 4px 10px; border-radius: 20px; margin-bottom: 12px; }\n")
            .append("  .header-title { font-size: 24px; font-weight: 700; margin-bottom: 6px; }\n")
            .append("  .header-subtitle { color: #A5B4FC; font-size: 13.5px; }\n")
            .append("  .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 24px; }\n")
            .append("  .kpi-card { background: var(--surface); padding: 20px; border-radius: 10px; border: 1px solid var(--border); box-shadow: 0 1px 3px rgba(0,0,0,0.04); }\n")
            .append("  .kpi-label { font-size: 11.5px; font-weight: 600; text-transform: uppercase; color: var(--text-muted); letter-spacing: 0.5px; }\n")
            .append("  .kpi-value { font-size: 26px; font-weight: 800; color: #1E293B; margin-top: 6px; }\n")
            .append("  .table-card { background: var(--surface); border-radius: 12px; border: 1px solid var(--border); box-shadow: 0 2px 8px rgba(0,0,0,0.04); overflow: hidden; }\n")
            .append("  .table-toolbar { padding: 16px 20px; border-bottom: 1px solid var(--border); display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; background: #FAF5FF; }\n")
            .append("  .search-input { padding: 8px 14px; border: 1px solid var(--border); border-radius: 6px; font-size: 13px; width: 300px; max-width: 100%; outline: none; background: white; }\n")
            .append("  .search-input:focus { border-color: var(--primary); box-shadow: 0 0 0 2px rgba(79,70,229,0.15); }\n")
            .append("  .btn-download { background: var(--primary); color: white; border: none; padding: 8px 16px; border-radius: 6px; font-size: 13px; font-weight: 600; cursor: pointer; transition: 0.2s; }\n")
            .append("  .btn-download:hover { background: var(--primary-dark); }\n")
            .append("  .table-wrapper { overflow-x: auto; max-height: 600px; }\n")
            .append("  table { width: 100%; border-collapse: collapse; font-size: 13px; }\n")
            .append("  th { background: #312E81; color: white; padding: 12px 16px; text-align: left; font-weight: 600; position: sticky; top: 0; white-space: nowrap; }\n")
            .append("  td { padding: 10px 16px; border-bottom: 1px solid #F1F5F9; color: #334155; white-space: nowrap; }\n")
            .append("  tr:nth-child(even) td { background: #F8FAFC; }\n")
            .append("  tr:hover td { background: #EEF2FF; }\n")
            .append("  .text-right { text-align: right; }\n")
            .append("  .report-section { background: var(--surface); padding: 24px; border: 1px solid var(--border); border-radius: 12px; margin: 0 0 24px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }\n")
            .append("  .report-section h2 { font-size: 18px; margin-bottom: 12px; color: #1E1B4B; } .report-section h3 { margin: 20px 0 12px; font-size: 15px; }\n")
            .append("  .muted { color: var(--text-muted); font-size: 13px; margin-bottom: 14px; } .context-grid { display:grid; grid-template-columns:1fr 1fr; gap:24px; }\n")
            .append("  .bar-row { display:grid; grid-template-columns:minmax(120px, 1fr) minmax(120px, 3fr) 80px; gap:12px; align-items:center; margin:10px 0; font-size:13px; } .bar-label { overflow-wrap:anywhere; } .bar-track { background:#E0E7FF; border-radius:8px; height:12px; overflow:hidden; } .bar-fill { display:block; height:100%; background:linear-gradient(90deg,#4F46E5,#06B6D4); border-radius:8px; }\n")
            .append("  .footer-note { text-align: center; margin-top: 32px; font-size: 12px; color: var(--text-muted); }\n")
            .append("  @media (max-width:700px) { .context-grid { grid-template-columns:1fr; } .bar-row { grid-template-columns:1fr 2fr 55px; } }\n")
            .append("  @media print { @page { size: A4 landscape; margin: 12mm; } body { padding: 0; background: #fff; print-color-adjust: exact; -webkit-print-color-adjust: exact; } .container { max-width: none; } .table-toolbar { display: none; } .table-wrapper { max-height: none; overflow: visible; } .table-card, .report-section { break-inside: auto; box-shadow: none; } .header-card, .kpi-card { break-inside: avoid; box-shadow: none; } th { position: static; } thead { display: table-header-group; } tr { break-inside: avoid; } tr:hover td { background: inherit; } .footer-note { margin-top: 12px; } }\n")
            .append("</style>\n</head>\n<body>\n")
            .append("<div class='container'>\n")
            .append("  <div class='header-card'>\n")
            .append("    <span class='header-badge'>Báo cáo Kiểm toán & Dữ liệu Sạch</span>\n")
            .append("    <h1 class='header-title'>").append(htmlEscape(dataset.getOriginalFileName())).append("</h1>\n")
            .append("    <p class='header-subtitle'>Tệp được làm sạch, kiểm định và tạo tự động bởi DataLens Studio vào lúc ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("</p>\n")
            .append("    <p class='header-subtitle'>Chỉ số: ").append(htmlEscape(Objects.toString(latestReport.get("metricCol"), "Chưa chọn")))
            .append(" · Nhóm: ").append(htmlEscape(Objects.toString(latestReport.get("categoryCol"), "Chưa chọn")))
            .append(" · Bộ lọc: ").append(htmlEscape(filterSummary(latestReport))).append("</p>\n")
            .append("  </div>\n")
            .append("  <div class='kpi-grid'>\n")
            .append("    <div class='kpi-card'><div class='kpi-label'>Tổng số bản ghi sạch</div><div class='kpi-value'>").append(String.format("%,d", rows.size())).append("</div></div>\n")
            .append("    <div class='kpi-card'><div class='kpi-label'>Số trường thuộc tính</div><div class='kpi-value'>").append(columns.size()).append("</div></div>\n")
            .append("    <div class='kpi-card'><div class='kpi-label'>Thao tác đã kiểm toán</div><div class='kpi-value'>").append(auditLogs.size()).append("</div></div>\n")
            .append("    <div class='kpi-card'><div class='kpi-label'>Dòng bị ảnh hưởng</div><div class='kpi-value'>").append(auditLogs.stream().map(AuditLog::getRowNumber).filter(Objects::nonNull).distinct().count()).append("</div></div>\n")
            .append("    <div class='kpi-card'><div class='kpi-label'>Cột bị ảnh hưởng</div><div class='kpi-value'>").append(auditLogs.stream().map(AuditLog::getColumnName).filter(Objects::nonNull).distinct().count()).append("</div></div>\n")
            .append("    <div class='kpi-card'><div class='kpi-label'>Độ đầy đủ dữ liệu</div><div class='kpi-value' style='color:#059669;'>").append(quality.get("completenessPercent")).append("%</div></div>\n")
            .append("    <div class='kpi-card'><div class='kpi-label'>Ô dữ liệu còn trống</div><div class='kpi-value'>").append(quality.get("missingCells")).append("</div></div>\n")
            .append("  </div>\n");

        if (dataset.getBusinessContext() != null || dataset.getDataGoal() != null) {
            html.append("  <section class='report-section context-grid'><div><h2>Bối cảnh</h2><p>")
                    .append(htmlEscape(Objects.toString(dataset.getBusinessContext(), "Chưa cung cấp")))
                    .append("</p></div><div><h2>Mục tiêu báo cáo</h2><p>")
                    .append(htmlEscape(Objects.toString(dataset.getDataGoal(), "Chưa cung cấp")))
                    .append("</p></div></section>\n");
        }
        html.append("  <section class='report-section'><h2>Kết quả phân tích</h2><p class='muted'>Chỉ số: <b>")
                .append(htmlEscape(Objects.toString(latestReport.get("metricCol"), "Chưa chọn")))
                .append("</b> · Phân nhóm: <b>").append(htmlEscape(Objects.toString(latestReport.get("categoryCol"), "Chưa chọn")))
                .append("</b> · Bộ lọc: ").append(htmlEscape(filterSummary(latestReport))).append("</p>");
        Map<String, Map<String, Object>> stats = mapMap(latestReport.get("columnStats"));
        if (!stats.isEmpty()) {
            html.append("<div class='table-wrapper'><table><thead><tr><th>Chỉ số</th><th>Thống kê</th><th>Giá trị</th></tr></thead><tbody>");
            for (Map.Entry<String, Map<String, Object>> col : stats.entrySet()) {
                for (Map.Entry<String, Object> stat : col.getValue().entrySet()) {
                    html.append("<tr><td>").append(htmlEscape(col.getKey())).append("</td><td>")
                            .append(htmlEscape(stat.getKey())).append("</td><td>")
                            .append(htmlEscape(Objects.toString(stat.getValue(), ""))).append("</td></tr>");
                }
            }
            html.append("</tbody></table></div>");
        }
        List<Map<String, Object>> aggregate = mapList(latestReport.get("aggregatedData"));
        if (!aggregate.isEmpty()) {
            double maxValue = aggregate.stream().mapToDouble(item -> parseNumeric(item.get("value"))).max().orElse(0);
            html.append("<div class='bar-chart' role='img' aria-label='Biểu đồ tổng hợp'><h3>")
                    .append(htmlEscape(Objects.toString(latestReport.get("metricCol"), "Số bản ghi"))).append(" theo ")
                    .append(htmlEscape(Objects.toString(latestReport.get("categoryCol"), "nhóm"))).append("</h3>");
            for (Map<String, Object> item : aggregate) {
                double value = parseNumeric(item.get("value"));
                int width = maxValue <= 0 ? 0 : (int) Math.max(2, Math.round(value / maxValue * 100));
                html.append("<div class='bar-row'><span class='bar-label'>").append(htmlEscape(Objects.toString(item.get("category"), "")))
                        .append("</span><span class='bar-track'><span class='bar-fill' style='width:").append(width)
                        .append("%'></span></span><b>").append(htmlEscape(formatNumberForCsv(item.get("value")))).append("</b></div>");
            }
            html.append("</div>");
        }
        appendHtmlList(html, "Nhận định", stringList(latestReport.get("insights")));
        appendHtmlList(html, "Khuyến nghị", stringList(latestReport.get("recommendations")));
        html.append("</section>\n");

        if (!auditLogs.isEmpty()) {
            html.append("  <section class='report-section'><h2>Nhật ký làm sạch &amp; chỉnh sửa</h2><p class='muted'>")
                    .append(auditLogs.size()).append(" thao tác được ghi nhận</p><div class='table-wrapper'><table><thead><tr>")
                    .append("<th>Dòng</th><th>Cột</th><th>Giá trị trước</th><th>Giá trị sau</th><th>Quy tắc / lý do</th><th>Thời điểm</th>")
                    .append("</tr></thead><tbody>");
            DateTimeFormatter auditFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            for (AuditLog item : auditLogs) {
                html.append("<tr><td>").append(Objects.toString(item.getRowNumber(), ""))
                        .append("</td><td>").append(htmlEscape(Objects.toString(item.getColumnName(), "")))
                        .append("</td><td>").append(htmlEscape(Objects.toString(item.getBeforeValue(), "")))
                        .append("</td><td>").append(htmlEscape(Objects.toString(item.getAfterValue(), "")))
                        .append("</td><td>").append(htmlEscape(Objects.toString(item.getChangeReason(), "")))
                        .append("</td><td>").append(item.getCreatedAt() == null ? "" : item.getCreatedAt().format(auditFormat)).append("</td></tr>");
            }
            html.append("</tbody></table></div></section>\n");
        }
        html.append("  <div class='table-card'>\n")
            .append("  <div class='table-card'>\n")
            .append("    <div class='table-toolbar'>\n")
            .append("      <input id='searchInput' class='search-input' type='text' placeholder='🔍 Tìm kiếm nhanh trong bảng...' onkeyup='filterTable()' />\n")
            .append("      <button class='btn-download' onclick='window.print()'>In / Lưu PDF</button>\n")
            .append("      <button class='btn-download' onclick='downloadCSV()'>📥 Tải xuống tệp CSV</button>\n")
            .append("    </div>\n")
            .append("    <div class='table-wrapper'>\n")
            .append("      <table id='dataTable'>\n")
            .append("        <thead><tr>\n");

        for (String col : columns) {
            html.append("          <th>").append(htmlEscape(col)).append("</th>\n");
        }
        html.append("        </tr></thead>\n        <tbody>\n");

        for (Map<String, Object> row : rows) {
            html.append("        <tr>\n");
            for (String col : columns) {
                Object v = row.get(col);
                boolean isNum = (v instanceof Number) || (v != null && v.toString().matches("^-?\\d+(\\.\\d+)?$"));
                String alignClass = isNum ? " class='text-right'" : "";
                html.append("          <td").append(alignClass).append(">").append(htmlEscape(formatNumberForCsv(v))).append("</td>\n");
            }
            html.append("        </tr>\n");
        }

        html.append("        </tbody>\n")
            .append("      </table>\n")
            .append("    </div>\n")
            .append("  </div>\n")
            .append("  <div class='footer-note'>Người tạo: DataLens Studio · Báo cáo được tạo lúc ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
            .append(" · Nền tảng Làm sạch & Phân tích Dữ liệu Doanh nghiệp.</div>\n")
            .append("</div>\n")
            .append("<script>\n")
            .append("  function filterTable() {\n")
            .append("    var input = document.getElementById('searchInput');\n")
            .append("    var filter = input.value.toLowerCase();\n")
            .append("    var rows = document.querySelectorAll('#dataTable tbody tr');\n")
            .append("    rows.forEach(function(row) {\n")
            .append("      var text = row.textContent.toLowerCase();\n")
            .append("      row.style.display = text.indexOf(filter) > -1 ? '' : 'none';\n")
            .append("    });\n")
            .append("  }\n")
            .append("  function downloadCSV() {\n")
            .append("    var rows = document.querySelectorAll('#dataTable tr');\n")
            .append("    var csv = [];\n")
            .append("    rows.forEach(function(r) {\n")
            .append("      var cols = r.querySelectorAll('th, td');\n")
            .append("      var rowData = [];\n")
            .append("      cols.forEach(function(c) {\n")
            .append("        var val = c.innerText.replace(/\"/g, '\"\"');\n")
            .append("        rowData.push('\"' + val + '\"');\n")
            .append("      });\n")
            .append("      csv.push(rowData.join(','));\n")
            .append("    });\n")
            .append("    var blob = new Blob(['\\uFEFF' + csv.join('\\r\\n')], { type: 'text/csv;charset=utf-8;' });\n")
            .append("    var link = document.createElement('a');\n")
            .append("    link.href = URL.createObjectURL(blob);\n")
            .append("    link.download = 'datalens_clean_data.csv';\n")
            .append("    link.click();\n")
            .append("  }\n")
            .append("</script>\n")
            .append("</body>\n</html>\n");

        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Dataset getDataset(Long datasetId) {
        return datasetRepo.findById(datasetId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bộ dữ liệu với mã ID: " + datasetId));
    }

    private XSSFCellStyle createRowStyle(XSSFWorkbook wb, XSSFFont font, Object bg) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFont(font);
        if (bg instanceof byte[] bytes) {
            style.setFillForegroundColor(new XSSFColor(bytes, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        } else if (bg instanceof Short index) {
            style.setFillForegroundColor(index);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(style, BorderStyle.THIN, IndexedColors.GREY_25_PERCENT.getIndex());
        return style;
    }

    private void setBorders(CellStyle style, BorderStyle border, short color) {
        style.setBorderTop(border);
        style.setTopBorderColor(color);
        style.setBorderBottom(border);
        style.setBottomBorderColor(color);
        style.setBorderLeft(border);
        style.setLeftBorderColor(color);
        style.setBorderRight(border);
        style.setRightBorderColor(color);
    }

    private void setCellValueFormatted(Cell cell, Object val, CellStyle intStyle, CellStyle decStyle, CellStyle textStyle) {
        if (val == null || val.toString().trim().isEmpty()) {
            cell.setBlank();
            cell.setCellStyle(textStyle);
            return;
        }

        if (val instanceof Number num) {
            double d = num.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                cell.setCellValue(d);
                cell.setCellStyle(intStyle);
            } else {
                cell.setCellValue(d);
                cell.setCellStyle(decStyle);
            }
            return;
        }

        String str = val.toString().trim();
        // Check if string is actually a formatted integer or decimal
        try {
            if (str.matches("^-?\\d+$")) {
                double d = Double.parseDouble(str);
                if (Math.abs(d) < 1e15) {
                    cell.setCellValue(d);
                    cell.setCellStyle(intStyle);
                    return;
                }
            } else if (str.matches("^-?\\d*\\.\\d+$")) {
                double d = Double.parseDouble(str);
                cell.setCellValue(d);
                cell.setCellStyle(decStyle);
                return;
            }
        } catch (Exception ignored) {}

        cell.setCellValue(str);
        cell.setCellStyle(textStyle);
    }

    private void createCellWithStyle(Row row, int colIndex, Object val, CellStyle style) {
        Cell cell = row.createCell(colIndex);
        if (val instanceof Number num) {
            cell.setCellValue(num.doubleValue());
        } else {
            cell.setCellValue(val != null ? val.toString() : "");
        }
        cell.setCellStyle(style);
    }

    private String formatNumberForCsv(Object v) {
        if (v == null) return "";
        if (v instanceof Number num) {
            double d = num.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return String.format("%.0f", d);
            }
            return String.valueOf(d);
        }
        String str = v.toString();
        // If it's a decimal like "100.0", convert to "100"
        if (str.matches("^-?\\d+\\.0$")) {
            return str.substring(0, str.length() - 2);
        }
        return str;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) if (item instanceof Map<?, ?> map) result.add((Map<String, Object>) map);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> mapMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Collections.emptyMap();
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        map.forEach((key, item) -> {
            if (item instanceof Map<?, ?> nested) result.put(Objects.toString(key), (Map<String, Object>) nested);
        });
        return result;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return Collections.emptyList();
        return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
    }

    private double parseNumeric(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return 0;
        try { return Double.parseDouble(value.toString().trim().replace(",", "")); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private void appendNarrativeRows(Sheet sheet, int startRow, String title, List<String> values, CellStyle style) {
        Row heading = sheet.createRow(startRow);
        heading.createCell(0).setCellValue(title);
        heading.getCell(0).setCellStyle(style);
        int rowIndex = startRow + 1;
        for (String value : values) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue("• " + value);
            row.getCell(0).setCellStyle(style);
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 1));
            row.setHeightInPoints(30);
        }
    }

    private void appendHtmlList(StringBuilder html, String title, List<String> items) {
        if (items.isEmpty()) return;
        html.append("<h3>").append(htmlEscape(title)).append("</h3><ul class='narrative-list'>");
        for (String item : items) html.append("<li>").append(htmlEscape(item)).append("</li>");
        html.append("</ul>");
    }

    private String csvEscape(String val, boolean protectFormula) {
        if (val == null) return "";
        if (protectFormula && val.stripLeading().matches("^[=+@\\-].*")) val = "'" + val;
        if (val.contains(",") || val.contains("\"") || val.contains("\n") || val.contains("\r")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private String filterSummary(Map<?, ?> report) {
        List<String> filters = new ArrayList<>();
        Object dateColumn = report.get("dateCol");
        Object from = report.get("fromDate");
        Object to = report.get("toDate");
        if (dateColumn != null && (!Objects.toString(from, "").isBlank() || !Objects.toString(to, "").isBlank())) {
            filters.add(Objects.toString(dateColumn) + ": " + Objects.toString(from, "—") + " → " + Objects.toString(to, "—"));
        }
        if (report.get("categoryValue") != null && !Objects.toString(report.get("categoryValue"), "").isBlank()) {
            filters.add(Objects.toString(report.get("categoryCol"), "Danh mục") + " = " + report.get("categoryValue"));
        }
        return filters.isEmpty() ? "Không áp dụng" : String.join("; ", filters);
    }

    private Map<?, ?> latestReport(Long datasetId) {
        Map<?, ?> result = reportRepo.findTopByDatasetIdOrderByCreatedAtDesc(datasetId)
                .map(report -> (Map<?, ?>) prettyGson.fromJson(report.getReportJson(), Map.class)).orElse(Collections.emptyMap());
        return result == null ? Collections.emptyMap() : result;
    }

    private Map<String, Object> qualitySummary(List<Map<String, Object>> rows) {
        long cells = 0;
        long missing = 0;
        Set<String> signatures = new HashSet<>();
        long duplicates = 0;
        for (Map<String, Object> row : rows) {
            Map<String, Object> visible = new LinkedHashMap<>();
            row.forEach((key, value) -> { if (!key.startsWith("__")) visible.put(key, value); });
            cells += visible.size();
            missing += visible.values().stream().filter(value -> value == null || value.toString().isBlank()).count();
            if (!signatures.add(visible.toString())) duplicates++;
        }
        double completeness = cells == 0 ? 100.0 : Math.round((1.0 - (double) missing / cells) * 10000.0) / 100.0;
        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("totalCells", cells);
        quality.put("missingCells", missing);
        quality.put("estimatedDuplicates", duplicates);
        quality.put("completenessPercent", completeness);
        return quality;
    }

    private String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
