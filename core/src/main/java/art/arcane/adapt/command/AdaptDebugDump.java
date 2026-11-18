package art.arcane.adapt.command;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.fx.TpsPort;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.volmlib.util.diagnostics.BukkitDebugMessages;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.web.MclogsClient;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code /adapt debug dump} without Bukkit: captures the SPI view of the server on the global thread, writes the report
 * to {@code <data>/debug/adapt-v<version>-debugdump-<UTC time>.txt} like the VolmLib Bukkit dump and optionally uploads
 * it to mclo.gs.
 */
public final class AdaptDebugDump implements DebugDumpPort {
  public static final String PERMISSION = "adapt.debugdump";
  private static final String PLUGIN = "Adapt";
  private static final String COMMAND = "/adapt debug dump";
  private static final String PARENT = "/adapt debug";
  private static final int MAXIMUM_FILE_ATTEMPTS = 10_000;
  private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd-HH-mm-ss").withZone(ZoneOffset.UTC);
  private static final String PRIMARY_LEFT = "#8b0000";
  private static final String PRIMARY_RIGHT = "#ff4d4d";
  private static final String DESCRIPTION = "#ffc7c7";
  private static final String REQUIRED = "#ff3333";
  private static final String OPTIONAL = "#ff9e9e";
  private static final String MUTED = "#d8a0a0";

  private final Uploader uploader;
  private final AtomicBoolean active = new AtomicBoolean();

  public AdaptDebugDump() {
    this(new MclogsClient()::publish);
  }

  AdaptDebugDump(Uploader uploader) {
    this.uploader = Objects.requireNonNull(uploader, "uploader");
  }

  @Override
  public void request(CommandSource source, boolean upload) {
    if (!source.hasPermission(PERMISSION)) {
      message(source, text(source, BukkitDebugMessages.MISSING_PERMISSION, MessageArgument.untrusted("permission", PERMISSION)),
          DESCRIPTION);
      return;
    }
    AdaptCore core = AdaptCore.current();
    if (core == null) {
      message(source, text(source, BukkitDebugMessages.PROVIDER_CLOSED), REQUIRED);
      return;
    }
    if (!active.compareAndSet(false, true)) {
      message(source, text(source, BukkitDebugMessages.ALREADY_PREPARING, MessageArgument.untrusted("plugin", PLUGIN)),
          DESCRIPTION);
      return;
    }
    message(source, text(source, BukkitDebugMessages.PREPARING, MessageArgument.untrusted("plugin", PLUGIN)), DESCRIPTION);
    AdaptPlatform platform = core.getPlatform();
    if (!platform.scheduler().runGlobal(() -> capture(source, upload, platform))) {
      finish(source, failure(source, BukkitDebugMessages.SCHEDULE_FAILED));
    }
  }

  private void capture(CommandSource source, boolean upload, AdaptPlatform platform) {
    AdaptDebugReport.Snapshot snapshot;
    String details;
    try {
      snapshot = snapshot(platform, source);
      details = details();
    } catch (RuntimeException exception) {
      AdaptLog.error("Unable to capture Adapt diagnostic state", exception);
      finish(source, failure(source, BukkitDebugMessages.CAPTURE_FAILED));
      return;
    }
    platform.scheduler().runAsync(() -> writeAndUpload(source, snapshot, details, upload));
  }

