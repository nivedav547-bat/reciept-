package com.receipttracker;

import java.util.Objects;

/**
 * Represents a single transaction record (Received or Withdrawn).
 * Contains: name, amount (signed: positive for received, negative for withdrawn),
 * transactionType ("Received" or "Withdrawn"), paymentMode ("By Hand" or "Online"),
 * and optional description for legacy compatibility.
 */
public class Receipt {
    public static final String TYPE_RECEIVED = "Received";
    public static final String TYPE_WITHDRAWN = "Withdrawn";

    public static final String MODE_BY_HAND = "By Hand";
    public static final String MODE_ONLINE = "Online";

    private String name;
    private double amount;
    private String transactionType;
    private String paymentMode;
    private String description;

    public Receipt(String name, double amount, String paymentMode) {
        this(name, amount, amount < 0 ? TYPE_WITHDRAWN : TYPE_RECEIVED, paymentMode, "");
    }

    public Receipt(String name, double amount, String transactionType, String paymentMode) {
        this(name, amount, transactionType, paymentMode, "");
    }

    public Receipt(String name, double amount, String transactionType, String paymentMode, String description) {
        this.name = name != null ? name.trim() : "";
        this.transactionType = normalizeType(transactionType, amount);
        this.amount = normalizeSignedAmount(amount, this.transactionType);
        this.paymentMode = normalizeMode(paymentMode);
        this.description = description != null ? description.trim() : "";
    }

    private static String normalizeType(String type, double amount) {
        if (type != null) {
            String lower = type.trim().toLowerCase();
            if (lower.contains("withdrawn") || lower.contains("withdraw") || lower.contains("expense")) {
                return TYPE_WITHDRAWN;
            }
            if (lower.contains("received") || lower.contains("receive") || lower.contains("income")) {
                return TYPE_RECEIVED;
            }
        }
        return amount < 0 ? TYPE_WITHDRAWN : TYPE_RECEIVED;
    }

    private static double normalizeSignedAmount(double amt, String type) {
        if (TYPE_WITHDRAWN.equalsIgnoreCase(type)) {
            return -Math.abs(amt);
        } else {
            return Math.abs(amt);
        }
    }

    private static String normalizeMode(String mode) {
        if (mode != null && MODE_ONLINE.equalsIgnoreCase(mode.trim())) {
            return MODE_ONLINE;
        }
        return MODE_BY_HAND;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name.trim() : "";
    }

    /**
     * Returns the signed amount (+ for Received, - for Withdrawn).
     */
    public double getAmount() {
        return amount;
    }

    /**
     * Sets the signed amount and updates transaction type if sign changes.
     */
    public void setAmount(double amount) {
        this.amount = normalizeSignedAmount(amount, this.transactionType);
    }

    /**
     * Returns the absolute display amount (always positive).
     */
    public double getDisplayAmount() {
        return Math.abs(amount);
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = normalizeType(transactionType, this.amount);
        this.amount = normalizeSignedAmount(this.amount, this.transactionType);
    }

    public String getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(String paymentMode) {
        this.paymentMode = normalizeMode(paymentMode);
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description != null ? description.trim() : "";
    }

    public boolean isReceived() {
        return TYPE_RECEIVED.equalsIgnoreCase(transactionType) || amount >= 0;
    }

    public boolean isWithdrawn() {
        return TYPE_WITHDRAWN.equalsIgnoreCase(transactionType) || amount < 0;
    }

    public boolean isByHand() {
        return MODE_BY_HAND.equalsIgnoreCase(paymentMode);
    }

    public boolean isOnline() {
        return MODE_ONLINE.equalsIgnoreCase(paymentMode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Receipt receipt = (Receipt) o;
        return Double.compare(receipt.amount, amount) == 0 &&
                Objects.equals(name, receipt.name) &&
                Objects.equals(transactionType, receipt.transactionType) &&
                Objects.equals(paymentMode, receipt.paymentMode) &&
                Objects.equals(description, receipt.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, amount, transactionType, paymentMode, description);
    }

    @Override
    public String toString() {
        return "Receipt{" +
                "name='" + name + '\'' +
                ", amount=" + amount +
                ", transactionType='" + transactionType + '\'' +
                ", paymentMode='" + paymentMode + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
