
package dev.mineshaft.internal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class MineshaftInternal extends JavaPlugin {
    private HttpServer httpServer;
    private ExecutorService executor;
    private String token;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        token = getConfig().getString("token", "TOKEN");
        String host = getConfig().getString("host", "127.0.0.1");
        int port = getConfig().getInt("port", 8765);

        if (token == null || token.isBlank()
                || "change-this-token".equals(token)) {
            getLogger().severe("Set a private token in config.yml before starting Mineshaft.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
            executor = Executors.newFixedThreadPool(4);
            httpServer.setExecutor(executor);

            httpServer.createContext("/v1/status", exchange -> {
                if (!authorized(exchange)) return;

                if (!method(exchange, "GET")) return;

                respond(exchange, 200, jsonObject(
                    "\"ok\":true",
                    "\"name\":\"MineshaftInternal\"",
                    "\"version\":\"0.3.0\""
                ));
            });

            httpServer.createContext("/v1/command", exchange -> {
                if (!authorized(exchange)) return;

                if (!method(exchange, "POST")) return;

                handleCommand(exchange);
            });

            httpServer.createContext("/v1/extras/players", exchange -> {
                if (!authorized(exchange)) return;
                if (!method(exchange, "GET")) return;

                List<String> players = new ArrayList<>();

                Bukkit.getOnlinePlayers().forEach(
                    player -> players.add(player.getName())
                );

                respond(exchange, 200, jsonObject(
                    "\"ok\":true",
                    "\"count\":" + players.size(),
                    "\"players\":" + jsonArray(players)
                ));
            });

            httpServer.createContext("/v1/extras/plugins", exchange -> {
                if (!authorized(exchange)) return;
                if (!method(exchange, "GET")) return;

                List<String> plugins = new ArrayList<>();

                for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                    plugins.add(jsonObject(
                        "\"name\":" + jsonString(plugin.getName()),
                        "\"version\":" + jsonString(plugin.getDescription().getVersion()),
                        "\"enabled\":" + plugin.isEnabled()
                    ));
                }

                respond(exchange, 200, jsonObject(
                    "\"ok\":true",
                    "\"count\":" + plugins.size(),
                    "\"plugins\":[" + String.join(",", plugins) + "]"
                ));
            });

            httpServer.createContext("/v1/extras/datapacks", exchange -> {
                if (!authorized(exchange)) return;
                if (!method(exchange, "GET")) return;

                List<String> datapacks = new ArrayList<>();

                for (World world : Bukkit.getWorlds()) {
                    Path directory = world.getWorldFolder()
                        .toPath()
                        .resolve("datapacks");

                    if (!Files.isDirectory(directory)) continue;

                    try (Stream<Path> paths = Files.list(directory)) {
                        paths.filter(path ->
                                Files.isDirectory(path)
                                || path.getFileName().toString().endsWith(".zip"))
                            .forEach(path -> datapacks.add(jsonObject(
                                "\"name\":" + jsonString(
                                    path.getFileName().toString()
                                ),
                                "\"world\":" + jsonString(world.getName()),
                                "\"enabled\":null"
                            )));
                    } catch (IOException exception) {
                        getLogger().warning(
                            "Could not read datapacks for "
                                + world.getName() + ": "
                                + exception.getMessage()
                        );
                    }
                }

                respond(exchange, 200, jsonObject(
                    "\"ok\":true",
                    "\"count\":" + datapacks.size(),
                    "\"datapacks\":[" + String.join(",", datapacks) + "]"
                ));
            });

            httpServer.createContext("/v1/extras/version", exchange -> {
                if (!authorized(exchange)) return;
                if (!method(exchange, "GET")) return;

                respond(exchange, 200, jsonObject(
                    "\"ok\":true",
                    "\"minecraft\":" + jsonString(Bukkit.getMinecraftVersion()),
                    "\"paper\":" + jsonString(Bukkit.getBukkitVersion()),
                    "\"server\":" + jsonString(Bukkit.getVersion()),
                    "\"plugin\":\"0.3.0\""
                ));
            });

            httpServer.createContext("/v1/extras/info", exchange -> {
                if (!authorized(exchange)) return;
                if (!method(exchange, "GET")) return;

                List<String> worlds = new ArrayList<>();
                Bukkit.getWorlds().forEach(
                    world -> worlds.add(world.getName())
                );

                respond(exchange, 200, jsonObject(
                    "\"ok\":true",
                    "\"minecraft\":" + jsonString(Bukkit.getMinecraftVersion()),
                    "\"server\":" + jsonString(Bukkit.getName()),
                    "\"onlinePlayers\":" + Bukkit.getOnlinePlayers().size(),
                    "\"maxPlayers\":" + Bukkit.getMaxPlayers(),
                    "\"worlds\":" + jsonArray(worlds),
                    "\"plugin\":\"MineshaftInternal\"",
                    "\"pluginVersion\":\"0.3.0\""
                ));
            });

            httpServer.start();
            getLogger().info(
                "Mineshaft Internal listening on " + host + ":" + port
            );
        } catch (IOException exception) {
            getLogger().severe(
                "Could not start Mineshaft HTTP bridge: "
                    + exception.getMessage()
            );
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private boolean authorized(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");

        if (token == null || token.isBlank()
                || "change-this-token".equals(token)
                || !("Bearer " + token).equals(auth)) {
            respond(exchange, 401, jsonObject(
                "\"error\":\"unauthorized\"",
                "\"hint\":\"Check the private token in config.yml\""
            ));
            return false;
        }

        return true;
    }

    private boolean method(HttpExchange exchange, String expected)
            throws IOException {
        if (!expected.equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, jsonObject(
                "\"error\":\"method_not_allowed\""
            ));
            return false;
        }

        return true;
    }

    private void handleCommand(HttpExchange exchange) throws IOException {
        String body = new String(
            exchange.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8
        );

        String command = stringField(body, "command");

        if (command == null || command.isBlank()) {
            respond(exchange, 400, jsonObject(
                "\"error\":\"missing_command\""
            ));
            return;
        }

        if (command.length() > 2048 || command.startsWith("/")) {
            respond(exchange, 400, jsonObject(
                "\"error\":\"invalid_command\""
            ));
            return;
        }

        FutureTask<Boolean> task = new FutureTask<>(() ->
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        );

        try {
            Bukkit.getScheduler().runTask(this, task);
            boolean executed = task.get();

            respond(exchange, 200, jsonObject(
                "\"ok\":true",
                "\"command\":" + jsonString(command),
                "\"accepted\":" + executed
            ));
        } catch (Exception exception) {
            getLogger().warning(
                "Command execution failed: " + exception.getMessage()
            );
            respond(exchange, 500, jsonObject(
                "\"error\":\"command_execution_failed\""
            ));
        }
    }

    private static String stringField(String json, String field) {
        Matcher matcher = Pattern.compile(
            "\"" + Pattern.quote(field)
                + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        ).matcher(json);

        if (!matcher.find()) return null;

        return matcher.group(1)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r");
    }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder("\"");

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
            }
        }

        return result.append('"').toString();
    }

    private static String jsonArray(List<String> values) {
        List<String> encoded = new ArrayList<>();

        for (String value : values) {
            encoded.add(jsonString(value));
        }

        return "[" + String.join(",", encoded) + "]";
    }

    private static String jsonObject(String... fields) {
        return "{" + String.join(",", fields) + "}";
    }

    private static void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
            "Content-Type",
            "application/json; charset=utf-8"
        );
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void onDisable() {
        if (httpServer != null) httpServer.stop(0);
        if (executor != null) executor.shutdownNow();
    }
}
