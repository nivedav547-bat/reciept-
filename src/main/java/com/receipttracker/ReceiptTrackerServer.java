package com.receipttracker;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Embedded HTTP Web Server for Receipt & Expense/Withdrawal Tracker.
 * Features a Kerala Theyyam-inspired dark theme blended with a modern financial dashboard.
 * Supports Received & Withdrawn transactions, 5 live financial balance metrics,
 * and direct Excel workbook download.
 */
public class ReceiptTrackerServer {

    public static final int DEFAULT_PORT = 8080;
    private final int port;
    private final ExcelManager excelManager;
    private HttpServer server;

    public ReceiptTrackerServer() {
        this(DEFAULT_PORT, ExcelManager.resolveDefaultPath());
    }

    public ReceiptTrackerServer(int port, Path excelPath) {
        this.port = port;
        this.excelManager = new ExcelManager(excelPath);
    }

    public void start() throws IOException {
        excelManager.initializeFile();

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        // Routes
        server.createContext("/", new StaticAppHandler());
        server.createContext("/assets/", new StaticAssetHandler(resolveAssetsPath()));
        server.createContext("/api/receipts", new ReceiptsApiHandler());
        server.createContext("/api/download", new DownloadHandler());
        server.createContext("/download", new DownloadHandler());
        server.createContext("/api/info", new InfoHandler());

        server.start();

        List<String> ips = getLocalIpAddresses();
        System.out.println("==================================================");
        System.out.println("  Receipt & Expense Tracker Web App is live!");
        System.out.println("  Theme:       Kerala Theyyam Art x Modern Financial UI");
        System.out.println("  PC URL:      http://localhost:" + port);
        for (String ip : ips) {
            System.out.println("  Android URL: http://" + ip + ":" + port);
        }
        System.out.println("  Excel file:  " + excelManager.getFilePath().toAbsolutePath());
        System.out.println("==================================================");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public static Path resolveAssetsPath() {
        Path[] candidates = new Path[] {
            Path.of("assets"),
            Path.of("c:/Users/NIVED/.gemini/antigravity/scratch/ReceiptTracker/assets"),
            Path.of("..", "assets"),
            Path.of(System.getProperty("user.dir"), "assets")
        };
        for (Path p : candidates) {
            if (Files.exists(p) && Files.isDirectory(p)) {
                return p.toAbsolutePath().normalize();
            }
        }
        return Path.of("assets").toAbsolutePath().normalize();
    }

    public static List<String> getLocalIpAddresses() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {}
        return ips;
    }

    // ==========================================================
    // HTTP HANDLERS
    // ==========================================================

    private class StaticAppHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (!"/".equals(path) && !"/index.html".equals(path)) {
                sendResponse(exchange, 404, "text/plain", "Not Found");
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            String html = getIndexHtml();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Serves local images, CSS, and static assets.
     */
    private class StaticAssetHandler implements HttpHandler {
        private final Path assetsRoot;

        public StaticAssetHandler(Path assetsRoot) {
            this.assetsRoot = assetsRoot.toAbsolutePath().normalize();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            String uriPath = exchange.getRequestURI().getPath();
            String subPath = uriPath.startsWith("/assets/") ? uriPath.substring("/assets/".length()) : uriPath;
            Path filePath = assetsRoot.resolve(subPath).normalize();

            if (!filePath.startsWith(assetsRoot) || !Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendResponse(exchange, 404, "text/plain", "Asset Not Found: " + subPath);
                return;
            }

            String mimeType = "application/octet-stream";
            String lower = filePath.getFileName().toString().toLowerCase();
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            } else if (lower.endsWith(".png")) {
                mimeType = "image/png";
            } else if (lower.endsWith(".svg")) {
                mimeType = "image/svg+xml";
            } else if (lower.endsWith(".css")) {
                mimeType = "text/css";
            } else if (lower.endsWith(".js")) {
                mimeType = "application/javascript";
            } else if (lower.endsWith(".webp")) {
                mimeType = "image/webp";
            }

            byte[] bytes = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    /**
     * Streams the complete Excel (.xlsx) workbook directly to the user's browser.
     */
    private class DownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            Path path = excelManager.getFilePath();
            if (!Files.exists(path)) {
                excelManager.initializeFile();
            }

            byte[] fileBytes = Files.readAllBytes(path);
            String fileName = path.getFileName().toString();

            exchange.getResponseHeaders().set("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileBytes.length));
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");

            exchange.sendResponseHeaders(200, fileBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileBytes);
            }
        }
    }

    private class InfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            List<String> ips = getLocalIpAddresses();
            StringBuilder ipsJson = new StringBuilder("[");
            for (int i = 0; i < ips.size(); i++) {
                if (i > 0) ipsJson.append(",");
                ipsJson.append(quoteJson(ips.get(i)));
            }
            ipsJson.append("]");

