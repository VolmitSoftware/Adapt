package com.volmit.adapt;

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
import com.volmit.adapt.util.redis.VelocityConfig;
import net.byteflux.libby.Library;
import net.byteflux.libby.VelocityLibraryManager;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

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

    private final VelocityLibraryManager<AdaptVelocity> libby;
    private final Logger logger;
    private final ProxyServer proxy;
    private final File configFile;
    private RedisHandler handler;

    @Inject
    public AdaptVelocity(Logger logger,
                         ProxyServer proxy,
                         PluginManager pluginManager,
                         @DataDirectory Path dataFolder) {
        libby = new VelocityLibraryManager<>(logger, dataFolder, pluginManager, this);
        libby.addMavenCentral();

        this.logger = logger;
        this.proxy = proxy;
        this.configFile = dataFolder.resolve("config.yml").toFile();
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) throws IOException {
        netty("common");
        netty("handler");
        netty("buffer");
        netty("codec");
        netty("resolver");
        netty("transport");
        netty("transport-native-unix-common");
        dependency("io.lettuce:lettuce-core:6.5.1.RELEASE");
        dependency("io.projectreactor:reactor-core:3.6.6");
        dependency("org.reactivestreams:reactive-streams:1.0.4");

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
    public void onShutdown(ProxyShutdownEvent event) {
        if (this.handler != null) {
            proxy.getEventManager().unregisterListener(this, handler);
            handler.close();
            handler = null;
        }
    }

    private void load() throws IOException {
        if (!configFile.exists()) {
            logger.info("Config file does not exist. Creating one.");
            if (!configFile.getParentFile().exists() && !configFile.getParentFile().mkdirs())
                throw new IOException("Unable to create directory " + configFile.getParentFile());
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(new VelocityConfig(), writer);
            }
            return;
        }

        VelocityConfig config;
        try (FileReader reader = new FileReader(configFile)) {
            config = GSON.fromJson(reader, VelocityConfig.class);
        }

        this.handler = new RedisHandler(config.isDebug(), config.createClient());
        proxy.getEventManager().register(this, handler);
    }

    private void dependency(String dependency) {
        String[] part = dependency.split(":", 3);
        if (part.length != 3) throw new IllegalArgumentException("Invalid dependency: " + dependency);
        libby.loadLibrary(Library.builder()
                .id(part[1])
                .groupId(part[0])
                .artifactId(part[1])
                .version(part[2])
                .build());
    }

    private void netty(String artifactId) {
        libby.loadLibrary(Library.builder()
                .id("netty-" + artifactId)
                .groupId("io.netty")
                .artifactId("netty-" + artifactId)
                .version("4.1.115.Final")
                .build());
    }
}
