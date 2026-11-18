package com.receipttracker;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages all Excel (.xlsx) file interactions using Apache POI.
 * Stores transactions with columns: Name, Amount, Transaction Type, Payment Mode.
 * Handles automatic migration from legacy structures with automatic backup.
 */
public class ExcelManager {
    public static final String DEFAULT_FILE_NAME = "receipts.xlsx";
    public static final String SHEET_NAME = "Receipts";
    public static final String[] HEADERS = {"Name", "Amount", "Transaction Type", "Payment Mode"};

    private final Path filePath;

    public ExcelManager() {
        this(resolveDefaultPath());
    }

    public static Path resolveDefaultPath() {
        Path fullRoot = Paths.get("c:/Users/NIVED/.gemini/antigravity/scratch/ReceiptTracker/receipts.xlsx");
        if (Files.exists(fullRoot)) return fullRoot;

        Path p1 = Paths.get("receipts.xlsx");
        if (Files.exists(p1)) return p1;

        Path p2 = Paths.get("src/main/java/com/receipttracker/receipts.xlsx");
        if (Files.exists(p2)) return p2;

        return Paths.get(DEFAULT_FILE_NAME);
    }

    public ExcelManager(Path filePath) {
        this.filePath = filePath;
    }

    public Path getFilePath() {
        return filePath;
    }

    /**
     * Initializes the Excel file. If legacy structure is detected,
     * it automatically backs up the file and migrates to:
     * Name | Amount | Transaction Type | Payment Mode
     */
    public synchronized void initializeFile() throws IOException {
        if (!Files.exists(filePath) || Files.size(filePath) == 0) {
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet(SHEET_NAME);
                createHeaderRow(workbook, sheet);
                sheet.createFreezePane(0, 1);
                autoSizeColumns(sheet);
                saveWorkbookSafely(workbook);
            }
            return;
        }

