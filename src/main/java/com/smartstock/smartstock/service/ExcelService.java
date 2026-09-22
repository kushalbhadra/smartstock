package com.smartstock.smartstock.service;

import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.entity.Stock;
import com.smartstock.smartstock.entity.StockMovement;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.StockMovementRepository;
import com.smartstock.smartstock.repository.StockRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ExcelService {

    @Autowired private ProductRepository productRepository;
    @Autowired private StockRepository stockRepository;
    @Autowired private StockMovementRepository movementRepository;

    // ── EXPORT PRODUCTS ───────────────────────────────────────────────

    public byte[] exportProducts() throws IOException {
        List<Product> products = productRepository.findAll();

        // Create workbook (the Excel file)
        Workbook workbook = new XSSFWorkbook();

        // Create sheet inside workbook
        Sheet sheet = workbook.createSheet("Products");

        // ── Header row ──
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);

        String[] headers = {"ID", "Name", "SKU",
                "Category", "Unit", "Price (₹)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // ── Data rows ──
        int rowNum = 1;
        for (Product p : products) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(p.getId());
            row.createCell(1).setCellValue(p.getName());
            row.createCell(2).setCellValue(p.getSku());
            row.createCell(3).setCellValue(p.getCategory());
            row.createCell(4).setCellValue(p.getUnit());
            row.createCell(5).setCellValue(
                    p.getPrice().doubleValue()
            );
        }

        // Auto-size all columns for clean look
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        return toByteArray(workbook);
    }

    // ── EXPORT STOCK LEVELS ───────────────────────────────────────────

    public byte[] exportStocks() throws IOException {
        List<Stock> stocks = stockRepository.findAll();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Stock Levels");

        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);

        String[] headers = {"Stock ID", "Product",
                "Warehouse", "Quantity",
                "Min Qty", "Max Qty", "Status"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (Stock s : stocks) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(s.getId());
            row.createCell(1).setCellValue(
                    s.getProduct().getName()
            );
            row.createCell(2).setCellValue(
                    s.getWarehouse().getName()
            );
            row.createCell(3).setCellValue(s.getQuantity());
            row.createCell(4).setCellValue(s.getMinQuantity());
            row.createCell(5).setCellValue(s.getMaxQuantity());
            row.createCell(6).setCellValue(
                    getStockStatus(s)
            );
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        return toByteArray(workbook);
    }

    // ── EXPORT MOVEMENTS ──────────────────────────────────────────────

    public byte[] exportMovements() throws IOException {
        List<StockMovement> movements =
                movementRepository.findAll();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Stock Movements");

        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);

        String[] headers = {"ID", "Product", "Warehouse",
                "Type", "Quantity", "Reason",
                "Performed By", "Date"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (StockMovement m : movements) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(m.getId());
            row.createCell(1).setCellValue(
                    m.getStock().getProduct().getName()
            );
            row.createCell(2).setCellValue(
                    m.getStock().getWarehouse().getName()
            );
            row.createCell(3).setCellValue(
                    m.getMovementType().name()
            );
            row.createCell(4).setCellValue(m.getQuantity());
            row.createCell(5).setCellValue(m.getReason());
            row.createCell(6).setCellValue(
                    m.getPerformedBy().getName()
            );
            row.createCell(7).setCellValue(
                    m.getCreatedAt().toString()
            );
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        return toByteArray(workbook);
    }

    // ── IMPORT PRODUCTS FROM EXCEL ────────────────────────────────────

    public ImportResult importProducts(byte[] fileBytes)
            throws IOException {

        Workbook workbook = new XSSFWorkbook(
                new java.io.ByteArrayInputStream(fileBytes)
        );
        Sheet sheet = workbook.getSheetAt(0);

        int success = 0;
        int failed = 0;
        StringBuilder errors = new StringBuilder();

        // Start from row 1 — skip header row (row 0)
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            try {
                String name = getCellString(row, 0);
                String sku  = getCellString(row, 1);
                String cat  = getCellString(row, 2);
                String unit = getCellString(row, 3);
                double price = row.getCell(4).getNumericCellValue();

                // Validate required fields
                if (name.isEmpty() || sku.isEmpty()) {
                    errors.append("Row ").append(i + 1)
                            .append(": Name and SKU required\n");
                    failed++;
                    continue;
                }

                // Skip if SKU already exists
                if (productRepository.existsBySku(sku)) {
                    errors.append("Row ").append(i + 1)
                            .append(": SKU ").append(sku)
                            .append(" already exists\n");
                    failed++;
                    continue;
                }

                Product product = Product.builder()
                        .name(name)
                        .sku(sku.toUpperCase())
                        .category(cat)
                        .unit(unit)
                        .price(java.math.BigDecimal.valueOf(price))
                        .build();

                productRepository.save(product);
                success++;

            } catch (Exception e) {
                errors.append("Row ").append(i + 1)
                        .append(": ").append(e.getMessage())
                        .append("\n");
                failed++;
            }
        }

        workbook.close();
        return new ImportResult(success, failed, errors.toString());
    }

    // ── DOWNLOAD TEMPLATE ─────────────────────────────────────────────

    public byte[] generateProductTemplate() throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Products Template");

        Row headerRow = sheet.createRow(0);
        CellStyle style = createHeaderStyle(workbook);

        String[] headers = {"Name", "SKU", "Category",
                "Unit", "Price"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
            sheet.setColumnWidth(i, 5000);
        }

        // Example data row
        Row example = sheet.createRow(1);
        example.createCell(0).setCellValue("Laptop 15 inch");
        example.createCell(1).setCellValue("ELEC001");
        example.createCell(2).setCellValue("Electronics");
        example.createCell(3).setCellValue("piece");
        example.createCell(4).setCellValue(45000);

        return toByteArray(workbook);
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        // Bold font
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);

        // Blue background
        style.setFillForegroundColor(
                IndexedColors.CORNFLOWER_BLUE.getIndex()
        );
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Border
        style.setBorderBottom(BorderStyle.THIN);

        return style;
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf(
                    (long) cell.getNumericCellValue()
            );
            default -> "";
        };
    }

    private String getStockStatus(Stock s) {
        if (s.getQuantity() < s.getMinQuantity() * 0.5)
            return "CRITICAL";
        if (s.getQuantity() < s.getMinQuantity())
            return "LOW";
        if (s.getQuantity() > s.getMaxQuantity())
            return "OVERSTOCK";
        return "OK";
    }

    private byte[] toByteArray(Workbook workbook)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();
        return out.toByteArray();
    }

    // Result of import operation
    public record ImportResult(
            int successCount,
            int failedCount,
            String errors
    ) {}
}