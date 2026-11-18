package com.receipttracker;

import flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Main application window for the Receipt Collection & Expense Tracker.
 * Designed for rapid entry directly synchronized to Excel (.xlsx).
 */
public class ReceiptTracker extends JFrame {

    // Models & Data
    private final ExcelManager excelManager;
    private final List<Receipt> receiptsList = new ArrayList<>();
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> rowSorter;

    // UI Input Components
    private JTextField txtName;
    private JComboBox<String> cmbTransactionType;
    private JTextField txtAmount;
    private JComboBox<String> cmbPaymentMode;
    private JButton btnAddReceipt;
    private JButton btnClearInput;

    // Search Component
    private JTextField txtSearch;
    private JButton btnSearch;
    private JButton btnClearSearch;

    // Table & Actions
    private JTable receiptsTable;
    private JButton btnDeleteSelected;

    // 5 Totals Displays
    private JLabel lblTotalReceived;
    private JLabel lblTotalWithdrawn;
    private JLabel lblByHandBalance;
    private JLabel lblOnlineBalance;
    private JLabel lblAvailableBalance;

    // Status / Feedback
    private JLabel lblStatus;

    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.00");

    public ReceiptTracker() {
        this(Paths.get(ExcelManager.DEFAULT_FILE_NAME));
    }