  private void writeAndUpload(CommandSource source, AdaptDebugReport.Snapshot snapshot, String details, boolean upload) {
    String report;
    Path file;
    try {
      report = AdaptDebugReport.create(snapshot, details);
      file = write(snapshot.dataDirectory(), snapshot.pluginVersion(), snapshot.generatedAt(), report);
    } catch (IOException | RuntimeException exception) {
      AdaptLog.error("Unable to write Adapt debug dump", exception);
      finish(source, failure(source, BukkitDebugMessages.WRITE_FAILED));
      return;
    }
    String path = file.toAbsolutePath().normalize().toString();
    AdaptLog.info("Saved debug dump: " + path);
    List<String> entries = new ArrayList<>(saved(source, path));
    if (upload) {
      try {
        URI url = uploader.publish(report, "VolmitSoftware - " + PLUGIN + " - v" + snapshot.pluginVersion(),
            "VolmitSoftware/" + PLUGIN + "/" + snapshot.pluginVersion());
        AdaptLog.info("Debug dump uploaded: " + url);
        entries.add(themed(source, text(source, BukkitDebugMessages.UPLOADED_AS,
            MessageArgument.untrusted("plugin", PLUGIN),
            MessageArgument.untrusted("version", snapshot.pluginVersion())), DESCRIPTION));
        entries.add(action(source, text(source, BukkitDebugMessages.OPEN_LABEL, MessageArgument.untrusted("url", url)),
            text(source, BukkitDebugMessages.OPEN_DESCRIPTION), url.toString(), "open_url"));
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        AdaptLog.warn("Adapt debug dump upload was interrupted");
        entries.add(themed(source, text(source, BukkitDebugMessages.UPLOAD_INTERRUPTED), REQUIRED));
      } catch (IOException | RuntimeException exception) {
        AdaptLog.warn("Unable to upload Adapt debug dump to mclo.gs: " + exception.getMessage());
        entries.add(themed(source, text(source, BukkitDebugMessages.UPLOAD_FAILED), REQUIRED));
      }
    }
    finish(source, entries);
  }

  private void finish(CommandSource source, List<String> entries) {
    active.set(false);
    menu(source, entries);
  }

  static AdaptDebugReport.Snapshot snapshot(AdaptPlatform platform, CommandSource source) {
    PlatformHost host = platform.host();
    Map<String, String> server = new LinkedHashMap<>();
    server.put("Implementation", host.platformName());
    server.put("Platform family", host.family().name());
    server.put("Scheduler", platform.scheduler().isRegionized() ? "Folia region" : "server thread");
    server.put("Minecraft", host.minecraftVersion());
    server.put("Players online", String.valueOf(host.onlinePlayers().size()));
    Map<String, Integer> environments = new TreeMap<>();
    int worlds = 0;
    for (WorldRef world : host.worlds()) {
      environments.merge(world.environment().name(), 1, Integer::sum);
      worlds++;
    }
    server.put("Loaded worlds", String.valueOf(worlds));
    for (Map.Entry<String, Integer> environment : environments.entrySet()) {
      server.put("Worlds " + environment.getKey().toLowerCase(Locale.ROOT), String.valueOf(environment.getValue()));
    }
    TpsPort tps = AdaptPorts.get(TpsPort.class, null);
    server.put("TPS", tps == null ? "unavailable" : String.format(Locale.ROOT, "%.2f", tps.recentTps()));
    return new AdaptDebugReport.Snapshot(Instant.now(), PLUGIN, host.adaptVersion(),
        source.player().isPresent() ? "player" : "console-or-other", server, host.dataDirectory(), codeSource());
  }

  private static String details() {
    try {
      AdaptCore core = AdaptCore.current();
      AdaptServer server = core == null ? null : core.getAdaptServer();
      return "Language: " + AdaptLanguage.activeLocale()
          + "\nLanguage source: " + AdaptLanguage.sourceState()
          + "\nSkills: " + (server == null ? 0 : server.getSkillRegistry().getAllSkills().size());
    } catch (RuntimeException exception) {
      AdaptLog.error("Unable to capture Adapt diagnostic details", exception);
      return "Plugin diagnostic capture failed: " + exception.getClass().getSimpleName();
    }
  }

  private static Path codeSource() {
    try {
      CodeSource source = AdaptDebugDump.class.getProtectionDomain().getCodeSource();
      return source == null ? null : Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
    } catch (URISyntaxException | RuntimeException exception) {
      return null;
    }
  }

