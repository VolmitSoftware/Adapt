package art.arcane.adapt.localization;

import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.integration.IrisTreeFellerPort;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.localization.catalog.ConfigFieldMessages;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.MessageKey;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Installs the built-in content's messages without a running core, for tests that read the whole built-in catalog. */
public final class BuiltInText {
  private static volatile List<MessageKey> configText;

  private BuiltInText() {
  }

  /** The built-in catalog with the built-in addon's messages, composed without installing anything. */
  public static MessageCatalog catalog() {
    return TextBundle.builtIn(AdaptMessages.catalog(), bundle(List.of())).catalog();
  }

  /** Installs the built-in addon's bundles and returns the built-in catalog they complete. */
  public static MessageCatalog install() {
    AdaptLanguage.texts().install(bundle(List.of()));
    return AdaptLanguage.builtInCatalog();
  }

  /**
   * As {@link #install()}, with the config field text the built-in content declares when it registers, Iris tree
   * felling included. Boots the built-in content once per test JVM, so call it before a test installs its own core.
   */
  public static MessageCatalog installWithConfigText() {
    AdaptLanguage.texts().install(bundle(configText()));
    return AdaptLanguage.builtInCatalog();
  }

  public static void uninstall() {
    AdaptLanguage.texts().uninstall(TextBundle.BUILT_IN);
  }

  /** The config field text of every built-in skill, adaptation, upgrade and block, read from a started registry. */
  public static synchronized List<MessageKey> configText() {
    if (configText == null) {
      configText = bootConfigText();
    }
    return configText;
  }

  private static List<MessageKey> bootConfigText() {
    Path data;
    try {
      data = Files.createTempDirectory("adapt-config-text");
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    AdaptPorts.install(IrisTreeFellerPort.class, new IrisTreeFellerPort() {
      @Override
      public boolean isAvailable() {
        return true;
      }
    });
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(data).build(), new BuiltinAddon())) {
      return ConfigFieldMessages.declaredBy(TextBundle.BUILT_IN,
          adapt.core().getAdaptServer().getSkillRegistry().getAllSkills());
    } finally {
      AdaptPorts.uninstall(IrisTreeFellerPort.class);
      delete(data);
    }
  }

  private static void delete(Path root) {
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static TextBundle bundle(List<MessageKey> configText) {
    return TextBundle.messages(TextBundle.BUILT_IN, BuiltinAddon.messages(), configText);
  }
}