            String json = "{" +
                    "\"port\":" + port + "," +
                    "\"excelFile\":" + quoteJson(excelManager.getFilePath().getFileName().toString()) + "," +
                    "\"ips\":" + ipsJson.toString() +
                    "}";
            sendResponse(exchange, 200, "application/json", json);
        }
    }

    private class ReceiptsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            String method = exchange.getRequestMethod().toUpperCase();

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                switch (method) {
                    case "GET":
                        handleGetReceipts(exchange);
                        break;
                    case "POST":
                        handleCreateReceipt(exchange);
                        break;
                    case "DELETE":
                        handleDeleteReceipt(exchange);
                        break;
                    default:
                        sendResponse(exchange, 405, "application/json", "{\"error\":\"Method not allowed\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                String errorJson = "{\"error\":" + quoteJson(e.getMessage() != null ? e.getMessage() : "Internal Server Error") + "}";
                sendResponse(exchange, 500, "application/json", errorJson);
            }
        }

        private void handleGetReceipts(HttpExchange exchange) throws IOException {
            List<Receipt> list = excelManager.loadReceipts();
            String json = buildReceiptsJson(list);
            sendResponse(exchange, 200, "application/json", json);
        }

        private void handleCreateReceipt(HttpExchange exchange) throws IOException {
            String body = readRequestBody(exchange);
            String name = extractJsonField(body, "name");
            String amountStr = extractJsonField(body, "amount");
            String transactionType = extractJsonField(body, "transactionType");
            String paymentMode = extractJsonField(body, "paymentMode");

            if (name == null || name.trim().isEmpty()) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Name or expense description is required\"}");
                return;
            }

            double enteredAmount;
            try {
                enteredAmount = Double.parseDouble(amountStr.replace("₹", "").replace(",", "").trim());
                if (enteredAmount <= 0) {
                    sendResponse(exchange, 400, "application/json", "{\"error\":\"Amount must be greater than zero\"}");
                    return;
                }
            } catch (Exception e) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Invalid numeric amount entered\"}");
                return;
            }

            // Normalize transaction type
            boolean isWithdrawal = false;
            if (transactionType != null) {
                String t = transactionType.trim().toLowerCase();
                if (t.contains("withdrawn") || t.contains("withdraw") || t.contains("expense")) {
                    isWithdrawal = true;
                }
            }

            String finalType = isWithdrawal ? Receipt.TYPE_WITHDRAWN : Receipt.TYPE_RECEIVED;
            // Negative signed value for Withdrawn, positive for Received
            double signedAmount = isWithdrawal ? -Math.abs(enteredAmount) : Math.abs(enteredAmount);

            // Normalize payment mode (By Hand or Online)
            String finalMode = Receipt.MODE_BY_HAND;
            if (paymentMode != null && Receipt.MODE_ONLINE.equalsIgnoreCase(paymentMode.trim())) {
                finalMode = Receipt.MODE_ONLINE;
            }

            Receipt receipt = new Receipt(name.trim(), signedAmount, finalType, finalMode);
            excelManager.addReceipt(receipt);

            List<Receipt> updatedList = excelManager.loadReceipts();
            String json = buildReceiptsJson(updatedList);
            sendResponse(exchange, 201, "application/json", json);
        }

        private void handleDeleteReceipt(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            int index = -1;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2 && "index".equalsIgnoreCase(pair[0])) {
                        try {
                            index = Integer.parseInt(pair[1]);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            if (index < 0) {
                sendResponse(exchange, 400, "application/json", "{\"error\":\"Valid index parameter is required\"}");
                return;
            }

            excelManager.deleteReceipt(index);
            List<Receipt> updatedList = excelManager.loadReceipts();
            String json = buildReceiptsJson(updatedList);
            sendResponse(exchange, 200, "application/json", json);
        }
    }

    // ==========================================================
    // JSON & HTTP HELPERS
    // ==========================================================

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private static String extractJsonField(String json, String field) {
        if (json == null) return null;
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(?:\"([^\"]*)\"|([0-9.-]+)|(true|false|null))");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            if (matcher.group(1) != null) return matcher.group(1);
            if (matcher.group(2) != null) return matcher.group(2);
            if (matcher.group(3) != null) return matcher.group(3);
        }
        return null;
    }

    private static String quoteJson(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    /**
     * Calculates all 5 financial balances:
     * - TOTAL RECEIVED: Sum of all positive transaction amounts.
     * - TOTAL WITHDRAWN: Absolute sum of all negative transaction amounts.
     * - BY HAND BALANCE: Sum of all transactions where Payment Mode = By Hand.
     * - ONLINE BALANCE: Sum of all transactions where Payment Mode = Online.
     * - AVAILABLE BALANCE: Total Received - Total Withdrawn.
     */
    private String buildReceiptsJson(List<Receipt> list) {
        double totalReceived = 0.0;
        double totalWithdrawn = 0.0;
        double byHandBalance = 0.0;
        double onlineBalance = 0.0;

        StringBuilder items = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Receipt r = list.get(i);
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

            if (i > 0) items.append(",");
            items.append("{")
                    .append("\"index\":").append(i).append(",")
                    .append("\"name\":").append(quoteJson(r.getName())).append(",")
                    .append("\"amount\":").append(amt).append(",")
                    .append("\"displayAmount\":").append(Math.abs(amt)).append(",")
                    .append("\"transactionType\":").append(quoteJson(r.getTransactionType())).append(",")
                    .append("\"paymentMode\":").append(quoteJson(r.getPaymentMode()))
                    .append("}");
        }
        items.append("]");

        double availableBalance = totalReceived - totalWithdrawn;

        return "{" +
                "\"receipts\":" + items.toString() + "," +
                "\"totals\":{" +
                "\"totalReceived\":" + totalReceived + "," +
                "\"totalWithdrawn\":" + totalWithdrawn + "," +
                "\"byHandBalance\":" + byHandBalance + "," +
                "\"onlineBalance\":" + onlineBalance + "," +
                "\"availableBalance\":" + availableBalance +
                "}," +
                "\"excelFile\":" + quoteJson(excelManager.getFilePath().getFileName().toString()) +
                "}";
    }

    // ==========================================================
    // FRONTEND HTML / CSS / JAVASCRIPT
    // KERALA THEYYAM THEME x 5-METRIC FINANCIAL DASHBOARD
    // ==========================================================

    private String getIndexHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\" />\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover\" />\n" +
                "  <meta name=\"theme-color\" content=\"#11100E\" />\n" +
                "  <meta name=\"apple-mobile-web-app-capable\" content=\"yes\" />\n" +
                "  <meta name=\"apple-mobile-web-app-status-bar-style\" content=\"black-translucent\" />\n" +
                "  <title>Receipt &amp; Expense Tracker | Kerala Heritage Ledger</title>\n" +
                "  <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">\n" +
                "  <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>\n" +
                "  <link href=\"https://fonts.googleapis.com/css2?family=Cinzel:wght@600;700;800&family=Inter:wght@400;500;600;700&display=swap\" rel=\"stylesheet\">\n" +
                "  <style>\n" +
                "    :root {\n" +
                "      /* Palette */\n" +
                "      --bg-main: #11100E;\n" +
                "      --bg-secondary: #191512;\n" +
                "      --bg-card: #211B17;\n" +
                "      --bg-elevated: #29211B;\n" +
                "      --gold-primary: #D4AF62;\n" +
                "      --gold-bright: #F0CC7A;\n" +
                "      --gold-muted: #9C783B;\n" +
                "      --red-traditional: #8C241F;\n" +
                "      --red-bright: #ef4444;\n" +
                "      --red-dark: #511813;\n" +
                "      --text-primary: #F5EFE5;\n" +
                "      --text-secondary: #B8AA98;\n" +
                "      --text-muted: #85796C;\n" +
                "      --success-emerald: #3F8A65;\n" +
                "      --gold-border: rgba(212, 175, 98, 0.22);\n" +
                "      --gold-border-bright: rgba(240, 204, 122, 0.45);\n" +
                "      --neutral-border: rgba(255, 255, 255, 0.07);\n" +
                "    }\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }\n" +
                "    \n" +
                "    body {\n" +
                "      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;\n" +
                "      background-color: var(--bg-main);\n" +
                "      color: var(--text-primary);\n" +
                "      min-height: 100vh;\n" +
                "      position: relative;\n" +
                "      overflow-x: hidden;\n" +
                "      padding: 18px;\n" +
                "      padding-bottom: env(safe-area-inset-bottom, 32px);\n" +
                "      background-image: \n" +
                "        radial-gradient(circle at 88% 18%, rgba(140, 36, 31, 0.24) 0%, rgba(81, 24, 19, 0.08) 45%, transparent 70%),\n" +
                "        radial-gradient(circle at 10% 8%, rgba(212, 175, 98, 0.09) 0%, transparent 40%),\n" +
                "        radial-gradient(circle at 25% 90%, rgba(81, 24, 19, 0.16) 0%, transparent 50%),\n" +
                "        radial-gradient(ellipse at center, transparent 50%, rgba(10, 9, 8, 0.85) 100%);\n" +
                "      background-attachment: fixed;\n" +
                "    }\n" +
                "\n" +
                "    /* Subtle Theyyam Art Decorative Element on the Right */\n" +
                "    .theyyam-bg-art {\n" +
                "      position: fixed;\n" +
                "      top: 0;\n" +
                "      right: 0;\n" +
                "      width: 58vw;\n" +
                "      height: 100vh;\n" +
                "      max-width: 820px;\n" +
                "      background-image: url('/assets/images/theyyam-art.jpg'), url('/assets/images/theyyam-art.png');\n" +
                "      background-repeat: no-repeat;\n" +
                "      background-position: top 30px right -40px;\n" +
                "      background-size: cover;\n" +
                "      opacity: 0.19;\n" +
                "      mix-blend-mode: screen;\n" +
                "      pointer-events: none;\n" +
                "      z-index: 0;\n" +
                "      -webkit-mask-image: radial-gradient(ellipse 95% 85% at 80% 28%, black 15%, rgba(0,0,0,0.6) 45%, transparent 75%);\n" +
                "      mask-image: radial-gradient(ellipse 95% 85% at 80% 28%, black 15%, rgba(0,0,0,0.6) 45%, transparent 75%);\n" +
                "      filter: contrast(1.15) saturate(1.1);\n" +
                "      transition: opacity 0.5s ease;\n" +
                "    }\n" +
                "\n" +
                "    .container {\n" +
                "      position: relative;\n" +
                "      z-index: 1;\n" +
                "      max-width: 980px;\n" +
                "      margin: 0 auto;\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      gap: 18px;\n" +
                "    }\n" +
                "\n" +
                "    /* Header Section */\n" +
                "    header {\n" +
                "      display: flex;\n" +
                "      justify-content: space-between;\n" +
                "      align-items: center;\n" +
                "      flex-wrap: wrap;\n" +
                "      gap: 16px;\n" +
                "      padding: 14px 20px;\n" +
                "      background: rgba(33, 27, 23, 0.72);\n" +
                "      border: 1px solid var(--gold-border);\n" +
                "      backdrop-filter: blur(14px);\n" +
                "      -webkit-backdrop-filter: blur(14px);\n" +
                "      border-radius: 16px;\n" +
                "      box-shadow: 0 4px 24px rgba(0, 0, 0, 0.45);\n" +
                "    }\n" +
                "    .brand {\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 14px;\n" +
                "    }\n" +
                "    .ornament-logo {\n" +
                "      width: 44px;\n" +
                "      height: 44px;\n" +
                "      border-radius: 12px;\n" +
                "      background: linear-gradient(135deg, #511813 0%, #29211B 60%, #11100E 100%);\n" +
                "      border: 1.5px solid var(--gold-primary);\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      box-shadow: 0 2px 10px rgba(212, 175, 98, 0.25);\n" +
                "      flex-shrink: 0;\n" +
                "    }\n" +
                "    .ornament-logo svg {\n" +
                "      width: 24px;\n" +
                "      height: 24px;\n" +
                "      fill: var(--gold-bright);\n" +
                "      filter: drop-shadow(0 0 4px rgba(240, 204, 122, 0.5));\n" +
                "    }\n" +
                "    .header-titles h1 {\n" +
                "      font-family: 'Cinzel', serif;\n" +
                "      font-size: 21px;\n" +
                "      font-weight: 700;\n" +
                "      letter-spacing: 1.5px;\n" +
                "      color: var(--gold-bright);\n" +
                "      text-shadow: 0 1px 3px rgba(0, 0, 0, 0.8);\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 10px;\n" +
                "    }\n" +
                "    .header-titles .sub-text {\n" +
                "      font-size: 12.5px;\n" +
                "      color: var(--text-secondary);\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 8px;\n" +
                "      margin-top: 3px;\n" +
                "    }\n" +
                "    .heritage-tag {\n" +
                "      font-size: 10px;\n" +
                "      text-transform: uppercase;\n" +
                "      letter-spacing: 0.8px;\n" +
                "      color: var(--gold-muted);\n" +
                "      border: 1px solid rgba(212, 175, 98, 0.3);\n" +
                "      padding: 1px 6px;\n" +
                "      border-radius: 4px;\n" +
                "    }\n" +
                "    .header-right {\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 12px;\n" +
                "      flex-wrap: wrap;\n" +
                "    }\n" +
                "    .status-badge {\n" +
                "      display: inline-flex;\n" +
                "      align-items: center;\n" +
                "      gap: 7px;\n" +
                "      padding: 6px 12px;\n" +
                "      border-radius: 20px;\n" +
                "      font-size: 11px;\n" +
                "      font-weight: 600;\n" +
                "      letter-spacing: 0.8px;\n" +
                "      background: rgba(63, 138, 101, 0.12);\n" +
                "      border: 1px solid rgba(63, 138, 101, 0.35);\n" +
                "      color: #6ee7b7;\n" +
                "    }\n" +
                "    .status-dot {\n" +
                "      width: 7px;\n" +
                "      height: 7px;\n" +
                "      border-radius: 50%;\n" +
                "      background: #3F8A65;\n" +
                "      box-shadow: 0 0 8px #3F8A65;\n" +
                "      animation: pulseGreen 2s infinite ease-in-out;\n" +
                "    }\n" +
                "    @keyframes pulseGreen {\n" +
                "      0%, 100% { opacity: 1; transform: scale(1); }\n" +
                "      50% { opacity: 0.4; transform: scale(0.85); }\n" +
                "    }\n" +
                "    .btn-excel {\n" +
                "      background: linear-gradient(135deg, #1b382b 0%, #0d281a 100%);\n" +
                "      color: #a7f3d0;\n" +
                "      border: 1px solid rgba(63, 138, 101, 0.4);\n" +
                "      border-radius: 9px;\n" +
                "      padding: 7px 14px;\n" +
                "      font-size: 12.5px;\n" +
                "      font-weight: 600;\n" +
                "      cursor: pointer;\n" +
                "      display: inline-flex;\n" +
                "      align-items: center;\n" +
                "      gap: 6px;\n" +
                "      text-decoration: none;\n" +
                "      transition: all 0.2s;\n" +
                "      min-height: 38px;\n" +
                "    }\n" +
                "    .btn-excel:hover {\n" +
                "      background: linear-gradient(135deg, #244b3a 0%, #123724 100%);\n" +
                "      border-color: #3F8A65;\n" +
                "      box-shadow: 0 2px 10px rgba(63, 138, 101, 0.25);\n" +
                "      color: #fff;\n" +
                "    }\n" +
                "    .btn-csv-light {\n" +
                "      background: var(--bg-card);\n" +
                "      color: var(--text-secondary);\n" +
                "      border: 1px solid var(--gold-border);\n" +
                "      border-radius: 9px;\n" +
                "      padding: 7px 12px;\n" +
                "      font-size: 12.5px;\n" +
                "      font-weight: 600;\n" +
                "      cursor: pointer;\n" +
                "      min-height: 38px;\n" +
                "      transition: all 0.2s;\n" +
                "    }\n" +
                "    .btn-csv-light:hover {\n" +
                "      background: var(--bg-elevated);\n" +
                "      color: var(--gold-bright);\n" +
                "      border-color: var(--gold-primary);\n" +
                "    }\n" +
                "\n" +
                "    /* Android Wi-Fi Link Banner */\n" +
                "    .android-banner {\n" +
                "      background: rgba(33, 27, 23, 0.85);\n" +
                "      border: 1px solid var(--gold-border);\n" +
                "      border-radius: 12px;\n" +
                "      padding: 10px 16px;\n" +
                "      font-size: 13px;\n" +
                "      color: var(--gold-bright);\n" +
                "      display: flex;\n" +
                "      justify-content: space-between;\n" +
                "      align-items: center;\n" +
                "      gap: 10px;\n" +
                "      backdrop-filter: blur(8px);\n" +
                "    }\n" +
                "    .android-banner code {\n" +
                "      background: #11100E;\n" +
                "      color: var(--gold-bright);\n" +
                "      padding: 3px 8px;\n" +
                "      border-radius: 5px;\n" +
                "      border: 1px solid var(--gold-border);\n" +
                "      font-family: monospace;\n" +
                "      font-size: 13px;\n" +
                "    }\n" +
                "\n" +
                "    /* 5-Metric Financial Dashboard */\n" +
                "    .dashboard-section {\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      gap: 12px;\n" +
                "    }\n" +
                "    /* Hero Available Balance Card */\n" +
                "    .hero-balance-card {\n" +
                "      background: linear-gradient(135deg, #2D231B 0%, #221B15 50%, #191410 100%);\n" +
                "      border: 1.5px solid var(--gold-border-bright);\n" +
                "      border-radius: 16px;\n" +
                "      padding: 20px 24px;\n" +
                "      box-shadow: 0 8px 32px rgba(212, 175, 98, 0.18), inset 0 1px 0 rgba(240, 204, 122, 0.3);\n" +
                "      position: relative;\n" +
                "      overflow: hidden;\n" +
                "      display: flex;\n" +
                "      justify-content: space-between;\n" +
                "      align-items: center;\n" +
                "      flex-wrap: wrap;\n" +
                "      gap: 14px;\n" +
                "    }\n" +
                "    .hero-balance-card::before {\n" +
                "      content: '';\n" +
                "      position: absolute;\n" +
                "      top: 0; left: 0; width: 6px; height: 100%;\n" +
                "      background: linear-gradient(to bottom, #F0CC7A, #D4AF62, #9C783B);\n" +
                "    }\n" +
                "    .hero-info h3 {\n" +
                "      font-family: 'Cinzel', serif;\n" +
                "      font-size: 12px;\n" +
                "      font-weight: 700;\n" +
                "      letter-spacing: 1.2px;\n" +
                "      color: var(--gold-bright);\n" +
                "      text-transform: uppercase;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 8px;\n" +
                "      margin-bottom: 6px;\n" +
                "    }\n" +
                "    .hero-amount {\n" +
                "      font-size: 32px;\n" +
                "      font-weight: 800;\n" +
                "      color: var(--gold-bright);\n" +
                "      letter-spacing: -0.5px;\n" +
                "      text-shadow: 0 0 16px rgba(240, 204, 122, 0.35);\n" +
                "    }\n" +
                "    .hero-sub {\n" +
                "      font-size: 12.5px;\n" +
                "      color: var(--text-secondary);\n" +
                "      margin-top: 4px;\n" +
                "    }\n" +
                "    .hero-badge {\n" +
                "      background: rgba(212, 175, 98, 0.15);\n" +
                "      border: 1px solid var(--gold-border-bright);\n" +
                "      padding: 8px 16px;\n" +
                "      border-radius: 12px;\n" +
                "      font-size: 12px;\n" +
                "      font-weight: 600;\n" +
                "      color: var(--gold-bright);\n" +
                "      text-align: right;\n" +
                "    }\n" +
                "\n" +
                "    /* 4 Breakdown Cards Grid */\n" +
                "    .breakdown-grid {\n" +
                "      display: grid;\n" +
                "      grid-template-columns: repeat(4, 1fr);\n" +
                "      gap: 12px;\n" +
                "    }\n" +
                "    .metric-card {\n" +
                "      background: var(--bg-card);\n" +
                "      border: 1px solid var(--neutral-border);\n" +
                "      border-radius: 14px;\n" +
                "      padding: 14px 16px;\n" +
                "      box-shadow: 0 4px 18px rgba(0, 0, 0, 0.3);\n" +
                "      position: relative;\n" +
                "      overflow: hidden;\n" +
                "      transition: transform 0.2s, border-color 0.2s;\n" +
                "    }\n" +
                "    .metric-card:hover { transform: translateY(-2px); }\n" +
                "    \n" +
                "    /* 1. Total Received - Gold Accent */\n" +
                "    .metric-card.received {\n" +
                "      border-color: rgba(212, 175, 98, 0.3);\n" +
                "      background: linear-gradient(145deg, #241D18 0%, #1D1713 100%);\n" +
                "    }\n" +
                "    .metric-card.received::before {\n" +
                "      content: ''; position: absolute; top: 0; left: 0; width: 4px; height: 100%;\n" +
                "      background: var(--gold-primary);\n" +
                "    }\n" +
                "    .metric-card.received h4 { color: var(--gold-primary); }\n" +
                "    .metric-card.received .metric-value { color: var(--gold-bright); }\n" +
                "\n" +
                "    /* 2. Total Withdrawn - Red Accent */\n" +
                "    .metric-card.withdrawn {\n" +
                "      border-color: rgba(140, 36, 31, 0.4);\n" +
                "      background: linear-gradient(145deg, #241514 0%, #1A100F 100%);\n" +
                "    }\n" +
                "    .metric-card.withdrawn::before {\n" +
                "      content: ''; position: absolute; top: 0; left: 0; width: 4px; height: 100%;\n" +
                "      background: var(--red-bright);\n" +
                "    }\n" +
                "    .metric-card.withdrawn h4 { color: #fca5a5; }\n" +
                "    .metric-card.withdrawn .metric-value { color: #f87171; }\n" +
                "\n" +
                "    /* 3. By Hand Balance - Gold Accent */\n" +
                "    .metric-card.byhand {\n" +
                "      border-color: rgba(212, 175, 98, 0.25);\n" +
                "      background: linear-gradient(145deg, #231C18 0%, #1C1613 100%);\n" +
                "    }\n" +
                "    .metric-card.byhand::before {\n" +
                "      content: ''; position: absolute; top: 0; left: 0; width: 4px; height: 100%;\n" +
                "      background: var(--gold-muted);\n" +
                "    }\n" +
                "    .metric-card.byhand h4 { color: var(--gold-bright); }\n" +
                "    .metric-card.byhand .metric-value { color: var(--text-primary); }\n" +
                "\n" +
                "    /* 4. Online Balance - Green Accent */\n" +
                "    .metric-card.online {\n" +
                "      border-color: rgba(63, 138, 101, 0.35);\n" +
                "      background: linear-gradient(145deg, #17241C 0%, #121D16 100%);\n" +
                "    }\n" +
                "    .metric-card.online::before {\n" +
                "      content: ''; position: absolute; top: 0; left: 0; width: 4px; height: 100%;\n" +
                "      background: var(--success-emerald);\n" +
                "    }\n" +
                "    .metric-card.online h4 { color: #6ee7b7; }\n" +
                "    .metric-card.online .metric-value { color: #a7f3d0; }\n" +
                "\n" +
                "    .metric-card h4 {\n" +
                "      font-size: 11px;\n" +
                "      font-weight: 700;\n" +
                "      text-transform: uppercase;\n" +
                "      letter-spacing: 0.8px;\n" +
                "      margin-bottom: 6px;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: space-between;\n" +
                "    }\n" +
                "    .metric-value {\n" +
                "      font-size: 21px;\n" +
                "      font-weight: 700;\n" +
                "      letter-spacing: -0.3px;\n" +
                "      word-break: break-word;\n" +
                "    }\n" +
                "\n" +
                "    /* Universal Card Style */\n" +
                "    .card {\n" +
                "      background: var(--bg-card);\n" +
                "      border: 1px solid var(--gold-border);\n" +
                "      border-radius: 16px;\n" +
                "      padding: 22px;\n" +
                "      box-shadow: 0 6px 24px rgba(0, 0, 0, 0.35);\n" +
                "      position: relative;\n" +
                "      backdrop-filter: blur(10px);\n" +
                "    }\n" +
                "    .card-header {\n" +
                "      margin-bottom: 16px;\n" +
                "    }\n" +
                "    .card-header h2 {\n" +
                "      font-family: 'Cinzel', serif;\n" +
                "      font-size: 16px;\n" +
                "      font-weight: 700;\n" +
                "      letter-spacing: 1.2px;\n" +
                "      color: var(--gold-bright);\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 8px;\n" +
                "    }\n" +
                "    .card-header .card-subtitle {\n" +
                "      font-size: 12.5px;\n" +
                "      color: var(--text-secondary);\n" +
                "      margin-top: 3px;\n" +
                "    }\n" +
                "\n" +
                "    /* Transaction Entry Form Grid */\n" +
                "    .form-grid {\n" +
                "      display: grid;\n" +
                "      grid-template-columns: 1.5fr 1.2fr 1.1fr 1.2fr;\n" +
                "      gap: 14px;\n" +
                "      align-items: end;\n" +
                "    }\n" +
                "    .form-group {\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      gap: 7px;\n" +
                "    }\n" +
                "    .form-group label {\n" +
                "      font-size: 12px;\n" +
                "      font-weight: 600;\n" +
                "      color: var(--text-secondary);\n" +
                "      text-transform: uppercase;\n" +
                "      letter-spacing: 0.8px;\n" +
                "      display: flex;\n" +
                "      justify-content: space-between;\n" +
                "    }\n" +
                "    .form-group label span.hint {\n" +
                "      color: var(--text-muted);\n" +
                "      font-size: 11px;\n" +
                "      text-transform: none;\n" +
                "      letter-spacing: 0;\n" +
                "    }\n" +
                "    .input-wrapper {\n" +
                "      position: relative;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "    }\n" +
                "    .currency-symbol {\n" +
                "      position: absolute;\n" +
                "      left: 14px;\n" +
                "      color: var(--gold-primary);\n" +
                "      font-weight: 700;\n" +
                "      font-size: 17px;\n" +
                "      pointer-events: none;\n" +
                "    }\n" +
                "    input[type=\"text\"], select.custom-select {\n" +
                "      width: 100%;\n" +
                "      height: 48px;\n" +
                "      padding: 10px 14px;\n" +
                "      font-size: 15px;\n" +
                "      color: var(--text-primary);\n" +
                "      background: var(--bg-secondary);\n" +
                "      border: 1.5px solid rgba(212, 175, 98, 0.22);\n" +
                "      border-radius: 10px;\n" +
                "      outline: none;\n" +
                "      transition: all 0.2s ease;\n" +
                "      font-family: inherit;\n" +
                "    }\n" +
                "    input[type=\"text\"].amount-input {\n" +
                "      padding-left: 32px;\n" +
                "      font-weight: 700;\n" +
                "      color: var(--gold-bright);\n" +
                "    }\n" +
                "    input[type=\"text\"]:focus, select.custom-select:focus {\n" +
                "      border-color: var(--gold-primary);\n" +
                "      box-shadow: 0 0 0 3px rgba(212, 175, 98, 0.18);\n" +
                "      background: #1e1915;\n" +
                "    }\n" +
                "    input[type=\"text\"]::placeholder {\n" +
                "      color: var(--text-muted);\n" +
                "      font-size: 14px;\n" +
                "    }\n" +
                "    select.custom-select {\n" +
                "      cursor: pointer;\n" +
                "      appearance: none;\n" +
                "      -webkit-appearance: none;\n" +
                "      background-image: url(\"data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23D4AF62'%3e%3cpath d='M7 10l5 5 5-5z'/%3e%3c/svg%3e\");\n" +
                "      background-repeat: no-repeat;\n" +
                "      background-position: right 12px center;\n" +
                "      background-size: 18px;\n" +
                "      padding-right: 36px;\n" +
                "      font-weight: 600;\n" +
                "    }\n" +
                "    select.custom-select option {\n" +
                "      background: #191512;\n" +
                "      color: #F5EFE5;\n" +
                "      padding: 10px;\n" +
                "    }\n" +
                "\n" +
                "    /* Payment Mode Selector */\n" +
                "    .mode-toggle {\n" +
                "      display: flex;\n" +
                "      border: 1.5px solid rgba(212, 175, 98, 0.22);\n" +
                "      border-radius: 10px;\n" +
                "      overflow: hidden;\n" +
                "      height: 48px;\n" +
                "      background: var(--bg-secondary);\n" +
                "      padding: 3px;\n" +
                "      gap: 4px;\n" +
                "    }\n" +
                "    .mode-btn {\n" +
                "      flex: 1;\n" +
                "      border: none;\n" +
                "      background: transparent;\n" +
                "      color: var(--text-secondary);\n" +
                "      font-size: 13.5px;\n" +
                "      font-weight: 600;\n" +
                "      cursor: pointer;\n" +
                "      border-radius: 7px;\n" +
                "      transition: all 0.2s ease;\n" +
                "      display: inline-flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      gap: 6px;\n" +
                "    }\n" +
                "    .mode-btn.active.hand {\n" +
                "      background: linear-gradient(135deg, #D4AF62 0%, #9C783B 100%);\n" +
                "      color: #11100E;\n" +
                "      font-weight: 700;\n" +
                "      box-shadow: 0 2px 8px rgba(212, 175, 98, 0.3);\n" +
                "    }\n" +
                "    .mode-btn.active.online {\n" +
                "      background: linear-gradient(135deg, #3F8A65 0%, #24563e 100%);\n" +
                "      color: #ffffff;\n" +
                "      font-weight: 700;\n" +
                "      box-shadow: 0 2px 8px rgba(63, 138, 101, 0.35);\n" +
                "    }\n" +
                "\n" +
                "    /* Action Buttons Row */\n" +
                "    .button-row {\n" +
                "      display: flex;\n" +
                "      gap: 12px;\n" +
                "      margin-top: 16px;\n" +
                "      align-items: center;\n" +
                "      flex-wrap: wrap;\n" +
                "    }\n" +
                "    .btn-add {\n" +
                "      flex: 1.6;\n" +
                "      height: 48px;\n" +
                "      min-width: 180px;\n" +
                "      background: linear-gradient(135deg, #F0CC7A 0%, #D4AF62 50%, #9C783B 100%);\n" +
                "      color: #11100E;\n" +
                "      border: none;\n" +
                "      border-radius: 10px;\n" +
                "      font-size: 14.5px;\n" +
                "      font-weight: 700;\n" +
                "      letter-spacing: 0.6px;\n" +
                "      cursor: pointer;\n" +
                "      display: inline-flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      gap: 8px;\n" +
                "      box-shadow: 0 4px 18px rgba(212, 175, 98, 0.28);\n" +
                "      transition: all 0.2s ease;\n" +
                "    }\n" +
                "    .btn-add:hover {\n" +
                "      filter: brightness(1.08);\n" +
                "      transform: translateY(-1px);\n" +
                "      box-shadow: 0 6px 22px rgba(212, 175, 98, 0.38);\n" +
                "    }\n" +
                "    .btn-add:active { transform: scale(0.98); }\n" +
                "    \n" +
                "    .btn-voice {\n" +
                "      flex: 1;\n" +
                "      height: 48px;\n" +
                "      min-width: 150px;\n" +
                "      background: var(--bg-secondary);\n" +
                "      border: 1.5px solid var(--gold-primary);\n" +
                "      color: var(--gold-bright);\n" +
                "      border-radius: 10px;\n" +
                "      font-size: 13.5px;\n" +
                "      font-weight: 700;\n" +
                "      letter-spacing: 0.5px;\n" +
                "      cursor: pointer;\n" +
                "      display: inline-flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: center;\n" +
                "      gap: 8px;\n" +
                "      transition: all 0.2s ease;\n" +
                "    }\n" +
                "    .btn-voice:hover {\n" +
                "      background: rgba(212, 175, 98, 0.12);\n" +
                "      border-color: var(--gold-bright);\n" +
                "    }\n" +
                "    .btn-voice.listening {\n" +
                "      background: rgba(140, 36, 31, 0.25);\n" +
                "      border-color: #ef4444;\n" +
                "      color: #fca5a5;\n" +
                "    }\n" +
                "    .pulse-dot {\n" +
                "      width: 9px;\n" +
                "      height: 9px;\n" +
                "      border-radius: 50%;\n" +
                "      background: #ef4444;\n" +
                "      box-shadow: 0 0 10px #ef4444;\n" +
                "      animation: pulseRed 1s infinite alternate;\n" +
                "    }\n" +
                "    @keyframes pulseRed {\n" +
                "      0% { transform: scale(0.85); opacity: 0.6; }\n" +
                "      100% { transform: scale(1.2); opacity: 1; }\n" +
                "    }\n" +
                "    .btn-clear {\n" +
                "      height: 48px;\n" +
                "      padding: 0 18px;\n" +
                "      background: var(--bg-secondary);\n" +
                "      border: 1px solid var(--neutral-border);\n" +
                "      color: var(--text-secondary);\n" +
                "      border-radius: 10px;\n" +
                "      font-size: 13.5px;\n" +
                "      font-weight: 600;\n" +
                "      cursor: pointer;\n" +
                "      transition: all 0.2s;\n" +
                "    }\n" +
                "    .btn-clear:hover {\n" +
                "      background: var(--bg-elevated);\n" +
                "      color: var(--text-primary);\n" +
                "      border-color: var(--gold-border);\n" +
                "    }\n" +
                "\n" +
                "    /* Transaction Table Section */\n" +
                "    .table-top-bar {\n" +
                "      display: flex;\n" +
                "      justify-content: space-between;\n" +
                "      align-items: center;\n" +
                "      flex-wrap: wrap;\n" +
                "      gap: 14px;\n" +
                "      margin-bottom: 16px;\n" +
                "    }\n" +
                "    .search-box {\n" +
                "      position: relative;\n" +
                "      min-width: 270px;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "    }\n" +
                "    .search-icon {\n" +
                "      position: absolute;\n" +
                "      left: 12px;\n" +
                "      color: var(--gold-muted);\n" +
                "      font-size: 14px;\n" +
                "      pointer-events: none;\n" +
                "    }\n" +
                "    .search-input {\n" +
                "      width: 100%;\n" +
                "      height: 42px;\n" +
                "      padding: 8px 12px 8px 36px;\n" +
                "      font-size: 13.5px;\n" +
                "      background: var(--bg-secondary);\n" +
                "      border: 1.5px solid var(--gold-border);\n" +
                "      border-radius: 9px;\n" +
                "      color: var(--text-primary);\n" +
                "      outline: none;\n" +
                "      transition: all 0.2s;\n" +
                "    }\n" +
                "    .search-input:focus {\n" +
                "      border-color: var(--gold-primary);\n" +
                "      box-shadow: 0 0 0 3px rgba(212, 175, 98, 0.15);\n" +
                "    }\n" +
                "\n" +
                "    /* Desktop Table */\n" +
                "    .table-responsive {\n" +
                "      overflow-x: auto;\n" +
                "      border-radius: 10px;\n" +
                "      border: 1px solid var(--gold-border);\n" +
                "    }\n" +
                "    table {\n" +
                "      width: 100%;\n" +
                "      border-collapse: collapse;\n" +
                "      font-size: 14px;\n" +
                "    }\n" +
                "    th {\n" +
                "      background: #191512;\n" +
                "      color: var(--gold-primary);\n" +
                "      font-family: 'Cinzel', serif;\n" +
                "      font-size: 12px;\n" +
                "      font-weight: 700;\n" +
                "      letter-spacing: 0.9px;\n" +
                "      text-align: left;\n" +
                "      padding: 13px 16px;\n" +
                "      border-bottom: 1.5px solid var(--gold-border);\n" +
                "      white-space: nowrap;\n" +
                "    }\n" +
                "    td {\n" +
                "      padding: 13px 16px;\n" +
                "      border-bottom: 1px solid rgba(255, 255, 255, 0.05);\n" +
                "      vertical-align: middle;\n" +
                "      color: var(--text-primary);\n" +
                "    }\n" +
                "    tbody tr {\n" +
                "      background-color: var(--bg-card);\n" +
                "      transition: background-color 0.15s ease;\n" +
                "    }\n" +
                "    tbody tr:nth-child(even) {\n" +
                "      background-color: #1e1814;\n" +
                "    }\n" +
                "    tbody tr:hover {\n" +
                "      background-color: #2c221c;\n" +
                "    }\n" +
                "    .td-num { text-align: right; font-weight: 700; }\n" +
                "    .td-num.positive { color: var(--gold-bright); }\n" +
                "    .td-num.negative { color: #f87171; }\n" +
                "    .td-center { text-align: center; }\n" +
                "    \n" +
                "    /* Badges */\n" +
                "    .badge {\n" +
                "      display: inline-flex;\n" +
                "      align-items: center;\n" +
                "      gap: 5px;\n" +
                "      padding: 4px 10px;\n" +
                "      border-radius: 6px;\n" +
                "      font-size: 11.5px;\n" +
                "      font-weight: 700;\n" +
                "      letter-spacing: 0.3px;\n" +
                "    }\n" +
                "    .badge-received {\n" +
                "      background: rgba(63, 138, 101, 0.18);\n" +
                "      color: #6ee7b7;\n" +
                "      border: 1px solid rgba(63, 138, 101, 0.35);\n" +
                "    }\n" +
                "    .badge-withdrawn {\n" +
                "      background: rgba(140, 36, 31, 0.22);\n" +
                "      color: #fca5a5;\n" +
                "      border: 1px solid rgba(140, 36, 31, 0.45);\n" +
                "    }\n" +
                "    .badge-hand {\n" +
                "      background: rgba(212, 175, 98, 0.14);\n" +
                "      color: var(--gold-bright);\n" +
                "      border: 1px solid rgba(212, 175, 98, 0.35);\n" +
                "    }\n" +
                "    .badge-online {\n" +
                "      background: rgba(63, 138, 101, 0.16);\n" +
                "      color: #a7f3d0;\n" +
                "      border: 1px solid rgba(63, 138, 101, 0.35);\n" +
                "    }\n" +
                "    \n" +
                "    /* Delete Button */\n" +
                "    .btn-delete {\n" +
                "      padding: 6px 12px;\n" +
                "      font-size: 12px;\n" +
                "      font-weight: 600;\n" +
                "      border-radius: 6px;\n" +
                "      background: rgba(140, 36, 31, 0.2);\n" +
                "      border: 1px solid rgba(140, 36, 31, 0.45);\n" +
                "      color: #fca5a5;\n" +
                "      cursor: pointer;\n" +
                "      display: inline-flex;\n" +
                "      align-items: center;\n" +
                "      gap: 4px;\n" +
                "      transition: all 0.2s;\n" +
                "    }\n" +
                "    .btn-delete:hover {\n" +
                "      background: rgba(140, 36, 31, 0.45);\n" +
                "      border-color: #ef4444;\n" +
                "      color: #ffffff;\n" +
                "    }\n" +
                "\n" +
                "    /* Mobile Cards View (Android Screen Interface) */\n" +
                "    .mobile-cards {\n" +
                "      display: none;\n" +
                "      flex-direction: column;\n" +
                "      gap: 12px;\n" +
                "    }\n" +
                "    .receipt-card-mobile {\n" +
                "      background: var(--bg-card);\n" +
                "      border: 1px solid var(--gold-border);\n" +
                "      border-radius: 12px;\n" +
                "      padding: 14px 16px;\n" +
                "      display: flex;\n" +
                "      justify-content: space-between;\n" +
                "      align-items: center;\n" +
                "      gap: 12px;\n" +
                "      box-shadow: 0 2px 10px rgba(0, 0, 0, 0.25);\n" +
                "    }\n" +
                "    .mobile-info {\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      gap: 4px;\n" +
                "    }\n" +
                "    .mobile-name {\n" +
                "      font-size: 15.5px;\n" +
                "      font-weight: 600;\n" +
                "      color: var(--text-primary);\n" +
                "    }\n" +
                "    .mobile-amount {\n" +
                "      font-size: 17px;\n" +
                "      font-weight: 700;\n" +
                "    }\n" +
                "    .mobile-amount.positive { color: var(--gold-bright); }\n" +
                "    .mobile-amount.negative { color: #f87171; }\n" +
                "    .mobile-badges {\n" +
                "      display: flex;\n" +
                "      gap: 6px;\n" +
                "      margin-top: 2px;\n" +
                "    }\n" +
                "    .mobile-actions {\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      align-items: flex-end;\n" +
                "      gap: 8px;\n" +
                "    }\n" +
                "\n" +
                "    .empty-placeholder {\n" +
                "      padding: 38px 20px;\n" +
                "      text-align: center;\n" +
                "      color: var(--text-secondary);\n" +
                "      font-size: 14px;\n" +
                "    }\n" +
                "\n" +
                "    /* Toast Notifications */\n" +
                "    .toast {\n" +
                "      position: fixed;\n" +
                "      bottom: 24px;\n" +
                "      left: 50%;\n" +
                "      transform: translateX(-50%) translateY(30px);\n" +
                "      padding: 12px 24px;\n" +
                "      background: #241D17;\n" +
                "      color: var(--gold-bright);\n" +
                "      border: 1px solid var(--gold-primary);\n" +
                "      border-radius: 30px;\n" +
                "      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.6);\n" +
                "      font-size: 13.5px;\n" +
                "      font-weight: 600;\n" +
                "      opacity: 0;\n" +
                "      transition: all 0.25s ease;\n" +
                "      pointer-events: none;\n" +
                "      z-index: 9999;\n" +
                "      white-space: nowrap;\n" +
                "    }\n" +
                "    .toast.show {\n" +
                "      opacity: 1;\n" +
                "      transform: translateX(-50%) translateY(0);\n" +
                "    }\n" +
                "\n" +
                "    /* Responsive Layout for Android / Mobile Devices */\n" +
                "    @media (max-width: 768px) {\n" +
                "      body { padding: 12px 10px; }\n" +
                "      .theyyam-bg-art { width: 90vw; opacity: 0.14; background-position: top 10px right -80px; }\n" +
                "      .breakdown-grid {\n" +
                "        grid-template-columns: repeat(2, 1fr);\n" +
                "        gap: 10px;\n" +
                "      }\n" +
                "      .hero-amount { font-size: 26px; }\n" +
                "      .form-grid {\n" +
                "        grid-template-columns: 1fr;\n" +
                "        gap: 12px;\n" +
                "      }\n" +
                "      .table-responsive { display: none; }\n" +
                "      .mobile-cards { display: flex; }\n" +
                "      .header-right { width: 100%; justify-content: space-between; }\n" +
                "      .btn-excel, .btn-csv-light { flex: 1; justify-content: center; }\n" +
                "      .search-box { width: 100%; }\n" +
                "    }\n" +
                "    @media (max-width: 480px) {\n" +
                "      .breakdown-grid { grid-template-columns: 1fr; }\n" +
                "    }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "  <!-- Subtle Kerala Theyyam Artwork Background -->\n" +
                "  <div class=\"theyyam-bg-art\" aria-hidden=\"true\"></div>\n" +
                "\n" +
                "  <div class=\"container\">\n" +
                "    <!-- Header -->\n" +
                "    <header>\n" +
                "      <div class=\"brand\">\n" +
                "        <div class=\"ornament-logo\" title=\"Kerala Heritage Motif\">\n" +
                "          <svg viewBox=\"0 0 24 24\">\n" +
                "            <!-- Kerala temple lamp / crescent flame motif -->\n" +
                "            <path d=\"M12 2C11.5 4 10 6 10 8.5C10 10.4 11.6 12 12.5 12C13.4 12 15 10.4 15 8.5C15 6 13.5 4 12 2ZM12 13C8.5 13 4 15.5 4 18V21C4 21.6 4.4 22 5 22H19C19.6 22 20 21.6 20 21V18C20 15.5 15.5 13 12 13ZM12 15C14.8 15 17.5 16.5 18 17.8V20H6V17.8C6.5 16.5 9.2 15 12 15Z\"/>\n" +
                "          </svg>\n" +
                "        </div>\n" +
                "        <div class=\"header-titles\">\n" +
                "          <h1>RECEIPT COLLECTION</h1>\n" +
                "          <div class=\"sub-text\">\n" +
                "            <span>Digital Collection &amp; Expense Ledger</span>\n" +
                "            <span class=\"heritage-tag\">Kerala Heritage Inspired</span>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "      <div class=\"header-right\">\n" +
                "        <div class=\"status-badge\">\n" +
                "          <span class=\"status-dot\"></span> SYSTEM READY\n" +
                "        </div>\n" +
                "        <a href=\"/api/download\" class=\"btn-excel\" title=\"Download complete Excel workbook\">\n" +
                "          <span>📥</span> <strong>Download Excel</strong>\n" +
                "        </a>\n" +
                "        <button class=\"btn-csv-light\" onclick=\"exportCsv()\" title=\"Export as CSV\">Export CSV</button>\n" +
                "      </div>\n" +
                "    </header>\n" +
                "\n" +
                "    <!-- Android Mobile Access Banner (Shows if local LAN IP is detected) -->\n" +
                "    <div class=\"android-banner\" id=\"androidBanner\" style=\"display: none;\">\n" +
                "      <div>📱 <strong>Android Phone Access:</strong> Open <code id=\"androidUrl\"></code> on mobile Chrome</div>\n" +
                "      <button class=\"btn-csv-light\" style=\"min-height:30px; padding:3px 10px; font-size:12px;\" onclick=\"copyAndroidUrl()\">Copy Link</button>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- 5-Metric Financial Dashboard -->\n" +
                "    <div class=\"dashboard-section\">\n" +
                "      <!-- 5. AVAILABLE BALANCE (Main Financial Indicator) -->\n" +
                "      <div class=\"hero-balance-card\">\n" +
                "        <div class=\"hero-info\">\n" +
                "          <h3>AVAILABLE BALANCE <span>👑</span></h3>\n" +
                "          <div class=\"hero-amount\" id=\"lblAvailableBalance\">₹0.00</div>\n" +
                "          <div class=\"hero-sub\">Total Net Funds (Received − Withdrawn)</div>\n" +
                "        </div>\n" +
                "        <div class=\"hero-badge\">\n" +
                "          <div>HAND + ONLINE</div>\n" +
                "          <div style=\"color:var(--text-secondary); font-size:11px;\">Synchronized with Excel</div>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- 4 Metric Breakdown Cards -->\n" +
                "      <div class=\"breakdown-grid\">\n" +
                "        <!-- 1. TOTAL RECEIVED (Gold Accent) -->\n" +
                "        <div class=\"metric-card received\">\n" +
                "          <h4>TOTAL RECEIVED <span>📈</span></h4>\n" +
                "          <div class=\"metric-value\" id=\"lblTotalReceived\">₹0.00</div>\n" +
                "        </div>\n" +
                "        <!-- 2. TOTAL WITHDRAWN (Red Accent) -->\n" +
                "        <div class=\"metric-card withdrawn\">\n" +
                "          <h4>TOTAL WITHDRAWN <span>📉</span></h4>\n" +
                "          <div class=\"metric-value\" id=\"lblTotalWithdrawn\">₹0.00</div>\n" +
                "        </div>\n" +
                "        <!-- 3. BY HAND BALANCE (Gold Accent) -->\n" +
                "        <div class=\"metric-card byhand\">\n" +
                "          <h4>BY HAND BALANCE <span>✋</span></h4>\n" +
                "          <div class=\"metric-value\" id=\"lblByHandBalance\">₹0.00</div>\n" +
                "        </div>\n" +
                "        <!-- 4. ONLINE BALANCE (Green Accent) -->\n" +
                "        <div class=\"metric-card online\">\n" +
                "          <h4>ONLINE BALANCE <span>⚡</span></h4>\n" +
                "          <div class=\"metric-value\" id=\"lblOnlineBalance\">₹0.00</div>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- Input Form (New Transaction) -->\n" +
                "    <div class=\"card\">\n" +
                "      <div class=\"card-header\">\n" +
                "        <h2>NEW TRANSACTION</h2>\n" +
                "        <div class=\"card-subtitle\">Record collection or withdrawal instantly.</div>\n" +
                "      </div>\n" +
                "      <form id=\"transactionForm\" onsubmit=\"event.preventDefault(); addTransaction();\">\n" +
                "        <div class=\"form-grid\">\n" +
                "          <!-- 1. Name -->\n" +
                "          <div class=\"form-group\">\n" +
                "            <label for=\"txtName\">Name</label>\n" +
                "            <input type=\"text\" id=\"txtName\" placeholder=\"Enter name or expense description\" autofocus autocomplete=\"off\" autocorrect=\"off\" spellcheck=\"false\" enterkeyhint=\"next\" required />\n" +
                "          </div>\n" +
                "          <!-- 2. Transaction Type Dropdown -->\n" +
                "          <div class=\"form-group\">\n" +
                "            <label for=\"selTransactionType\">Transaction Type</label>\n" +
                "            <select id=\"selTransactionType\" class=\"custom-select\">\n" +
                "              <option value=\"Received\" selected>Amount Received</option>\n" +
                "              <option value=\"Withdrawn\">Amount Withdrawn</option>\n" +
                "            </select>\n" +
                "          </div>\n" +
                "          <!-- 3. Amount -->\n" +
                "          <div class=\"form-group\">\n" +
                "            <label for=\"txtAmount\">Amount</label>\n" +
                "            <div class=\"input-wrapper\">\n" +
                "              <span class=\"currency-symbol\">₹</span>\n" +
                "              <input type=\"text\" id=\"txtAmount\" class=\"amount-input\" placeholder=\"Enter amount\" inputmode=\"decimal\" pattern=\"[0-9.]*\" autocomplete=\"off\" enterkeyhint=\"done\" required />\n" +
                "            </div>\n" +
                "          </div>\n" +
                "          <!-- 4. Payment Mode -->\n" +
                "          <div class=\"form-group\">\n" +
                "            <label>Payment Mode <span class=\"hint\">(← / → keys)</span></label>\n" +
                "            <div class=\"mode-toggle\">\n" +
                "              <button type=\"button\" id=\"btnModeHand\" class=\"mode-btn active hand\" onclick=\"setMode('By Hand')\" title=\"Press Left Arrow (←)\">← By Hand</button>\n" +
                "              <button type=\"button\" id=\"btnModeOnline\" class=\"mode-btn\" onclick=\"setMode('Online')\" title=\"Press Right Arrow (→)\">Online →</button>\n" +
                "            </div>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "\n" +
                "        <!-- Buttons -->\n" +
                "        <div class=\"button-row\">\n" +
                "          <button type=\"submit\" class=\"btn-add\" id=\"btnSubmit\">\n" +
                "            <span>ADD TRANSACTION</span>\n" +
                "          </button>\n" +
                "          <button type=\"button\" class=\"btn-voice\" id=\"btnVoice\" onclick=\"toggleVoice()\" title=\"Speak Name & Amount\">\n" +
                "            <span>🎤</span> VOICE ENTRY\n" +
                "          </button>\n" +
                "          <button type=\"button\" class=\"btn-clear\" onclick=\"clearInputs()\">\n" +
                "            CLEAR\n" +
                "          </button>\n" +
                "        </div>\n" +
                "      </form>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- Transaction Table -->\n" +
                "    <div class=\"card\">\n" +
                "      <div class=\"table-top-bar\">\n" +
                "        <div class=\"card-header\" style=\"margin-bottom:0;\">\n" +
                "          <h2>TRANSACTION HISTORY (<span id=\"receiptCount\">0</span>)</h2>\n" +
                "          <div class=\"card-subtitle\">All recorded collections and withdrawals</div>\n" +
                "        </div>\n" +
                "        <div class=\"search-box\">\n" +
                "          <span class=\"search-icon\">🔍</span>\n" +
                "          <input type=\"text\" id=\"txtSearch\" class=\"search-input\" placeholder=\"Search receipts by name...\" oninput=\"renderReceipts()\" />\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- Desktop Table View -->\n" +
                "      <div class=\"table-responsive\">\n" +
                "        <table>\n" +
                "          <thead>\n" +
                "            <tr>\n" +
                "              <th style=\"width: 45px;\">#</th>\n" +
                "              <th>Name</th>\n" +
                "              <th style=\"width: 140px; text-align: right;\">Amount</th>\n" +
                "              <th style=\"width: 140px; text-align: center;\">Transaction Type</th>\n" +
                "              <th style=\"width: 130px; text-align: center;\">Payment Mode</th>\n" +
                "              <th style=\"width: 85px; text-align: center;\">Action</th>\n" +
                "            </tr>\n" +
                "          </thead>\n" +
                "          <tbody id=\"tableBody\">\n" +
                "            <!-- Dynamic Rows -->\n" +
                "          </tbody>\n" +
                "        </table>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- Mobile Card View for Android -->\n" +
                "      <div class=\"mobile-cards\" id=\"mobileCards\">\n" +
                "        <!-- Dynamic Mobile Cards -->\n" +
                "      </div>\n" +
                "\n" +
                "      <div id=\"emptyState\" class=\"empty-placeholder\" style=\"display: none;\">\n" +
                "        No transactions recorded yet. Use the form above to add your first entry.\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "\n" +
                "  <div id=\"toast\" class=\"toast\"></div>\n" +
                "\n" +
                "  <script>\n" +
                "    let currentMode = 'By Hand';\n" +
                "    let receiptsData = [];\n" +
                "    let androidIpUrl = '';\n" +
                "    let recognition = null;\n" +
                "    let isListening = false;\n" +
                "\n" +
                "    function setMode(mode) {\n" +
                "      currentMode = mode;\n" +
                "      const btnHand = document.getElementById('btnModeHand');\n" +
                "      const btnOnline = document.getElementById('btnModeOnline');\n" +
                "      if (mode === 'By Hand') {\n" +
                "        btnHand.classList.add('active', 'hand');\n" +
                "        btnOnline.classList.remove('active', 'online');\n" +
                "      } else {\n" +
                "        btnOnline.classList.add('active', 'online');\n" +
                "        btnHand.classList.remove('active', 'hand');\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function formatRupees(num, signed = false) {\n" +
                "      const val = parseFloat(num) || 0;\n" +
                "      const formatted = Math.abs(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });\n" +
                "      if (signed) {\n" +
                "        if (val < 0) return '-₹ ' + formatted;\n" +
                "        return '+₹ ' + formatted;\n" +
                "      }\n" +
                "      return (val < 0 ? '-₹ ' : '₹ ') + formatted;\n" +
                "    }\n" +
                "\n" +
                "    function showToast(msg, isError = false) {\n" +
                "      const t = document.getElementById('toast');\n" +
                "      t.textContent = msg;\n" +
                "      t.style.borderColor = isError ? '#ef4444' : '#D4AF62';\n" +
                "      t.style.color = isError ? '#fca5a5' : '#F0CC7A';\n" +
                "      t.classList.add('show');\n" +
                "      setTimeout(() => t.classList.remove('show'), 2800);\n" +
                "    }\n" +
                "\n" +
                "    async function loadInfo() {\n" +
                "      try {\n" +
                "        const res = await fetch('/api/info');\n" +
                "        if (!res.ok) return;\n" +
                "        const info = await res.json();\n" +
                "        if (info.ips && info.ips.length > 0) {\n" +
                "          androidIpUrl = `http://${info.ips[0]}:${info.port}`;\n" +
                "          document.getElementById('androidUrl').textContent = androidIpUrl;\n" +
                "          document.getElementById('androidBanner').style.display = 'flex';\n" +
                "        }\n" +
                "      } catch (e) {}\n" +
                "    }\n" +
                "\n" +
                "    function copyAndroidUrl() {\n" +
                "      if (!androidIpUrl) return;\n" +
                "      navigator.clipboard.writeText(androidIpUrl);\n" +
                "      showToast('Link copied: ' + androidIpUrl);\n" +
                "    }\n" +
                "\n" +
                "    async function loadReceipts() {\n" +
                "      try {\n" +
                "        const res = await fetch('/api/receipts');\n" +
                "        if (!res.ok) throw new Error('Failed to load receipts');\n" +
                "        const data = await res.json();\n" +
                "        receiptsData = data.receipts || [];\n" +
                "        updateDashboard(data.totals);\n" +
                "        renderReceipts();\n" +
                "      } catch (err) {\n" +
                "        showToast('Error loading receipts: ' + err.message, true);\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function updateDashboard(totals) {\n" +
                "      if (!totals) return;\n" +
                "      document.getElementById('lblTotalReceived').textContent = formatRupees(totals.totalReceived);\n" +
                "      document.getElementById('lblTotalWithdrawn').textContent = formatRupees(totals.totalWithdrawn);\n" +
                "      document.getElementById('lblByHandBalance').textContent = formatRupees(totals.byHandBalance);\n" +
                "      document.getElementById('lblOnlineBalance').textContent = formatRupees(totals.onlineBalance);\n" +
                "      document.getElementById('lblAvailableBalance').textContent = formatRupees(totals.availableBalance);\n" +
                "    }\n" +
                "\n" +
                "    function renderReceipts() {\n" +
                "      const query = document.getElementById('txtSearch').value.toLowerCase().trim();\n" +
                "      const tbody = document.getElementById('tableBody');\n" +
                "      const mobileContainer = document.getElementById('mobileCards');\n" +
                "      tbody.innerHTML = '';\n" +
                "      mobileContainer.innerHTML = '';\n" +
                "\n" +
                "      const filtered = receiptsData.filter(r => r.name.toLowerCase().includes(query));\n" +
                "      document.getElementById('receiptCount').textContent = receiptsData.length;\n" +
                "\n" +
                "      const emptyState = document.getElementById('emptyState');\n" +
                "      if (filtered.length === 0) {\n" +
                "        emptyState.style.display = 'block';\n" +
                "        emptyState.textContent = query ? 'No matching transactions found.' : 'No transactions recorded yet. Use the form above to add your first entry.';\n" +
                "      } else {\n" +
                "        emptyState.style.display = 'none';\n" +
                "        filtered.forEach((r, idx) => {\n" +
                "          const isWithdrawal = (r.transactionType && r.transactionType.toLowerCase() === 'withdrawn') || r.amount < 0;\n" +
                "          const isOnline = r.paymentMode && r.paymentMode.toLowerCase() === 'online';\n" +
                "          \n" +
                "          const typeBadge = isWithdrawal\n" +
                "            ? `<span class=\"badge badge-withdrawn\">Withdrawn</span>`\n" +
                "            : `<span class=\"badge badge-received\">Received</span>`;\n" +
                "            \n" +
                "          const modeBadge = isOnline\n" +
                "            ? `<span class=\"badge badge-online\">Online</span>`\n" +
                "            : `<span class=\"badge badge-hand\">By Hand</span>`;\n" +
                "\n" +
                "          const amountFormatted = isWithdrawal ? `-₹ ${Math.abs(r.amount).toLocaleString('en-IN', {minimumFractionDigits: 2})}` : `+₹ ${Math.abs(r.amount).toLocaleString('en-IN', {minimumFractionDigits: 2})}`;\n" +
                "          const amountClass = isWithdrawal ? 'td-num negative' : 'td-num positive';\n" +
                "\n" +
                "          // Desktop Table Row\n" +
                "          const tr = document.createElement('tr');\n" +
                "          tr.innerHTML = `\n" +
                "            <td style=\"color: var(--text-muted); font-size: 12px;\">${idx + 1}</td>\n" +
                "            <td style=\"font-weight: 600;\">${escapeHtml(r.name)}</td>\n" +
                "            <td class=\"${amountClass}\">${amountFormatted}</td>\n" +
                "            <td class=\"td-center\">${typeBadge}</td>\n" +
                "            <td class=\"td-center\">${modeBadge}</td>\n" +
                "            <td class=\"td-center\">\n" +
                "              <button class=\"btn-delete\" onclick=\"deleteReceipt(${r.index})\" title=\"Delete record\">🗑️ Delete</button>\n" +
                "            </td>\n" +
                "          `;\n" +
                "          tbody.appendChild(tr);\n" +
                "\n" +
                "          // Mobile Card (Android optimized)\n" +
                "          const card = document.createElement('div');\n" +
                "          card.className = 'receipt-card-mobile';\n" +
                "          card.innerHTML = `\n" +
                "            <div class=\"mobile-info\">\n" +
                "              <div class=\"mobile-name\">${escapeHtml(r.name)}</div>\n" +
                "              <div class=\"mobile-amount ${isWithdrawal ? 'negative' : 'positive'}\">${amountFormatted}</div>\n" +
                "              <div class=\"mobile-badges\">${typeBadge} ${modeBadge}</div>\n" +
                "            </div>\n" +
                "            <div class=\"mobile-actions\">\n" +
                "              <button class=\"btn-delete\" onclick=\"deleteReceipt(${r.index})\">🗑️ Delete</button>\n" +
                "            </div>\n" +
                "          `;\n" +
                "          mobileContainer.appendChild(card);\n" +
                "        });\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function escapeHtml(str) {\n" +
                "      return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\"/g, '&quot;');\n" +
                "    }\n" +
                "\n" +
                "    async function addTransaction() {\n" +
                "      const txtName = document.getElementById('txtName');\n" +
                "      const txtAmount = document.getElementById('txtAmount');\n" +
                "      const selType = document.getElementById('selTransactionType');\n" +
                "      \n" +
                "      const name = txtName.value.trim();\n" +
                "      const amountRaw = txtAmount.value.replace(/₹|,|-/g, '').trim();\n" +
                "      const transactionType = selType.value;\n" +
                "\n" +
                "      if (!name) {\n" +
                "        showToast('Please enter a name or expense description', true);\n" +
                "        txtName.focus();\n" +
                "        return;\n" +
                "      }\n" +
                "      const amount = parseFloat(amountRaw);\n" +
                "      if (isNaN(amount) || amount <= 0) {\n" +
                "        showToast('Please enter an amount greater than zero', true);\n" +
                "        txtAmount.focus();\n" +
                "        return;\n" +
                "      }\n" +
                "\n" +
                "      try {\n" +
                "        const res = await fetch('/api/receipts', {\n" +
                "          method: 'POST',\n" +
                "          headers: { 'Content-Type': 'application/json' },\n" +
                "          body: JSON.stringify({ name, amount, transactionType, paymentMode: currentMode })\n" +
                "        });\n" +
                "        const data = await res.json();\n" +
                "        if (!res.ok) throw new Error(data.error || 'Failed to record transaction');\n" +
                "        \n" +
                "        receiptsData = data.receipts || [];\n" +
                "        updateDashboard(data.totals);\n" +
                "        renderReceipts();\n" +
                "\n" +
                "        txtName.value = '';\n" +
                "        txtAmount.value = '';\n" +
                "        selType.value = 'Received'; // Reset to default Amount Received\n" +
                "        txtName.focus();\n" +
                "        showToast('✓ Transaction saved to Excel!');\n" +
                "      } catch (err) {\n" +
                "        showToast(err.message, true);\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    async function deleteReceipt(index) {\n" +
                "      if (!confirm('Are you sure you want to delete this transaction record?')) return;\n" +
                "      try {\n" +
                "        const res = await fetch(`/api/receipts?index=${index}`, { method: 'DELETE' });\n" +
                "        const data = await res.json();\n" +
                "        if (!res.ok) throw new Error(data.error || 'Failed to delete record');\n" +
                "\n" +
                "        receiptsData = data.receipts || [];\n" +
                "        updateDashboard(data.totals);\n" +
                "        renderReceipts();\n" +
                "        showToast('Transaction record deleted.');\n" +
                "      } catch (err) {\n" +
                "        showToast(err.message, true);\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function exportCsv() {\n" +
                "      if (receiptsData.length === 0) {\n" +
                "        showToast('No transactions to export.', true);\n" +
                "        return;\n" +
                "      }\n" +
                "      let csv = 'Name,Amount,Transaction Type,Payment Mode\\n';\n" +
                "      receiptsData.forEach(r => {\n" +
                "        const escapedName = '\"' + r.name.replace(/\"/g, '\"\"') + '\"';\n" +
                "        csv += `${escapedName},${r.amount},${r.transactionType},${r.paymentMode}\\n`;\n" +
                "      });\n" +
                "      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });\n" +
                "      const url = URL.createObjectURL(blob);\n" +
                "      const a = document.createElement('a');\n" +
                "      a.href = url;\n" +
                "      a.download = 'receipts.csv';\n" +
                "      a.click();\n" +
                "      URL.revokeObjectURL(url);\n" +
                "      showToast('Exported receipts.csv');\n" +
                "    }\n" +
                "\n" +
                "    function clearInputs() {\n" +
                "      document.getElementById('txtName').value = '';\n" +
                "      document.getElementById('txtAmount').value = '';\n" +
                "      document.getElementById('selTransactionType').value = 'Received';\n" +
                "      document.getElementById('txtName').focus();\n" +
                "    }\n" +
                "\n" +
                "    // Keyboard navigation flow\n" +
                "    document.getElementById('txtName').addEventListener('keydown', (e) => {\n" +
                "      if (e.key === 'Enter') {\n" +
                "        e.preventDefault();\n" +
                "        document.getElementById('txtAmount').focus();\n" +
                "      }\n" +
                "    });\n" +
                "\n" +
                "    // Left & Right Arrow shortcuts for Payment Mode\n" +
                "    document.addEventListener('keydown', (e) => {\n" +
                "      if (e.key === 'Escape') {\n" +
                "        clearInputs();\n" +
                "        return;\n" +
                "      }\n" +
                "      if (e.key === 'ArrowLeft') {\n" +
                "        const txtName = document.getElementById('txtName');\n" +
                "        if (document.activeElement === txtName && txtName.value.length > 0 && txtName.selectionStart > 0) return;\n" +
                "        e.preventDefault();\n" +
                "        setMode('By Hand');\n" +
                "        showToast('Payment Mode: By Hand (←)');\n" +
                "      } else if (e.key === 'ArrowRight') {\n" +
                "        const txtName = document.getElementById('txtName');\n" +
                "        if (document.activeElement === txtName && txtName.value.length > 0 && txtName.selectionStart < txtName.value.length) return;\n" +
                "        e.preventDefault();\n" +
                "        setMode('Online');\n" +
                "        showToast('Payment Mode: Online (→)');\n" +
                "      }\n" +
                "    });\n" +
                "\n" +
                "    // ==========================================================\n" +
                "    // VOICE ENTRY (Web Speech API)\n" +
                "    // Extracts Name and Amount ONLY. Never touches Transaction Type or Payment Mode.\n" +
                "    // ==========================================================\n" +
                "    function initVoice() {\n" +
                "      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;\n" +
                "      if (!SpeechRecognition) {\n" +
                "        showToast('Voice entry not supported on this browser. Try Chrome.', true);\n" +
                "        return false;\n" +
                "      }\n" +
                "      recognition = new SpeechRecognition();\n" +
                "      recognition.lang = 'en-IN';\n" +
                "      recognition.continuous = false;\n" +
                "      recognition.interimResults = false;\n" +
                "\n" +
                "      recognition.onstart = () => {\n" +
                "        isListening = true;\n" +
                "        updateVoiceBtn(true);\n" +
                "        showToast('🎙️ Listening... Speak name & amount');\n" +
                "      };\n" +
                "\n" +
                "      recognition.onresult = (event) => {\n" +
                "        const transcript = event.results[0][0].transcript;\n" +
                "        parseAndFillVoice(transcript);\n" +
                "      };\n" +
                "\n" +
                "      recognition.onerror = (event) => {\n" +
                "        isListening = false;\n" +
                "        updateVoiceBtn(false);\n" +
                "        showToast('Voice error: ' + (event.error || 'speech not recognized'), true);\n" +
                "      };\n" +
                "\n" +
                "      recognition.onend = () => {\n" +
                "        isListening = false;\n" +
                "        updateVoiceBtn(false);\n" +
                "      };\n" +
                "      return true;\n" +
                "    }\n" +
                "\n" +
                "    function toggleVoice() {\n" +
                "      if (isListening) {\n" +
                "        if (recognition) recognition.stop();\n" +
                "        return;\n" +
                "      }\n" +
                "      if (!recognition && !initVoice()) return;\n" +
                "      try {\n" +
                "        recognition.start();\n" +
                "      } catch (err) {\n" +
                "        showToast('Voice initialization failed: ' + err.message, true);\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function updateVoiceBtn(listening) {\n" +
                "      const btn = document.getElementById('btnVoice');\n" +
                "      if (!btn) return;\n" +
                "      if (listening) {\n" +
                "        btn.innerHTML = '<span class=\"pulse-dot\"></span> ● LISTENING...';\n" +
                "        btn.classList.add('listening');\n" +
                "      } else {\n" +
                "        btn.innerHTML = '<span>🎤</span> VOICE ENTRY';\n" +
                "        btn.classList.remove('listening');\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function parseAndFillVoice(transcript) {\n" +
                "      showToast('Heard: \"' + transcript + '\"');\n" +
                "      let cleaned = transcript.toLowerCase().replace(/rupees|rupee|inr|rs\\.?/gi, '').trim();\n" +
                "\n" +
                "      const wordMap = {\n" +
                "        'zero': 0, 'one': 1, 'two': 2, 'three': 3, 'four': 4, 'five': 5,\n" +
                "        'six': 6, 'seven': 7, 'eight': 8, 'nine': 9, 'ten': 10,\n" +
                "        'eleven': 11, 'twelve': 12, 'thirteen': 13, 'fourteen': 14, 'fifteen': 15,\n" +
                "        'sixteen': 16, 'seventeen': 17, 'eighteen': 18, 'nineteen': 19, 'twenty': 20,\n" +
                "        'thirty': 30, 'forty': 40, 'fifty': 50, 'sixty': 60, 'seventy': 70,\n" +
                "        'eighty': 80, 'ninety': 90, 'hundred': 100, 'thousand': 1000\n" +
                "      };\n" +
                "\n" +
                "      const numMatch = cleaned.match(/(\\d+(?:\\.\\d+)?)/);\n" +
                "      let parsedAmount = null;\n" +
                "      let parsedName = '';\n" +
                "\n" +
                "      if (numMatch) {\n" +
                "        parsedAmount = numMatch[1];\n" +
                "        parsedName = cleaned.replace(numMatch[0], '').replace(/\\s+/g, ' ').trim();\n" +
                "      } else {\n" +
                "        const words = cleaned.split(/\\s+/);\n" +
                "        let amountVal = 0;\n" +
                "        let nameWords = [];\n" +
                "        let currentChunk = 0;\n" +
                "\n" +
                "        for (let w of words) {\n" +
                "          if (wordMap[w] !== undefined) {\n" +
                "            let val = wordMap[w];\n" +
                "            if (val === 100) {\n" +
                "              currentChunk = (currentChunk === 0 ? 1 : currentChunk) * 100;\n" +
                "            } else if (val === 1000) {\n" +
                "              currentChunk = (currentChunk === 0 ? 1 : currentChunk) * 1000;\n" +
                "              amountVal += currentChunk;\n" +
                "              currentChunk = 0;\n" +
                "            } else {\n" +
                "              currentChunk += val;\n" +
                "            }\n" +
                "          } else {\n" +
                "            if (currentChunk === 0 && amountVal === 0) {\n" +
                "              nameWords.push(w);\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "        amountVal += currentChunk;\n" +
                "        if (amountVal > 0) {\n" +
                "          parsedAmount = String(amountVal);\n" +
                "          parsedName = nameWords.join(' ');\n" +
                "        } else {\n" +
                "          parsedName = cleaned;\n" +
                "        }\n" +
                "      }\n" +
                "\n" +
                "      if (parsedName) {\n" +
                "        const formattedName = parsedName.split(' ')\n" +
                "          .map(w => w.charAt(0).toUpperCase() + w.slice(1))\n" +
                "          .join(' ');\n" +
                "        document.getElementById('txtName').value = formattedName;\n" +
                "      }\n" +
                "      if (parsedAmount) {\n" +
                "        document.getElementById('txtAmount').value = parsedAmount;\n" +
                "      }\n" +
                "      // Note: Transaction Type and Payment Mode are NEVER modified by voice input!\n" +
                "    }\n" +
                "\n" +
                "    // Initial Load\n" +
                "    loadReceipts();\n" +
                "    loadInfo();\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }

    // ==========================================================
    // MAIN ENTRY POINT
    // ==========================================================

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        try {
            ReceiptTrackerServer server = new ReceiptTrackerServer(port, ExcelManager.resolveDefaultPath());
            server.start();

            // Auto-open browser on launch if Desktop browsing is supported
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                try {
                    Desktop.getDesktop().browse(new URI("http://localhost:" + port));
                } catch (Exception ignored) {}
            }

            // Keep main thread alive
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Failed to start Receipt Tracker Web Server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
