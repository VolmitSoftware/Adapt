package art.arcane.adapt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import art.arcane.adapt.util.project.redis.VelocityConfig;
import com.moandjiezana.toml.Toml;
import io.github.slimjar.app.builder.VelocityApplicationBuilder;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Plugin(id = BuildConstants.ID,
        name = BuildConstants.NAME,
        version = BuildConstants.VERSION,
        authors = {"NextdoorPsycho", "Cyberpwn", "Vatuu"})
public class AdaptVelocity {
    private static final Gson GSON = new GsonBuilder()
            .setLenient()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private final Runnable libraries;
    private final Logger logger;
    private final ProxyServer proxy;
    private final File configTomlFile;
    private final File legacyConfigFile;
    private RedisHandler handler;

    @Inject
    public AdaptVelocity(Logger logger,
                         ProxyServer proxy,
                         PluginManager pluginManager,
                         @DataDirectory Path dataFolder) {
        libraries = () -> {
            logger.info("Loading libraries...");
            new VelocityApplicationBuilder(pluginManager, this)
                    .downloadDirectoryPath(dataFolder.resolve(".libs"))
                    .logger(logger)
                    .build();
            logger.info("Libraries loaded.");
        };
        this.logger = logger;
        this.proxy = proxy;
        this.configTomlFile = dataFolder.resolve("config.toml").toFile();
        this.legacyConfigFile = dataFolder.resolve("config.yml").toFile();
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) throws IOException {
        libraries.run();
        load();
    }

    @Subscribe
    public void onReload(ProxyReloadEvent event) throws Exception {
        if (handler != null) {
            proxy.getEventManager().unregisterListener(this, handler);
            handler.close();
            handler = null;
        }

        load();
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) throws Exception {
        if (this.handler != null) {
            proxy.getEventManager().unregisterListener(this, handler);
            handler.close();
            handler = null;
        }
    }

    private void load() throws IOException {
        if (!configTomlFile.getParentFile().exists() && !configTomlFile.getParentFile().mkdirs()) {
            throw new IOException("Unable to create directory " + configTomlFile.getParentFile());
        }

        VelocityConfig config;
        if (configTomlFile.exists()) {
            config = readToml(configTomlFile);
            String raw = Files.readString(configTomlFile.toPath());
            String canonical = toToml(config);
            if (!normalize(raw).equals(normalize(canonical))) {
                Files.writeString(configTomlFile.toPath(), canonical);
            }
        } else if (legacyConfigFile.exists()) {
            try (FileReader reader = new FileReader(legacyConfigFile)) {
                config = GSON.fromJson(reader, VelocityConfig.class);
            }
            if (config == null) {
                throw new IOException("Failed to parse legacy velocity config.yml");
            }
            Files.writeString(configTomlFile.toPath(), toToml(config));
            logger.info("Migrated legacy velocity config config.yml -> config.toml.");
        } else {
            config = new VelocityConfig();
            Files.writeString(configTomlFile.toPath(), toToml(config));
            logger.info("Created missing velocity config [config.toml] from defaults.");
        }

        this.handler = new RedisHandler(config.isDebug(), config.createClient());
        proxy.getEventManager().register(this, handler);
    }

    private VelocityConfig readToml(File file) throws IOException {
        try {
            String raw = Files.readString(file.toPath());
            Map<String, Object> map = new Toml().read(raw).toMap();
            return GSON.fromJson(GSON.toJson(map), VelocityConfig.class);
        } catch (Throwable e) {
            throw new IOException("Failed to parse config.toml", e);
        }
    }

    private String toToml(VelocityConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Adapt Velocity configuration\n");
        sb.append("# This file is canonicalized on load and supports live reload through /velocity reload.\n\n");
        sb.append("# Enables verbose velocity-side debug logging.\n");
        sb.append("# Effect: true prints extra diagnostics; false keeps logs quiet.\n");
        sb.append("debug = ").append(config.isDebug()).append('\n');
        sb.append('\n');
        sb.append("# Redis host address used by the velocity bridge.\n");
        sb.append("# Effect: change this to point at your Redis server.\n");
        sb.append("host = ").append(quote(config.getHost())).append('\n');
        sb.append('\n');
        sb.append("# Redis TCP port.\n");
        sb.append("# Effect: use the port exposed by your Redis service.\n");
        sb.append("port = ").append(config.getPort()).append('\n');
        sb.append('\n');
        sb.append("# Optional Redis username.\n");
        sb.append("# Effect: leave empty unless your Redis ACL requires it.\n");
        sb.append("username = ").append(quote(config.getUsername())).append('\n');
        sb.append('\n');
        sb.append("# Optional Redis password.\n");
        sb.append("# Effect: set this when your Redis server requires authentication.\n");
        sb.append("password = ").append(quote(config.getPassword())).append('\n');
        return normalize(sb.toString()) + '\n';
    }

    private String quote(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").stripTrailing();
    }
}
