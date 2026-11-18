package com.receipttracker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ExcelManagerTest {

    @TempDir
    Path tempDir;

    private Path testExcelFile;
    private ExcelManager excelManager;

    @BeforeEach
    void setUp() {
        testExcelFile = tempDir.resolve("test_receipts.xlsx");
        excelManager = new ExcelManager(testExcelFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(testExcelFile);
    }

    @Test
    void testInitializeFileCreatesHeaders() throws IOException {
        assertFalse(Files.exists(testExcelFile));
        excelManager.initializeFile();
        assertTrue(Files.exists(testExcelFile));

        List<Receipt> loaded = excelManager.loadReceipts();
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void testAddAndLoadReceipts() throws IOException {
        Receipt r1 = new Receipt("Rahul", 500.0, "By Hand");
        Receipt r2 = new Receipt("Anu", 750.0, "Online");
        Receipt r3 = new Receipt("Vishnu", 300.0, "By Hand");
        Receipt r4 = new Receipt("Priya", 1000.0, "Online", "Advance payment");

        excelManager.addReceipt(r1);
        excelManager.addReceipt(r2);
        excelManager.addReceipt(r3);
        excelManager.addReceipt(r4);

        List<Receipt> loaded = excelManager.loadReceipts();
        assertEquals(4, loaded.size());

        assertEquals("Rahul", loaded.get(0).getName());
        assertEquals(500.0, loaded.get(0).getAmount(), 0.001);
        assertEquals("By Hand", loaded.get(0).getPaymentMode());
        assertEquals("", loaded.get(0).getDescription());

        assertEquals("Anu", loaded.get(1).getName());
        assertEquals(750.0, loaded.get(1).getAmount(), 0.001);
        assertEquals("Online", loaded.get(1).getPaymentMode());

        assertEquals("Vishnu", loaded.get(2).getName());
        assertEquals(300.0, loaded.get(2).getAmount(), 0.001);
        assertEquals("By Hand", loaded.get(2).getPaymentMode());

        assertEquals("Priya", loaded.get(3).getName());
        assertEquals(1000.0, loaded.get(3).getAmount(), 0.001);
        assertEquals("Online", loaded.get(3).getPaymentMode());
        assertEquals("Advance payment", loaded.get(3).getDescription());
    }

    @Test
    void testDeleteReceipt() throws IOException {
        excelManager.addReceipt(new Receipt("Rahul", 500.0, "By Hand"));
        excelManager.addReceipt(new Receipt("Anu", 750.0, "Online"));
        excelManager.addReceipt(new Receipt("Vishnu", 300.0, "By Hand"));

        // Delete middle receipt (Anu)
        excelManager.deleteReceipt(1);

        List<Receipt> remaining = excelManager.loadReceipts();
        assertEquals(2, remaining.size());
        assertEquals("Rahul", remaining.get(0).getName());
        assertEquals("Vishnu", remaining.get(1).getName());
        assertEquals(300.0, remaining.get(1).getAmount(), 0.001);
    }

    @Test
    void testSaveAllReceipts() throws IOException {
        List<Receipt> list = List.of(
                new Receipt("Alice", 100.0, "By Hand"),
                new Receipt("Bob", 200.0, "Online")
        );
        excelManager.saveAllReceipts(list);

        List<Receipt> reloaded = excelManager.loadReceipts();
        assertEquals(2, reloaded.size());
        assertEquals("Alice", reloaded.get(0).getName());
        assertEquals("Bob", reloaded.get(1).getName());
    }
}
