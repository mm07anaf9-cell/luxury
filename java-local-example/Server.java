import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.KeySpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** LOCAL LEARNING EXAMPLE ONLY. Binds to localhost. Not a production auth system. Run: java java-local-example/Server.java [port] */
public class Server {
    static final Path ROOT = Paths.get(System.getProperty("site.root", ".")).toAbsolutePath().normalize();
    static final Path USERS = Paths.get(System.getProperty("users.file", "data/users.tsv"));
    static final Map<String, String> sessions = new ConcurrentHashMap<>(); // token -> email
    static final Object LOCK = new Object();
    static final SecureRandom RNG = new SecureRandom();

    static final class Car {
        final int id, year, price, hp; final String make, model, body, note;
        Car(int id, String make, String model, int year, int price, String body, int hp, String note) {
            this.id = id; this.make = make; this.model = model; this.year = year; this.price = price; this.body = body; this.hp = hp; this.note = note;
        }
        int id() { return id; } int year() { return year; } int price() { return price; } int hp() { return hp; }
        String make() { return make; } String model() { return model; } String body() { return body; } String note() { return note; }
    }
    static final List<Car> CARS = List.of(
        new Car(1, "Porsche", "911 Carrera", 2023, 135000, "Coupe", 379, "Sample listing"),
        new Car(2, "Mercedes-Benz", "S 500", 2022, 118000, "Sedan", 429, "Sample listing"),
        new Car(3, "BMW", "X5 xDrive40i", 2023, 72000, "SUV", 335, "Sample listing"),
        new Car(4, "Audi", "RS e-tron GT", 2023, 147000, "Sedan", 637, "Sample listing"),
        new Car(5, "Range Rover", "Sport", 2022, 96000, "SUV", 395, "Sample listing"),
        new Car(6, "Bentley", "Continental GT", 2021, 205000, "Coupe", 542, "Sample listing"));

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        HttpServer s = start(port);
        System.out.println("Luxury Motors running on http://localhost:" + s.getAddress().getPort());
    }

    static HttpServer start(int port) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        s.createContext("/api/signup", Server::signup);
        s.createContext("/api/login", Server::login);
        s.createContext("/api/logout", Server::logout);
        s.createContext("/api/me", Server::me);
        s.createContext("/api/cars", Server::cars);
        s.createContext("/api/contact", Server::contact);
        s.createContext("/", Server::staticFile);
        s.start();
        return s;
    }

    // ---------- helpers ----------
    static void send(HttpExchange x, int code, String type, byte[] body) throws IOException {
        x.getResponseHeaders().set("Content-Type", type);
        x.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        x.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        if (body.length > 0) try (OutputStream o = x.getResponseBody()) { o.write(body); }
        x.close();
    }
    static void json(HttpExchange x, int code, String j) throws IOException {
        send(x, code, "application/json; charset=utf-8", j.getBytes(StandardCharsets.UTF_8));
    }
    static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"') b.append("\\\"");
            else if (c == '\\') b.append("\\\\");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c == '\t') b.append("\\t");
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.toString();
    }
    static String err(String m) { return "{\"error\":\"" + esc(m) + "\"}"; }
    static Map<String, String> form(HttpExchange x) throws IOException {
        byte[] raw = x.getRequestBody().readNBytes(20000);
        Map<String, String> m = new HashMap<>();
        for (String p : new String(raw, StandardCharsets.UTF_8).split("&")) {
            if (p.isEmpty()) continue;
            int i = p.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? p : p.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8);
            m.put(k, v);
        }
        return m;
    }
    static boolean post(HttpExchange x) throws IOException {
        if (!x.getRequestMethod().equals("POST")) { json(x, 405, err("POST required")); return false; }
        return true;
    }
    static String query(HttpExchange x, String key) {
        String q = x.getRequestURI().getRawQuery();
        if (q == null) return null;
        for (String p : q.split("&")) {
            int i = p.indexOf('=');
            if (i > 0 && URLDecoder.decode(p.substring(0, i), StandardCharsets.UTF_8).equals(key))
                return URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8);
        }
        return null;
    }
    static String tokenOf(HttpExchange x) {
        String a = x.getRequestHeaders().getFirst("Authorization");
        return a != null && a.startsWith("Bearer ") ? a.substring(7) : null;
    }

    // ---------- users ----------
    static byte[] hash(String pw, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, 120000, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) { throw new RuntimeException(e); }
    }
    static List<String[]> loadUsers() throws IOException {
        List<String[]> l = new ArrayList<>();
        if (Files.exists(USERS)) for (String line : Files.readAllLines(USERS)) if (!line.isBlank()) l.add(line.split("\t", -1));
        return l;
    }
    static String clean(String s) { return s == null ? "" : s.replaceAll("[\\t\\r\\n]", " ").trim(); }

    static void signup(HttpExchange x) throws IOException {
        if (!post(x)) return;
        Map<String, String> f = form(x);
        String name = clean(f.get("name")), email = clean(f.get("email")).toLowerCase(), pw = f.getOrDefault("password", "");
        if (name.length() < 2 || name.length() > 80) { json(x, 400, err("Enter your full name.")); return; }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || email.length() > 120) { json(x, 400, err("Enter a valid email.")); return; }
        if (pw.length() < 8) { json(x, 400, err("Password must be at least 8 characters.")); return; }
        if (!pw.equals(f.get("confirm"))) { json(x, 400, err("Passwords do not match.")); return; }
        synchronized (LOCK) {
            for (String[] u : loadUsers()) if (u[0].equals(email)) { json(x, 409, err("An account with this email already exists.")); return; }
            byte[] salt = new byte[16]; RNG.nextBytes(salt);
            Base64.Encoder b = Base64.getEncoder();
            if (USERS.getParent() != null) Files.createDirectories(USERS.getParent());
            Files.writeString(USERS, email + "\t" + name + "\t" + b.encodeToString(salt) + "\t" + b.encodeToString(hash(pw, salt)) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        json(x, 201, "{\"ok\":true}");
    }

    static void login(HttpExchange x) throws IOException {
        if (!post(x)) return;
        Map<String, String> f = form(x);
        String email = clean(f.get("email")).toLowerCase(), pw = f.getOrDefault("password", "");
        synchronized (LOCK) {
            for (String[] u : loadUsers()) {
                if (u.length >= 4 && u[0].equals(email)) {
                    byte[] salt = Base64.getDecoder().decode(u[2]);
                    if (MessageDigest.isEqual(hash(pw, salt), Base64.getDecoder().decode(u[3]))) {
                        byte[] t = new byte[24]; RNG.nextBytes(t);
                        String tok = Base64.getUrlEncoder().withoutPadding().encodeToString(t);
                        sessions.put(tok, email);
                        json(x, 200, "{\"token\":\"" + tok + "\",\"name\":\"" + esc(u[1]) + "\"}");
                        return;
                    }
                }
            }
        }
        json(x, 401, err("Wrong email or password."));
    }

    static void logout(HttpExchange x) throws IOException {
        String t = tokenOf(x); if (t != null) sessions.remove(t);
        json(x, 200, "{\"ok\":true}");
    }

    static void me(HttpExchange x) throws IOException {
        String t = tokenOf(x), email = t == null ? null : sessions.get(t);
        if (email == null) { json(x, 401, err("Not signed in.")); return; }
        synchronized (LOCK) {
            for (String[] u : loadUsers()) if (u[0].equals(email)) { json(x, 200, "{\"name\":\"" + esc(u[1]) + "\",\"email\":\"" + esc(email) + "\"}"); return; }
        }
        json(x, 401, err("Not signed in."));
    }

    static void cars(HttpExchange x) throws IOException {
        String make = query(x, "make"), body = query(x, "body"), max = query(x, "maxPrice");
        int maxP = Integer.MAX_VALUE;
        try { if (max != null && !max.isBlank()) maxP = Integer.parseInt(max); } catch (NumberFormatException e) { json(x, 400, err("maxPrice must be a number.")); return; }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Car c : CARS) {
            if (make != null && !make.isBlank() && !c.make().equalsIgnoreCase(make)) continue;
            if (body != null && !body.isBlank() && !c.body().equalsIgnoreCase(body)) continue;
            if (c.price() > maxP) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"id\":").append(c.id()).append(",\"make\":\"").append(esc(c.make())).append("\",\"model\":\"").append(esc(c.model()))
              .append("\",\"year\":").append(c.year()).append(",\"price\":").append(c.price()).append(",\"body\":\"").append(esc(c.body()))
              .append("\",\"hp\":").append(c.hp()).append(",\"note\":\"").append(esc(c.note())).append("\"}");
        }
        json(x, 200, sb.append(']').toString());
    }

    static void contact(HttpExchange x) throws IOException {
        if (!post(x)) return;
        Map<String, String> f = form(x);
        String name = clean(f.get("name")), email = clean(f.get("email")), msg = clean(f.get("message"));
        if (name.isEmpty() || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || msg.length() < 5) { json(x, 400, err("Fill in name, a valid email and a message.")); return; }
        synchronized (LOCK) {
            Path p = USERS.resolveSibling("messages.tsv");
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.writeString(p, System.currentTimeMillis() + "\t" + name + "\t" + email + "\t" + msg + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        json(x, 201, "{\"ok\":true}");
    }

    static void staticFile(HttpExchange x) throws IOException {
        String path = URLDecoder.decode(x.getRequestURI().getPath(), StandardCharsets.UTF_8);
        if (path.equals("/")) path = "/index.html";
        Path p = ROOT.resolve("." + path).normalize();
        if (!p.startsWith(ROOT) || p.startsWith(ROOT.resolve("java-local-example")) || p.startsWith(ROOT.resolve("data")) || !Files.isRegularFile(p)) {
            send(x, 404, "text/plain; charset=utf-8", "Not found".getBytes()); return;
        }
        String n = p.getFileName().toString().toLowerCase(), type = "application/octet-stream";
        if (n.endsWith(".html")) type = "text/html; charset=utf-8"; else if (n.endsWith(".css")) type = "text/css";
        else if (n.endsWith(".js")) type = "text/javascript"; else if (n.endsWith(".jpeg") || n.endsWith(".jpg")) type = "image/jpeg";
        else if (n.endsWith(".ttf")) type = "font/ttf";
        send(x, 200, type, Files.readAllBytes(p));
    }
}