        // Inspect existing file
        boolean needsMigration = false;
        List<Receipt> existingRecords = new ArrayList<>();

        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0 || sheet.getRow(0) == null) {
                needsMigration = true;
            } else {
                Row headerRow = sheet.getRow(0);
                String col0 = getCellStringValue(headerRow.getCell(0)).toLowerCase();
                String col1 = getCellStringValue(headerRow.getCell(1)).toLowerCase();
                String col2 = getCellStringValue(headerRow.getCell(2)).toLowerCase();
                String col3 = getCellStringValue(headerRow.getCell(3)).toLowerCase();

                // If column 2 is already "transaction type", no migration needed
                if (!col2.contains("transaction") && !col2.contains("type")) {
                    needsMigration = true;
                    // Read records from legacy layout
                    int lastRowNum = sheet.getLastRowNum();
                    for (int r = 1; r <= lastRowNum; r++) {
                        Row row = sheet.getRow(r);
                        if (row == null) continue;

                        String name = getCellStringValue(row.getCell(0));
                        Double amount = getCellNumericValue(row.getCell(1));
                        String mode = getCellStringValue(row.getCell(2));
                        String desc = getCellStringValue(row.getCell(3));

                        if (name.isEmpty() && amount == null) continue;

                        double amt = (amount != null) ? amount : 0.0;
                        String type = (amt < 0) ? Receipt.TYPE_WITHDRAWN : Receipt.TYPE_RECEIVED;
                        if (mode.isEmpty()) mode = Receipt.MODE_BY_HAND;

                        existingRecords.add(new Receipt(name, amt, type, mode, desc));
                    }
                }
            }
        } catch (IOException e) {
            if (isFileLockedException(e)) {
                throw new IOException("Unable to access the Excel file. Please close " +
                        filePath.getFileName() + " if it is open in Microsoft Excel or another program.", e);
            }
            throw e;
        }

        if (needsMigration) {
            // Step 1: Create backup of original file
            createBackupCopy();

            // Step 2: Rewrite cleanly with new schema
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet(SHEET_NAME);
                createHeaderRow(workbook, sheet);
                sheet.createFreezePane(0, 1);

                CellStyle amountStyle = workbook.createCellStyle();
                DataFormat dataFormat = workbook.createDataFormat();
                amountStyle.setDataFormat(dataFormat.getFormat("#,##0.00;[Red]-#,##0.00"));

                int rowIdx = 1;
                for (Receipt r : existingRecords) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(r.getName());

                    Cell cAmt = row.createCell(1);
                    double val = r.isWithdrawn() ? -Math.abs(r.getAmount()) : Math.abs(r.getAmount());
                    cAmt.setCellValue(val);
                    cAmt.setCellStyle(amountStyle);

                    row.createCell(2).setCellValue(r.getTransactionType());
                    row.createCell(3).setCellValue(r.getPaymentMode());
                }

                autoSizeColumns(sheet);
                saveWorkbookSafely(workbook);
                System.out.println("Excel file successfully migrated to new schema with backup: " + filePath);
            }
        }
    }

    private void createBackupCopy() {
        try {
            if (Files.exists(filePath) && Files.size(filePath) > 0) {
                String fn = filePath.getFileName().toString();
                String baseName = fn.endsWith(".xlsx") ? fn.substring(0, fn.length() - 5) : fn;
                Path backup = filePath.resolveSibling(baseName + "_backup_" + System.currentTimeMillis() + ".xlsx");
                Files.copy(filePath, backup, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Created safe backup before schema migration: " + backup.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("Warning: could not create backup copy: " + e.getMessage());
        }
    }

    /**
     * Loads all existing receipts/transactions from the Excel file.
     */
    public synchronized List<Receipt> loadReceipts() throws IOException {
        initializeFile();

        List<Receipt> list = new ArrayList<>();
        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) return list;

            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) return list;

            // Detect column indices from header row
            Row headerRow = sheet.getRow(0);
            int nameCol = 0;
            int amountCol = 1;
            int typeCol = 2;
            int modeCol = 3;

            if (headerRow != null) {
                int cellCount = headerRow.getLastCellNum();
                for (int c = 0; c < cellCount; c++) {
                    String h = getCellStringValue(headerRow.getCell(c)).toLowerCase();
                    if (h.contains("name")) nameCol = c;
                    else if (h.contains("amount")) amountCol = c;
                    else if (h.contains("type")) typeCol = c;
                    else if (h.contains("mode")) modeCol = c;
                }
            }

            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String name = getCellStringValue(row.getCell(nameCol));
                Double amount = getCellNumericValue(row.getCell(amountCol));
                String type = (typeCol >= 0) ? getCellStringValue(row.getCell(typeCol)) : "";
                String mode = (modeCol >= 0) ? getCellStringValue(row.getCell(modeCol)) : "";

                if (name.isEmpty() && amount == null) continue;

                double rawAmount = (amount != null) ? amount : 0.0;

                // Determine transaction type and signed amount
                if (type.isEmpty()) {
                    type = (rawAmount < 0) ? Receipt.TYPE_WITHDRAWN : Receipt.TYPE_RECEIVED;
                }

                double signedAmount;
                if (type.equalsIgnoreCase(Receipt.TYPE_WITHDRAWN) || rawAmount < 0) {
                    type = Receipt.TYPE_WITHDRAWN;
                    signedAmount = -Math.abs(rawAmount);
                } else {
                    type = Receipt.TYPE_RECEIVED;
                    signedAmount = Math.abs(rawAmount);
                }

                if (mode.isEmpty()) mode = Receipt.MODE_BY_HAND;

                list.add(new Receipt(name, signedAmount, type, mode));
            }
        } catch (IOException e) {
            if (isFileLockedException(e)) {
                throw new IOException("Unable to open the Excel file. Please close " +
                        filePath.getFileName() + " if it is open in Microsoft Excel.", e);
            }
            throw e;
        }
        return list;
    }

    /**
     * Appends a new transaction to the Excel file.
     * When Amount Received: stores positive numeric value.
     * When Amount Withdrawn: stores negative numeric value.
     */
    public synchronized void addReceipt(Receipt receipt) throws IOException {
        initializeFile();

        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                sheet = workbook.createSheet(SHEET_NAME);
                createHeaderRow(workbook, sheet);
            }

            int nextRow = sheet.getLastRowNum() + 1;
            if (sheet.getPhysicalNumberOfRows() == 0 || nextRow == 0) {
                createHeaderRow(workbook, sheet);
                nextRow = 1;
            }

            Row row = sheet.createRow(nextRow);

            // Name (Column 0)
            Cell cellName = row.createCell(0);
            cellName.setCellValue(receipt.getName());

            // Amount (Column 1 - Numeric signed value)
            Cell cellAmount = row.createCell(1);
            double numericAmount = receipt.isWithdrawn() ? -Math.abs(receipt.getAmount()) : Math.abs(receipt.getAmount());
            cellAmount.setCellValue(numericAmount);

            CellStyle amountStyle = workbook.createCellStyle();
            DataFormat dataFormat = workbook.createDataFormat();
            amountStyle.setDataFormat(dataFormat.getFormat("#,##0.00;[Red]-#,##0.00"));
            cellAmount.setCellStyle(amountStyle);

            // Transaction Type (Column 2)
            Cell cellType = row.createCell(2);
            cellType.setCellValue(receipt.getTransactionType());

            // Payment Mode (Column 3)
            Cell cellMode = row.createCell(3);
            cellMode.setCellValue(receipt.getPaymentMode());

            autoSizeColumns(sheet);
            saveWorkbookSafely(workbook);
        }
    }

    /**
     * Deletes a transaction at the given 0-based data index.
     */
    public synchronized void deleteReceipt(int dataIndex) throws IOException {
        initializeFile();

        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) return;

            int excelRowIndex = dataIndex + 1;
            int lastRowNum = sheet.getLastRowNum();

            if (excelRowIndex < 1 || excelRowIndex > lastRowNum) {
                throw new IndexOutOfBoundsException("Invalid transaction index: " + dataIndex);
            }

            Row removingRow = sheet.getRow(excelRowIndex);
            if (removingRow != null) {
                sheet.removeRow(removingRow);
            }

            if (excelRowIndex < lastRowNum) {
                sheet.shiftRows(excelRowIndex + 1, lastRowNum, -1);
            }

            autoSizeColumns(sheet);
            saveWorkbookSafely(workbook);
        }
    }

    /**
     * Rewrites all transactions to the Excel file cleanly.
     */
    public synchronized void saveAllReceipts(List<Receipt> receipts) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            createHeaderRow(workbook, sheet);
            sheet.createFreezePane(0, 1);

            CellStyle amountStyle = workbook.createCellStyle();
            DataFormat dataFormat = workbook.createDataFormat();
            amountStyle.setDataFormat(dataFormat.getFormat("#,##0.00;[Red]-#,##0.00"));

            int rowIdx = 1;
            for (Receipt receipt : receipts) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(receipt.getName());

                Cell cellAmount = row.createCell(1);
                double numericVal = receipt.isWithdrawn() ? -Math.abs(receipt.getAmount()) : Math.abs(receipt.getAmount());
                cellAmount.setCellValue(numericVal);
                cellAmount.setCellStyle(amountStyle);

                row.createCell(2).setCellValue(receipt.getTransactionType());
                row.createCell(3).setCellValue(receipt.getPaymentMode());
            }

            autoSizeColumns(sheet);
            saveWorkbookSafely(workbook);
        }
    }

    private void createHeaderRow(Workbook workbook, Sheet sheet) {
        Row headerRow = sheet.createRow(0);

        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 11);

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void autoSizeColumns(Sheet sheet) {
        for (int i = 0; i < HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
            int currentWidth = sheet.getColumnWidth(i);
            int minWidth = 16 * 256;
            if (currentWidth < minWidth) {
                sheet.setColumnWidth(i, minWidth);
            }
        }
    }

    private void saveWorkbookSafely(Workbook workbook) throws IOException {
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        try {
            try (OutputStream os = Files.newOutputStream(tempFile)) {
                workbook.write(os);
            }
            try {
                Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception ignored) {}

            if (isFileLockedException(e)) {
                throw new IOException("Unable to save the Excel file. Please close " +
                        filePath.getFileName() + " if it is open in Microsoft Excel or another program.", e);
            }
            throw e;
        }
    }

    private boolean isFileLockedException(IOException e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        msg = msg.toLowerCase();
        return msg.contains("used by another process") ||
                msg.contains("access is denied") ||
                msg.contains("lock") ||
                msg.contains("being used");
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == (long) val) {
                    yield String.valueOf((long) val);
                } else {
                    yield String.valueOf(val);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private Double getCellNumericValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Double.parseDouble(cell.getStringCellValue().trim().replace(",", ""));
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }
}
