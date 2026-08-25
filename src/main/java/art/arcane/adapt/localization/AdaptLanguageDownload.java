package art.arcane.adapt.localization;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.service.HotloadSVC;
import art.arcane.volmlib.util.localization.LocaleOverlay;
import art.arcane.volmlib.util.localization.LocalizationValidationResult;
import art.arcane.volmlib.util.localization.LocalizationValidator;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public final class AdaptLanguageDownload {
  private static final int CONNECT_TIMEOUT_MILLIS = 3_000;
  private static final int READ_TIMEOUT_MILLIS = 5_000;
  private static final int MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024;
  private static final long SHUTDOWN_TIMEOUT_MILLIS = 2_000L;
  private static final String REVISION_RESOURCE = "adapt-language-source.properties";
  private static final Pattern REVISION_PATTERN = Pattern.compile("[0-9a-f]{40}");
  private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");
  private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
  private static final Set<String> REPORTED_UNSUPPORTED = ConcurrentHashMap.newKeySet();
  private static final Set<String> VERIFIED_CACHE = ConcurrentHashMap.newKeySet();
  private static final AtomicLong LIFECYCLE_GENERATION = new AtomicLong();
  private static final LanguageSource SOURCE = loadLanguageSource();
  private static ExecutorService executor;

  private AdaptLanguageDownload() {
  }

  public static synchronized void start() {
    if (executor != null && !executor.isShutdown()) {
      return;
    }
    LIFECYCLE_GENERATION.incrementAndGet();
    IN_FLIGHT.clear();
    REPORTED_UNSUPPORTED.clear();
    executor = Executors.newSingleThreadExecutor((Runnable task) -> {
      Thread thread = new Thread(task, "Adapt-Language-Download");
      thread.setDaemon(true);
      return thread;
    });
  }

  public static synchronized void shutdown() {
    LIFECYCLE_GENERATION.incrementAndGet();
    IN_FLIGHT.clear();
    VERIFIED_CACHE.clear();
    ExecutorService current = executor;
    executor = null;
    if (current == null) {
      return;
    }
    current.shutdownNow();
    try {
      if (!current.awaitTermination(SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
        Adapt.warn("Adapt language download worker did not stop within two seconds.");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      Adapt.error(interrupted);
    }
  }

  public static void requestConfiguredLocale() {
    String locale;
    try {
      locale = AdaptLanguage.normalizeLocale(AdaptConfig.get().getLanguage());
    } catch (Throwable failure) {
      Adapt.error("Cannot download the configured Adapt locale.", failure);
      return;
    }
    if (AdaptMessages.catalog().englishLocale().equalsIgnoreCase(locale)) {
      return;
    }
    if (!SOURCE.hashes().containsKey(locale)) {
      if (REPORTED_UNSUPPORTED.add(locale)) {
        Adapt.warn("Locale " + locale + " is not available for automatic download; code-owned English remains active.");
      }
      return;
    }

    File target = localeFile(locale);
    if ((target.isFile() && VERIFIED_CACHE.contains(locale)) || !IN_FLIGHT.add(locale)) {
      return;
    }

    Adapt owner = Adapt.instance;
    long generation = LIFECYCLE_GENERATION.get();
    ExecutorService current;
    synchronized (AdaptLanguageDownload.class) {
      current = executor;
    }
    if (current == null) {
      IN_FLIGHT.remove(locale);
      return;
    }
    try {
      current.execute(() -> downloadAndActivate(owner, generation, locale, target.toPath()));
    } catch (RejectedExecutionException rejected) {
      IN_FLIGHT.remove(locale);
    }
  }

  static File localeFile(String locale) {
    return new File(cacheFolder(), locale + ".toml");
  }

  static File cacheFolder() {
    return new File(AdaptLanguageReference.folder(), "downloaded/" + SOURCE.revision());
  }

  static String sourceRevision() {
    return SOURCE.revision();
  }

  static Set<String> availableLocales() {
    return SOURCE.hashes().keySet();
  }

  static URI sourceUri(String locale) {
    if (locale == null || !SOURCE.hashes().containsKey(locale)) {
      throw new IllegalArgumentException("Unsupported locale: " + locale);
    }
    return URI.create(
        "https://raw.githubusercontent.com/VolmitSoftware/Adapt/"
            + SOURCE.revision()
            + "/src/main/resources/"
            + locale
            + ".toml"
    );
  }

  static byte[] fetch(URI source) throws Exception {
    URLConnection connection = source.toURL().openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
    connection.setReadTimeout(READ_TIMEOUT_MILLIS);
    connection.setUseCaches(false);
    connection.setRequestProperty("Accept", "text/plain");
    connection.setRequestProperty("User-Agent", "Adapt-Language-Download");
    if (connection.getContentLengthLong() > MAX_DOWNLOAD_BYTES) {
      throw new IOException("Downloaded locale exceeds " + MAX_DOWNLOAD_BYTES + " bytes.");
    }

    HttpURLConnection http = connection instanceof HttpURLConnection value ? value : null;
    try {
      if (http != null && http.getResponseCode() != HttpURLConnection.HTTP_OK) {
        throw new IOException("Locale download returned HTTP " + http.getResponseCode() + ".");
      }
      try (InputStream input = connection.getInputStream()) {
        byte[] bytes = input.readNBytes(MAX_DOWNLOAD_BYTES + 1);
        if (bytes.length > MAX_DOWNLOAD_BYTES) {
          throw new IOException("Downloaded locale exceeds " + MAX_DOWNLOAD_BYTES + " bytes.");
        }
        return bytes;
      }
    } finally {
      if (http != null) {
        http.disconnect();
      }
    }
  }

  static void download(URI source, String locale, Path target) throws Exception {
    byte[] bytes = fetch(source);
    verifyHash(locale, bytes);
    String raw = decode(bytes);
    validate(locale, raw);
    LanguageFileWriter.writeAtomicRequired(target, raw);
  }

  static LocaleOverlay readVerifiedOverlay(String locale, boolean reportFailure) {
    File cached = localeFile(locale);
    if (!cached.isFile()) {
      VERIFIED_CACHE.remove(locale);
      return null;
    }
    try {
      if (cached.length() > MAX_DOWNLOAD_BYTES) {
        throw new IOException("Cached locale exceeds " + MAX_DOWNLOAD_BYTES + " bytes.");
      }
      byte[] bytes = Files.readAllBytes(cached.toPath());
      verifyHash(locale, bytes);
      LocaleOverlay overlay = validate(locale, decode(bytes));
      VERIFIED_CACHE.add(locale);
      return overlay;
    } catch (Throwable failure) {
      VERIFIED_CACHE.remove(locale);
      if (reportFailure) {
        Adapt.warn("Ignoring invalid downloaded locale " + locale + ": " + failure.getMessage());
      }
      return null;
    }
  }

  private static LocaleOverlay validate(String locale, String raw) {
    MessageCatalog catalog = AdaptMessages.catalog();
    LocaleOverlay overlay = AdaptLanguage.parseOverlay("download:" + locale, locale, raw);
    LocalizationValidationResult validation = LocalizationValidator.validate(catalog, List.of(overlay));
    validation.throwIfInvalid();
    if (!overlay.values().keySet().equals(catalog.byId().keySet())) {
      throw new IllegalArgumentException("Downloaded locale does not cover the complete Adapt catalog: " + locale);
    }
    return overlay;
  }

  private static void verifyHash(String locale, byte[] bytes) throws NoSuchAlgorithmException {
    String expected = SOURCE.hashes().get(locale);
    if (expected == null) {
      throw new IllegalArgumentException("Unsupported locale: " + locale);
    }
    String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII),
        actual.getBytes(StandardCharsets.US_ASCII)
    )) {
      throw new IllegalArgumentException("Locale checksum does not match the pinned source.");
    }
  }

  private static String decode(byte[] bytes) throws CharacterCodingException {
    return StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString();
  }

  private static void downloadAndActivate(Adapt owner, long generation, String locale, Path target) {
    try {
      download(sourceUri(locale), locale, target);
      Adapt.info("Downloaded Adapt locale " + locale + ".");
      if (!SchedulerUtils.runGlobal(owner, () -> activate(owner, generation, locale))) {
        Adapt.warn("Downloaded Adapt locale " + locale + "; it will activate on the next config reload or restart.");
      }
    } catch (Throwable failure) {
      if (generation == LIFECYCLE_GENERATION.get()) {
        Adapt.warn("Failed to download Adapt locale " + locale + "; code-owned English remains active: "
            + failure.getMessage());
      }
    } finally {
      IN_FLIGHT.remove(locale);
    }
  }

  private static void activate(Adapt owner, long generation, String locale) {
    if (generation != LIFECYCLE_GENERATION.get()
        || owner == null
        || owner != Adapt.instance
        || !owner.isEnabled()
        || !locale.equals(AdaptLanguage.normalizeLocale(AdaptConfig.get().getLanguage()))) {
      return;
    }
    if (AdaptLanguage.reloadPassive()) {
      HotloadSVC hotload = Adapt.service(HotloadSVC.class);
      if (hotload != null) {
        hotload.refreshLocalizationConsumers();
      }
    }
  }

  private static LanguageSource loadLanguageSource() {
    Properties properties = new Properties();
    try (InputStream input = AdaptLanguageDownload.class.getClassLoader().getResourceAsStream(REVISION_RESOURCE)) {
      if (input == null) {
        throw new IllegalStateException("Missing " + REVISION_RESOURCE + ".");
      }
      properties.load(input);
    } catch (IOException failure) {
      throw new IllegalStateException("Failed to read " + REVISION_RESOURCE + ".", failure);
    }

    String revision = properties.getProperty("revision", "").trim().toLowerCase(Locale.ROOT);
    if (!REVISION_PATTERN.matcher(revision).matches()) {
      throw new IllegalStateException("Invalid Adapt language source revision.");
    }
    Map<String, String> hashes = new LinkedHashMap<>();
    String[] locales = properties.getProperty("locales", "").split(",");
    for (String rawLocale : locales) {
      String locale = rawLocale.trim();
      String hash = properties.getProperty("sha256." + locale, "").trim().toLowerCase(Locale.ROOT);
      if (!AdaptLanguage.LOCALE_NAME.matcher(locale).matches() || !SHA256_PATTERN.matcher(hash).matches()) {
        throw new IllegalStateException("Invalid Adapt language source entry: " + locale);
      }
      hashes.put(locale, hash);
    }
    if (hashes.isEmpty()) {
      throw new IllegalStateException("Adapt language source manifest contains no locales.");
    }
    return new LanguageSource(revision, Collections.unmodifiableMap(hashes));
  }

  private record LanguageSource(String revision, Map<String, String> hashes) {
  }
}