  static Path write(Path dataDirectory, String version, Instant generatedAt, String report) throws IOException {
    Path root = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
    Path directory = root.resolve("debug").normalize();
    if (Files.isSymbolicLink(directory)) {
      throw new IOException("Debug output cannot be a symbolic link: " + directory);
    }
    Files.createDirectories(directory);
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("Debug output is not a regular directory: " + directory);
    }
    String baseName = PLUGIN.toLowerCase(Locale.ROOT) + "-v" + version.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_")
        + "-debugdump-" + FILE_TIME.format(generatedAt);
    byte[] content = Objects.requireNonNullElse(report, "").getBytes(StandardCharsets.UTF_8);
    for (int attempt = 1; attempt <= MAXIMUM_FILE_ATTEMPTS; attempt++) {
      Path target = directory.resolve(baseName + (attempt == 1 ? "" : "-" + attempt) + ".txt").normalize();
      if (!directory.equals(target.getParent())) {
        throw new IOException("Debug output escaped its directory: " + target);
      }
      try {
        Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        return target;
      } catch (FileAlreadyExistsException collision) {
        continue;
      }
    }
    throw new IOException("Unable to allocate a unique debug report in " + directory);
  }

  private static List<String> saved(CommandSource source, String path) {
    return List.of(
        themed(source, text(source, BukkitDebugMessages.SAVED,
            MessageArgument.untrusted("plugin", PLUGIN),
            MessageArgument.untrusted("path", path)), DESCRIPTION),
        action(source, text(source, BukkitDebugMessages.COPY_PATH), text(source, BukkitDebugMessages.COPY_PATH_DESCRIPTION),
            path, "copy_to_clipboard"));
  }

  private static List<String> failure(CommandSource source, TextKey key) {
    return List.of(themed(source, text(source, key), REQUIRED));
  }

  private static void message(CommandSource source, String text, String color) {
    menu(source, List.of(themed(source, text, color)));
  }

  private static void menu(CommandSource source, List<String> entries) {
    AdaptPorts.get(CommandHelpPort.class, CommandHelpPort.NONE)
        .menu(source, new CommandMenu(COMMAND, COMMAND, PARENT, entries, "", 1, Math.max(1, entries.size())));
  }

  private static String text(CommandSource source, TextKey key, MessageArgument... arguments) {
    MessageArgs.Builder values = MessageArgs.builder();
    for (MessageArgument argument : arguments) {
      values.add(argument);
    }
    UUID playerId = source.player().map(PlayerRef::uuid).orElse(null);
    return LanguageAudience.call(playerId, () -> AdaptLanguage.directorResolver().resolve(key, values.build()));
  }

  private static String themed(CommandSource source, String text, String color) {
    if (source.player().isEmpty()) {
      return text;
    }
    return "<" + MUTED + ">⇀</" + MUTED + "> <" + color + ">" + escapeText(text) + "</" + color + ">";
  }

  private static String action(CommandSource source, String title, String description, String value, String clickAction) {
    if (source.player().isEmpty()) {
      return title + ": " + value;
    }
    String hover = "<" + PRIMARY_RIGHT + ">" + escapeText(title) + "</" + PRIMARY_RIGHT + "><reset>\n"
        + "<" + DESCRIPTION + ">✎ " + escapeText(description) + "</" + DESCRIPTION + "><reset>\n"
        + "<" + OPTIONAL + ">✒ " + escapeText(value) + "</" + OPTIONAL + ">";
    return "<" + MUTED + ">⇀</" + MUTED + "> <click:" + clickAction + ":'" + escapeAttr(value) + "'><hover:show_text:'"
        + escapeAttr(hover) + "'><gradient:" + PRIMARY_LEFT + ":" + PRIMARY_RIGHT + ">" + escapeText(title)
        + "</gradient></hover></click>";
  }

  private static String escapeText(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("<", "\\<");
  }

  private static String escapeAttr(String value) {
    return value.replace("\\", "\\\\").replace("'", "\\'");
  }

  @FunctionalInterface
  interface Uploader {
    URI publish(String content, String source, String client) throws IOException, InterruptedException;
  }
}
