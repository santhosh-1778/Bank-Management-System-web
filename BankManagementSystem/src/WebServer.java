import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import model.*;
import service.*;

/**
 * Small dependency-free web layer around the existing BankService.
 * It keeps the OOM domain/service code intact and exposes a browser UI on port 8080.
 */
public class WebServer {
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("BANK_PORT", "8080"));
    private static final Path DATA = Paths.get("data", "bank-web.dat");
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static BankService bank;

    public static void main(String[] args) throws Exception {
        boolean demo = Arrays.asList(args).contains("--demo");
        if (Files.exists(DATA)) {
            bank = BankService.load(DATA);
        } else {
            bank = new BankService("BR001", "Main Branch");
            if (demo) DemoData.seed(bank); else bank.bootstrapAdmin("admin", "admin123");
            bank.save(DATA);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/api/login", WebServer::login);
        server.createContext("/api/logout", WebServer::logout);
        server.createContext("/api/me", WebServer::me);
        server.createContext("/api/accounts", WebServer::accounts);
        server.createContext("/api/transactions", WebServer::transactions);
        server.createContext("/api/deposit", WebServer::deposit);
        server.createContext("/api/withdraw", WebServer::withdraw);
        server.createContext("/api/transfer", WebServer::transfer);
        server.createContext("/api/loans", WebServer::loans);
        server.createContext("/api/loan", WebServer::loanAction);
        server.createContext("/", WebServer::staticFiles);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("Web Bank Management System running on http://localhost:" + PORT);
        System.out.println("Demo login: admin/admin123, alice/alice123, bob/bob123");
    }

    private static void login(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { send(ex, 405, jsonError("Method not allowed")); return; }
        Map<String,String> b = body(ex);
        try {
            Session s = bank.login(b.get("userId"), b.get("password"));
            String token = UUID.randomUUID().toString();
            SESSIONS.put(token, s);
            send(ex, 200, "{\"token\":" + q(token) + ",\"userId\":" + q(s.getUser().getUserId()) + ",\"role\":" + q(s.getUser().getRole().name()) + "}");
        } catch (RuntimeException e) { send(ex, 401, jsonError(e.getMessage())); }
    }

    private static void logout(HttpExchange ex) throws IOException {
        Session s = session(ex);
        if (s != null) bank.logout(s);
        String token = token(ex); if (token != null) SESSIONS.remove(token);
        send(ex, 200, "{\"ok\":true}");
    }

    private static void me(HttpExchange ex) throws IOException {
        Session s = session(ex); if (s == null) { send(ex,401,jsonError("Not logged in")); return; }
        try { s.validate(); } catch (RuntimeException e) { send(ex,401,jsonError(e.getMessage())); return; }
        User u = s.getUser();
        String customer = u.getCustomerId() == null ? "null" : q(u.getCustomerId());
        send(ex,200,"{\"userId\":"+q(u.getUserId())+",\"role\":"+q(u.getRole().name())+",\"customerId\":"+customer+"}");
    }

    private static void accounts(HttpExchange ex) throws IOException {
        Session s = require(ex); if (s == null) return;
        try {
            StringBuilder out = new StringBuilder("["); boolean first=true;
            for (Account a : bank.listAccounts(s)) {
                if(!first) out.append(','); first=false;
                out.append("{\"accountNo\":").append(q(a.getAccountNo()))
                   .append(",\"customerId\":").append(q(a.getCustomerId()))
                   .append(",\"type\":").append(q(a.getType()))
                   .append(",\"balance\":").append(a.getBalance())
                   .append(",\"status\":").append(q(a.getStatus().name())).append('}');
            }
            out.append(']'); send(ex,200,out.toString());
        } catch (RuntimeException e) { send(ex,400,jsonError(e.getMessage())); }
    }

    private static void transactions(HttpExchange ex) throws IOException {
        Session s = require(ex); if (s == null) return;
        String account = query(ex.getRequestURI()).get("account");
        if (account == null) { send(ex,400,jsonError("account is required")); return; }
        try {
            StringBuilder out=new StringBuilder("["); boolean first=true;
            for(Transaction t: bank.getMiniStatement(s,account)){
                if(!first) out.append(','); first=false;
                out.append("{\"id\":").append(t.getTxnId()).append(",\"type\":").append(q(t.getType().name()))
                   .append(",\"amount\":").append(t.getAmount()).append(",\"balance\":").append(t.getBalanceAfter())
                   .append(",\"date\":").append(q(t.getDate().toString())).append('}');
            }
            out.append(']'); send(ex,200,out.toString());
        } catch(RuntimeException e){send(ex,400,jsonError(e.getMessage()));}
    }

    private static void deposit(HttpExchange ex) throws IOException { money(ex,true); }
    private static void withdraw(HttpExchange ex) throws IOException { money(ex,false); }
    private static void money(HttpExchange ex, boolean deposit) throws IOException {
        Session s=require(ex); if(s==null)return; Map<String,String>b=body(ex);
        try { double amount=Double.parseDouble(b.get("amount")); if(deposit) bank.deposit(s,b.get("accountNo"),amount); else bank.withdraw(s,b.get("accountNo"),amount); bank.save(DATA); send(ex,200,"{\"ok\":true}"); }
        catch(Exception e){send(ex,400,jsonError(e.getMessage()));}
    }

    private static void transfer(HttpExchange ex) throws IOException {
        Session s=require(ex); if(s==null)return; Map<String,String>b=body(ex);
        try { bank.transfer(s,b.get("from"),b.get("to"),Double.parseDouble(b.get("amount"))); bank.save(DATA); send(ex,200,"{\"ok\":true}"); }
        catch(Exception e){send(ex,400,jsonError(e.getMessage()));}
    }

    private static void loans(HttpExchange ex) throws IOException {
        Session s=require(ex); if(s==null)return;
        try { StringBuilder out=new StringBuilder("["); boolean first=true; for(Loan l:bank.listLoans(s)){ if(!first)out.append(','); first=false; out.append(loanJson(l)); } out.append(']'); send(ex,200,out.toString()); }
        catch(Exception e){send(ex,400,jsonError(e.getMessage()));}
    }

    private static void loanAction(HttpExchange ex) throws IOException {
        Session s=require(ex); if(s==null)return; Map<String,String>b=body(ex); String action=b.get("action");
        try {
            if("apply".equals(action)) bank.applyForLoan(s,b.get("accountNo"),Double.parseDouble(b.get("amount")),Integer.parseInt(b.get("tenure")),b.get("purpose"));
            else if("approve".equals(action)) bank.approveLoan(s,b.get("loanId"));
            else if("reject".equals(action)) bank.rejectLoan(s,b.get("loanId"));
            else if("repay".equals(action)) bank.repayLoan(s,b.get("loanId"),Double.parseDouble(b.get("amount")));
            else throw new IllegalArgumentException("Unknown loan action");
            bank.save(DATA); send(ex,200,"{\"ok\":true}");
        } catch(Exception e){send(ex,400,jsonError(e.getMessage()));}
    }

    private static String loanJson(Loan l){ return "{\"loanId\":"+q(l.getLoanId())+",\"accountNo\":"+q(l.getAccountNo())+",\"principal\":"+l.getPrincipal()+",\"rate\":"+l.getRate()+",\"tenure\":"+l.getTenureMonths()+",\"purpose\":"+q(l.getPurpose())+",\"status\":"+q(l.getStatus().name())+",\"outstanding\":"+l.getOutstanding()+",\"emi\":"+l.calculateEMI()+"}"; }

    private static Session require(HttpExchange ex) throws IOException {
        Session s=session(ex); if(s==null){send(ex,401,jsonError("Please log in"));return null;}
        try{s.validate();return s;}catch(RuntimeException e){send(ex,401,jsonError(e.getMessage()));return null;}
    }
    private static Session session(HttpExchange ex){String t=token(ex);return t==null?null:SESSIONS.get(t);}
    private static String token(HttpExchange ex){String h=ex.getRequestHeaders().getFirst("Authorization");return h!=null&&h.startsWith("Bearer ")?h.substring(7):null;}

    private static void staticFiles(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {send(ex,405,"Method not allowed");return;}
        String p=ex.getRequestURI().getPath(); if("/".equals(p))p="/index.html";
        if(p.contains("..")){send(ex,400,"Bad path");return;}
        Path file=Paths.get("web",p.substring(1));
        if(!Files.exists(file)||Files.isDirectory(file)){send(ex,404,"Not found");return;}
        String type=Files.probeContentType(file); if(type==null) type=contentType(file);
        ex.getResponseHeaders().set("Content-Type",type+"; charset=UTF-8"); byte[] data=Files.readAllBytes(file); ex.sendResponseHeaders(200,data.length); try(OutputStream os=ex.getResponseBody()){os.write(data);}
    }

    private static String contentType(Path p){String n=p.getFileName().toString().toLowerCase(); if(n.endsWith(".css"))return"text/css"; if(n.endsWith(".js"))return"application/javascript"; if(n.endsWith(".html"))return"text/html"; return"application/octet-stream";}
    private static Map<String,String> body(HttpExchange ex)throws IOException{String raw=new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);Map<String,String>m=new HashMap<>();for(String k:List.of("userId","password","accountNo","amount","from","to","action","loanId","tenure","purpose")){String v=jsonString(raw,k);if(v!=null)m.put(k,v);}return m;}
    private static String jsonString(String json,String key){String pat="\\\""+java.util.regex.Pattern.quote(key)+"\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"";var m=java.util.regex.Pattern.compile(pat).matcher(json);if(m.find())return m.group(1).replace("\\\"","\"").replace("\\\\","\\");String np="\\\""+java.util.regex.Pattern.quote(key)+"\\\"\\s*:\\s*([^,}]+)";m=java.util.regex.Pattern.compile(np).matcher(json);return m.find()?m.group(1).trim():null;}
    private static Map<String,String> query(URI u){Map<String,String>m=new HashMap<>();String q=u.getRawQuery();if(q==null)return m;for(String part:q.split("&")){String[]kv=part.split("=",2);if(kv.length==2)m.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),URLDecoder.decode(kv[1],StandardCharsets.UTF_8));}return m;}
    private static String q(String s){if(s==null)return"null";return"\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}
    private static String jsonError(String s){return"{\"error\":"+q(s==null?"Unknown error":s)+"}";}
    private static void send(HttpExchange ex,int code,String body)throws IOException{ex.getResponseHeaders().set("Content-Type","application/json; charset=UTF-8");byte[]d=body.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(code,d.length);try(OutputStream os=ex.getResponseBody()){os.write(d);}}
}