    public ReceiptTracker(Path excelPath) {
        super("Receipt Collection & Expense Tracker");
        this.excelManager = new ExcelManager(excelPath);

        initUI();
        loadExistingData();

        SwingUtilities.invokeLater(() -> txtName.requestFocusInWindow());
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(950, 700));
        setSize(1020, 750);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 12));
        mainPanel.setBorder(new EmptyBorder(16, 20, 16, 20));
        mainPanel.setBackground(new Color(245, 247, 250));

        // 1. Header
        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);

        // 2. Center Content (Inputs + Table)
        JPanel centerContent = new JPanel(new BorderLayout(0, 12));
        centerContent.setOpaque(false);
        centerContent.add(createInputPanel(), BorderLayout.NORTH);
        centerContent.add(createTableSectionPanel(), BorderLayout.CENTER);
        mainPanel.add(centerContent, BorderLayout.CENTER);

        // 3. Footer (5 Totals + Status)
        mainPanel.add(createFooterPanel(), BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(218, 224, 233)),
                new EmptyBorder(0, 0, 10, 0)));

        JLabel lblTitle = new JLabel("RECEIPT & EXPENSE TRACKER");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitle.setForeground(new Color(18, 38, 68));

        JLabel lblSub = new JLabel("Fast Collection, Withdrawal & Balance Ledger");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(100, 116, 139));

        panel.add(lblTitle, BorderLayout.WEST);
        panel.add(lblSub, BorderLayout.EAST);
        return panel;
    }

    private JPanel createInputPanel() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(222, 226, 230), 1, true),
                new EmptyBorder(14, 18, 14, 18)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Labels
        JLabel lblName = new JLabel("Name / Description:");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.35;
        card.add(lblName, gbc);

        JLabel lblType = new JLabel("Transaction Type:");
        lblType.setFont(new Font("Segoe UI", Font.BOLD, 13));
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.22;
        card.add(lblType, gbc);

        JLabel lblAmount = new JLabel("Amount (₹):");
        lblAmount.setFont(new Font("Segoe UI", Font.BOLD, 13));
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 0.20;
        card.add(lblAmount, gbc);

        JLabel lblMode = new JLabel("Payment Mode:");
        lblMode.setFont(new Font("Segoe UI", Font.BOLD, 13));
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.weightx = 0.23;
        card.add(lblMode, gbc);

        // Row 1: Controls
        txtName = new JTextField();
        txtName.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtName.setPreferredSize(new Dimension(200, 34));
        txtName.setToolTipText("Enter person's name or expense description");
        gbc.gridx = 0;
        gbc.gridy = 1;
        card.add(txtName, gbc);

        cmbTransactionType = new JComboBox<>(new String[] { "Amount Received", "Amount Withdrawn" });
        cmbTransactionType.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cmbTransactionType.setPreferredSize(new Dimension(150, 34));
        gbc.gridx = 1;
        gbc.gridy = 1;
        card.add(cmbTransactionType, gbc);

        txtAmount = new JTextField();
        txtAmount.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtAmount.setPreferredSize(new Dimension(120, 34));
        txtAmount.setToolTipText("Enter positive amount in Rupees (e.g. 500)");
        gbc.gridx = 2;
        gbc.gridy = 1;
        card.add(txtAmount, gbc);

        cmbPaymentMode = new JComboBox<>(new String[] { Receipt.MODE_BY_HAND, Receipt.MODE_ONLINE });
        cmbPaymentMode.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cmbPaymentMode.setPreferredSize(new Dimension(130, 34));
        cmbPaymentMode.setToolTipText("Select By Hand or Online (← / → keys)");
        gbc.gridx = 3;
        gbc.gridy = 1;
        card.add(cmbPaymentMode, gbc);

        // Row 2: Action Buttons
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonRow.setOpaque(false);

        btnAddReceipt = new JButton("ADD TRANSACTION");
        btnAddReceipt.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnAddReceipt.setBackground(new Color(24, 134, 75)); // Forest green
        btnAddReceipt.setForeground(Color.WHITE);
        btnAddReceipt.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnAddReceipt.setPreferredSize(new Dimension(160, 36));

        btnClearInput = new JButton("CLEAR");
        btnClearInput.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnClearInput.setPreferredSize(new Dimension(90, 36));

        buttonRow.add(btnAddReceipt);
        buttonRow.add(btnClearInput);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 4;
        gbc.insets = new Insets(10, 8, 2, 8);
        card.add(buttonRow, gbc);

        // Event Wiring
        btnAddReceipt.addActionListener(e -> onAddReceipt());
        btnClearInput.addActionListener(e -> onClearInput());

        txtName.addActionListener(e -> txtAmount.requestFocusInWindow());
        txtAmount.addActionListener(e -> onAddReceipt());

        KeyAdapter keyListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    onClearInput();
                } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    if (e.getSource() == txtName && txtName.getText().length() > 0 && txtName.getCaretPosition() > 0) {
                        return;
                    }
                    cmbPaymentMode.setSelectedItem(Receipt.MODE_BY_HAND);
                    setStatus("Payment Mode: By Hand (←)");
                } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    if (e.getSource() == txtName && txtName.getText().length() > 0
                            && txtName.getCaretPosition() < txtName.getText().length()) {
                        return;
                    }
                    cmbPaymentMode.setSelectedItem(Receipt.MODE_ONLINE);
                    setStatus("Payment Mode: Online (→)");
                }
            }
        };
        txtName.addKeyListener(keyListener);
        txtAmount.addKeyListener(keyListener);
        cmbPaymentMode.addKeyListener(keyListener);
        cmbTransactionType.addKeyListener(keyListener);

        return card;
    }

    private JPanel createTableSectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);

        // Header bar with search
        JPanel tableHeaderBar = new JPanel(new BorderLayout(10, 0));
        tableHeaderBar.setOpaque(false);

        JLabel lblReceiptsHeader = new JLabel("TRANSACTION HISTORY");
        lblReceiptsHeader.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblReceiptsHeader.setForeground(new Color(30, 41, 59));
        tableHeaderBar.add(lblReceiptsHeader, BorderLayout.WEST);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        searchPanel.setOpaque(false);

        JLabel lblSearch = new JLabel("Search Name:");
        lblSearch.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        txtSearch = new JTextField();
        txtSearch.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtSearch.setPreferredSize(new Dimension(170, 30));

        btnSearch = new JButton("SEARCH");
        btnSearch.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        btnClearSearch = new JButton("RESET");
        btnClearSearch.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        searchPanel.add(lblSearch);
        searchPanel.add(txtSearch);
        searchPanel.add(btnSearch);
        searchPanel.add(btnClearSearch);
        tableHeaderBar.add(searchPanel, BorderLayout.EAST);

        panel.add(tableHeaderBar, BorderLayout.NORTH);

        // Table Model: Name | Amount | Transaction Type | Payment Mode
        String[] columnNames = { "Name", "Amount", "Transaction Type", "Payment Mode" };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 1)
                    return Double.class;
                return String.class;
            }
        };

        receiptsTable = new JTable(tableModel);
        receiptsTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        receiptsTable.setRowHeight(28);
        receiptsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        receiptsTable.setShowGrid(true);
        receiptsTable.setGridColor(new Color(230, 235, 242));
        receiptsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        receiptsTable.getTableHeader().setBackground(new Color(241, 245, 249));
        receiptsTable.getTableHeader().setForeground(new Color(51, 65, 85));
        receiptsTable.getTableHeader().setReorderingAllowed(false);

        // Format Amount column with currency, sign, and color
        DefaultTableCellRenderer amountRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value instanceof Number) {
                    double num = ((Number) value).doubleValue();
                    if (num < 0) {
                        setText("-₹ " + AMOUNT_FORMAT.format(Math.abs(num)));
                        if (!isSelected)
                            setForeground(new Color(195, 30, 30));
                    } else {
                        setText("+₹ " + AMOUNT_FORMAT.format(num));
                        if (!isSelected)
                            setForeground(new Color(24, 134, 75));
                    }
                }
                setHorizontalAlignment(SwingConstants.RIGHT);
                return c;
            }
        };
        receiptsTable.getColumnModel().getColumn(1).setCellRenderer(amountRenderer);

        // Center align Transaction Type & Payment Mode
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        receiptsTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        receiptsTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);

        receiptsTable.getColumnModel().getColumn(0).setPreferredWidth(240);
        receiptsTable.getColumnModel().getColumn(1).setPreferredWidth(130);
        receiptsTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        receiptsTable.getColumnModel().getColumn(3).setPreferredWidth(130);

        rowSorter = new TableRowSorter<>(tableModel);
        receiptsTable.setRowSorter(rowSorter);

        JScrollPane scrollPane = new JScrollPane(receiptsTable);
        scrollPane.setBorder(new LineBorder(new Color(222, 226, 230), 1, true));
        scrollPane.getViewport().setBackground(Color.WHITE);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Bottom Table Controls (Delete Selected)
        JPanel tableBottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        tableBottomBar.setOpaque(false);

        btnDeleteSelected = new JButton("DELETE SELECTED");
        btnDeleteSelected.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnDeleteSelected.setForeground(new Color(180, 40, 40));
        btnDeleteSelected.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnDeleteSelected.addActionListener(e -> onDeleteSelected());

        tableBottomBar.add(btnDeleteSelected);
        panel.add(tableBottomBar, BorderLayout.SOUTH);

        btnSearch.addActionListener(e -> applySearchFilter());
        txtSearch.addActionListener(e -> applySearchFilter());
        btnClearSearch.addActionListener(e -> {
            txtSearch.setText("");
            rowSorter.setRowFilter(null);
            setStatus("Search cleared.");
        });

        return panel;
    }

    private JPanel createFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setOpaque(false);

        // 5 Summary Totals Cards
        JPanel totalsCard = new JPanel(new GridLayout(1, 5, 10, 0));
        totalsCard.setBackground(Color.WHITE);
        totalsCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(222, 226, 230), 1, true),
                new EmptyBorder(12, 14, 12, 14)));

        lblTotalReceived = createTotalLabel("Received: ₹0.00", new Color(180, 130, 20)); // Gold
        lblTotalWithdrawn = createTotalLabel("Withdrawn: ₹0.00", new Color(195, 30, 30)); // Red
        lblByHandBalance = createTotalLabel("Hand Bal: ₹0.00", new Color(50, 50, 50));
        lblOnlineBalance = createTotalLabel("Online Bal: ₹0.00", new Color(24, 134, 75)); // Green
        lblAvailableBalance = createTotalLabel("AVAILABLE: ₹0.00", new Color(175, 125, 10)); // Hero Gold
        lblAvailableBalance.setFont(new Font("Segoe UI", Font.BOLD, 16));

        totalsCard.add(lblTotalReceived);
        totalsCard.add(lblTotalWithdrawn);
        totalsCard.add(lblByHandBalance);
        totalsCard.add(lblOnlineBalance);
        totalsCard.add(lblAvailableBalance);
        footer.add(totalsCard, BorderLayout.NORTH);

        // Status Bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setOpaque(false);

        lblStatus = new JLabel("Ready. Enter details or record transaction.");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(new Color(100, 116, 139));
        statusBar.add(lblStatus, BorderLayout.WEST);

        JLabel lblStorage = new JLabel("Excel: " + excelManager.getFilePath().getFileName());
        lblStorage.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblStorage.setForeground(new Color(140, 150, 165));
        statusBar.add(lblStorage, BorderLayout.EAST);

        footer.add(statusBar, BorderLayout.SOUTH);
        return footer;
    }

    private JLabel createTotalLabel(String initialText, Color fgColor) {
        JLabel label = new JLabel(initialText, SwingConstants.CENTER);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(fgColor);
        return label;
    }

    // ==========================================
    // DATA LOGIC & ACTIONS
    // ==========================================

    private void loadExistingData() {
        try {
            List<Receipt> loaded = excelManager.loadReceipts();
            receiptsList.clear();
            tableModel.setRowCount(0);

            for (Receipt r : loaded) {
                receiptsList.add(r);
                tableModel.addRow(
                        new Object[] { r.getName(), r.getAmount(), r.getTransactionType(), r.getPaymentMode() });
            }

            recalculateTotals();
            setStatus(String.format("Loaded %d existing transactions from Excel.", receiptsList.size()));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to load records from " + excelManager.getFilePath().getFileName() + ":\n" + e.getMessage(),
                    "Excel Read Error",
                    JOptionPane.ERROR_MESSAGE);
            setStatus("Error reading Excel data.");
        }
    }

    private void onAddReceipt() {
        String name = txtName.getText().trim();
        String amountText = txtAmount.getText().trim();
        String selectedType = (String) cmbTransactionType.getSelectedItem();
        String paymentMode = (String) cmbPaymentMode.getSelectedItem();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a name or expense description.", "Validation Error",
                    JOptionPane.WARNING_MESSAGE);
            txtName.requestFocusInWindow();
            return;
        }

        if (amountText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter an amount.", "Validation Error",
                    JOptionPane.WARNING_MESSAGE);
            txtAmount.requestFocusInWindow();
            return;
        }

        double enteredAmount;
        try {
            String sanitized = amountText.replace("₹", "").replace(",", "").replace("-", "").trim();
            enteredAmount = Double.parseDouble(sanitized);
            if (enteredAmount <= 0) {
                JOptionPane.showMessageDialog(this, "Amount must be greater than zero.", "Validation Error",
                        JOptionPane.WARNING_MESSAGE);
                txtAmount.requestFocusInWindow();
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Please enter a valid numeric amount.", "Validation Error",
                    JOptionPane.WARNING_MESSAGE);
            txtAmount.requestFocusInWindow();
            return;
        }

        boolean isWithdrawal = selectedType != null && selectedType.contains("Withdrawn");
        String finalType = isWithdrawal ? Receipt.TYPE_WITHDRAWN : Receipt.TYPE_RECEIVED;
        double signedAmount = isWithdrawal ? -Math.abs(enteredAmount) : Math.abs(enteredAmount);

        if (paymentMode == null
                || (!paymentMode.equals(Receipt.MODE_BY_HAND) && !paymentMode.equals(Receipt.MODE_ONLINE))) {
            paymentMode = Receipt.MODE_BY_HAND;
        }

        Receipt newReceipt = new Receipt(name, signedAmount, finalType, paymentMode);

        try {
            excelManager.addReceipt(newReceipt);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    this,
                    e.getMessage(),
                    "Excel Save Error",
                    JOptionPane.ERROR_MESSAGE);
            setStatus("Failed to save transaction to Excel.");
            return;
        }

        receiptsList.add(newReceipt);
        tableModel.addRow(new Object[] { newReceipt.getName(), newReceipt.getAmount(), newReceipt.getTransactionType(),
                newReceipt.getPaymentMode() });

        recalculateTotals();

        txtName.setText("");
        txtAmount.setText("");
        cmbTransactionType.setSelectedIndex(0); // Reset to Amount Received
        txtName.requestFocusInWindow();

        setStatus("Transaction added successfully.");
    }

    private void onDeleteSelected() {
        int selectedViewRow = receiptsTable.getSelectedRow();
        if (selectedViewRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a record to delete.", "No Selection",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = receiptsTable.convertRowIndexToModel(selectedViewRow);
        if (modelRow < 0 || modelRow >= receiptsList.size()) {
            return;
        }

        Receipt target = receiptsList.get(modelRow);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                String.format(
                        "Are you sure you want to delete this record?\n\nName: %s\nAmount: ₹%.2f\nType: %s\nMode: %s",
                        target.getName(), Math.abs(target.getAmount()), target.getTransactionType(),
                        target.getPaymentMode()),
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            excelManager.deleteReceipt(modelRow);
            receiptsList.remove(modelRow);
            tableModel.removeRow(modelRow);
            recalculateTotals();
            setStatus("Transaction deleted successfully.");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to delete record from Excel:\n" + e.getMessage(),
                    "Excel Error",
                    JOptionPane.ERROR_MESSAGE);
            setStatus("Deletion failed.");
        }
    }

    private void onClearInput() {
        txtName.setText("");
        txtAmount.setText("");
        cmbTransactionType.setSelectedIndex(0);
        txtName.requestFocusInWindow();
        setStatus("Input fields cleared.");
    }

    private void applySearchFilter() {
        String text = txtSearch.getText().trim();
        if (text.isEmpty()) {
            rowSorter.setRowFilter(null);
            setStatus("Search reset.");
        } else {
            rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + PatternQuote(text), 0));
            setStatus("Filtering by name: \"" + text + "\"");
        }
    }

    private String PatternQuote(String s) {
        return java.util.regex.Pattern.quote(s);
    }

    /**
     * Recalculates all 5 financial balances:
     * - TOTAL RECEIVED: Sum of all positive amounts.
     * - TOTAL WITHDRAWN: Absolute sum of all negative amounts.
     * - BY HAND BALANCE: Net sum where Payment Mode = By Hand.
     * - ONLINE BALANCE: Net sum where Payment Mode = Online.
     * - AVAILABLE BALANCE: Total Received - Total Withdrawn.
     */
    private void recalculateTotals() {
        double totalReceived = 0.0;
        double totalWithdrawn = 0.0;
        double byHandBalance = 0.0;
        double onlineBalance = 0.0;

        for (Receipt r : receiptsList) {
            double amt = r.getAmount();
            if (amt > 0 || r.isReceived()) {
                totalReceived += Math.abs(amt);
            } else {
                totalWithdrawn += Math.abs(amt);
            }

            if (r.isByHand()) {
                byHandBalance += amt;
            } else if (r.isOnline()) {
                onlineBalance += amt;
            }
        }

        double availableBalance = totalReceived - totalWithdrawn;

        lblTotalReceived.setText("Received: " + formatRupees(totalReceived));
        lblTotalWithdrawn.setText("Withdrawn: " + formatRupees(totalWithdrawn));
        lblByHandBalance.setText("Hand Bal: " + formatRupees(byHandBalance));
        lblOnlineBalance.setText("Online Bal: " + formatRupees(onlineBalance));
        lblAvailableBalance.setText("AVAILABLE: " + formatRupees(availableBalance));
    }

    private String formatRupees(double val) {
        if (val == (long) val) {
            return "₹" + String.format("%,d", (long) val);
        } else {
            return "₹" + String.format("%,.2f", val);
        }
    }

    private void setStatus(String message) {
        lblStatus.setText(message);
    }

    // ==========================================
    // MAIN ENTRY POINT
    // ==========================================

    public static void main(String[] args) {
        try {
            FlatLightLaf.setup();
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            ReceiptTracker tracker = new ReceiptTracker();
            tracker.setVisible(true);
        });
    }
}
