import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.RenderResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public final class Playground {
  private static final ObjectMapper JSON = new ObjectMapper();

  public static void main(String[] args) throws Exception {
    byte[] page = Files.readAllBytes(Path.of("/app/index.html"));
    HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
    server.createContext("/", exchange -> {
      if (!exchange.getRequestURI().getPath().equals("/")) {
        respond(exchange, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
        return;
      }
      respond(exchange, 200, "text/html; charset=utf-8", page);
    });
    server.createContext("/render", exchange -> {
      if (!exchange.getRequestMethod().equals("POST")) {
        respond(exchange, 405, "application/json", JSON.writeValueAsBytes(Map.of("error", "Use POST")));
        return;
      }
      try {
        byte[] body = exchange.getRequestBody().readNBytes(1048577);
        if (body.length > 1048576) throw new IllegalArgumentException("Template request is too large");
        Map<String, Object> request = JSON.readValue(body, new TypeReference<>() {});
        Object template = request.get("template");
        Object context = request.getOrDefault("context", Map.of());
        if (!(template instanceof String) || !(context instanceof Map)) {
          throw new IllegalArgumentException("Provide a template string and a context object");
        }
        Map<String, Object> bindings = JSON.convertValue(context, new TypeReference<>() {});
        RenderResult result = new Jinjava().renderForResult((String) template, bindings);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("output", result.getOutput());
        response.put("errors", result.getErrors().stream().map(error -> error.getSeverity() + ": " + error.getMessage()).toList());
        respond(exchange, 200, "application/json", JSON.writeValueAsBytes(response));
      } catch (Exception error) {
        respond(exchange, 400, "application/json", JSON.writeValueAsBytes(Map.of("error", String.valueOf(error.getMessage()))));
      }
    });
    server.setExecutor(Executors.newFixedThreadPool(2));
    server.start();
    System.out.println("Jinjava IP playground is listening on port 8080.");
  }

  private static void respond(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
    exchange.getResponseHeaders().set("Content-Type", type);
    exchange.sendResponseHeaders(status, body.length);
    try (var output = exchange.getResponseBody()) { output.write(body); }
    finally { exchange.close(); }
  }
}
