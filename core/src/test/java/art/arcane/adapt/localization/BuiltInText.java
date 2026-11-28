package art.arcane.adapt.localization;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.integration.IrisTreeFellerPort;
import art.arcane.adapt.engine.skill.LegacyContentNames;
import art.arcane.adapt.engine.skill.SkillHost;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Installs the built-in content's messages without a running core, for tests that read the whole built-in catalog. */
public final class BuiltInText {
  private static volatile Boot boot;

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
  public static List<MessageKey> configText() {
    return boot().configText();
  }

  /** The flat pre-namespace name of every built-in skill, read from a started registry: {@code agility}. */
  public static Set<String> legacySkillNames() {
    return boot().skillNames();
  }

  /**
   * The flat pre-namespace name of every adaptation, upgrade and block the built-in skills own, read from a started
   * registry: {@code agility-armor-up}.
   */
  public static Set<String> legacyAdaptationNames() {
    return boot().adaptationNames();
  }

  private static synchronized Boot boot() {
    if (boot == null) {
      boot = start();
    }
    return boot;
  }

  private static Boot start() {
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
      List<Skill<?>> skills = adapt.core().getAdaptServer().getSkillRegistry().getAllSkills();
      Set<String> skillNames = new TreeSet<>();
      Set<String> adaptationNames = new TreeSet<>();
      for (Skill<?> skill : skills) {
        skillNames.add(LegacyContentNames.of(skill.getId()));
        for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
          adaptationNames.add(LegacyContentNames.of(adaptation.getId()));
        }
      }
      return new Boot(ConfigFieldMessages.declaredBy(TextBundle.BUILT_IN, skills), Set.copyOf(skillNames),
          Set.copyOf(adaptationNames));
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

  private record Boot(List<MessageKey> configText, Set<String> skillNames, Set<String> adaptationNames) {
  }

  private static TextBundle bundle(List<MessageKey> configText) {
    return TextBundle.messages(TextBundle.BUILT_IN, BuiltinAddon.messages(), configText);
  }
}
