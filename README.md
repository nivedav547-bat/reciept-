# Receipt & Expense Collection Tracker

A fast, lightweight financial ledger built with **Java**, **Apache POI**, and an embedded **Web Dashboard** inspired by traditional **Kerala Theyyam art**. Designed for rapidly recording collections, expenses, and withdrawals directly synchronized to an Excel (`receipts.xlsx`) workbook with zero external databases.

---

## Key Features

1. **Dual Interface**:
   - **Modern Web Dashboard**: Accessible at `http://localhost:8080` and from any mobile phone on Wi-Fi (e.g. Android Chrome).
   - **Java Desktop UI**: Lightweight native desktop interface with FlatLaf styling.

2. **Kerala Theyyam Art Heritage Theme**:
   - Visual aesthetics inspired by traditional Kerala Theyyam face art and ornamental motifs.
   - Deep charcoal background (`#11100E`), deep red/maroon glows (`#511813`), and refined gold accents (`#D4AF62`).

3. **Collection & Expense Tracking**:
   - **Amount Received**: Stored as a positive numeric value in Excel.
   - **Amount Withdrawn**: Stored as a negative numeric value in Excel (`-200.00`).
   - Payment Modes: Strictly **By Hand** or **Online** with Left/Right arrow key shortcuts.

4. **5-Metric Live Financial Dashboard**:
   - **AVAILABLE BALANCE**: Prominent hero card showing net funds (`Total Received − Total Withdrawn`).
   - **TOTAL RECEIVED**: Sum of all positive collection amounts.
   - **TOTAL WITHDRAWN**: Absolute sum of all negative withdrawal amounts.
   - **BY HAND BALANCE**: Net funds collected/held by hand.
   - **ONLINE BALANCE**: Net funds received/paid digitally.

5. **Voice Entry**:
   - Powered by browser Web Speech API.
   - Extracts Name and Amount from speech while leaving Transaction Type and Payment Mode for manual verification.

6. **Excel Synchronization & Direct Download**:
   - Automatically synchronizes every transaction to `receipts.xlsx`.
   - Direct Excel workbook download button (`📥 Download Excel`) streams the complete `.xlsx` workbook.

---

## Quick Start

### Run Web Application (Recommended)
Double-click `run-webapp.bat` or run:
```powershell
.\run-webapp.ps1
```
Open **http://localhost:8080** in your browser.

### Run Desktop GUI
Double-click `run.bat` or run:
```powershell
.\run.ps1
```

---

## Excel File Specification

File: `receipts.xlsx`  
Sheet: `Receipts`

| Name | Amount | Transaction Type | Payment Mode |
| :--- | :---: | :---: | :---: |
| Rahul | 500.00 | Received | By Hand |
| Anu | 750.00 | Received | Online |
| Shop Expense | -200.00 | Withdrawn | By Hand |
| Transport | -100.00 | Withdrawn | Online |
