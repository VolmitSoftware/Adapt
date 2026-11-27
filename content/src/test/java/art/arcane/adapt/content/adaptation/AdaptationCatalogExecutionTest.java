package art.arcane.adapt.content.adaptation;

import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.config.ConfigFileSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Mockito.mock;

class AdaptationCatalogExecutionTest {
  @TempDir
  Path dataFolder;

  private TestAdapt adapt;

  @BeforeEach
  void start() {
    adapt = TestAdapt.start(TestPlatform.builder(dataFolder).build(), List.of());
  }

  @AfterEach
  void stop() {
    adapt.close();
  }

  private static final List<Path> SOURCE_ROOTS = List.of(Path.of("src/main/java"));
  private static final Path ADAPTATION_PACKAGE = Path.of("art/arcane/adapt/content/adaptation");
  private static final Path SKILL_SOURCES = Path.of("src/main/java/art/arcane/adapt/content/skill");
  private static final Pattern SKILL_ID = Pattern.compile("static final Id ID = Id\\.adapt\\(\"([a-z_]+)\"\\)");
  private static final Pattern CONCRETE_ADAPTATION = Pattern.compile(
      "(?m)^public\\s+(?:final\\s+)?class\\s+\\w+\\s+extends\\s+SimpleAdaptation<"
  );

  private static final Set<String> SERVER_REGISTRY_CONSTRUCTORS = Set.of(
      "architect.ArchitectChalkLine",
      "architect.ArchitectElevator",
      "architect.ArchitectWirelessRedstone",
      "axe.AxeCraftLogSwap",
      "blocking.BlockingChainArmorer",
      "blocking.BlockingHorseArmorer",
      "blocking.BlockingPhalanxCrafter",
      "blocking.BlockingSaddlecrafter",
      "brewing.BrewingAbsorption",
      "brewing.BrewingBlindness",
      "brewing.BrewingDarkness",
      "brewing.BrewingDecay",
      "brewing.BrewingFatigue",
      "brewing.BrewingHaste",
      "brewing.BrewingHealthBoost",
      "brewing.BrewingHunger",
      "brewing.BrewingNausea",
      "brewing.BrewingResistance",
      "brewing.BrewingSaturation",
      "chronos.ChronosTimeBomb",
      "chronos.ChronosTimeInABottle",
      "crafting.CraftingBackpacks",
      "crafting.CraftingLeather",
      "crafting.CraftingReconstruction",
      "crafting.CraftingSkulls",
      "discovery.DiscoveryArmor",
      "golemancy.GolemAnglerCore",
      "golemancy.GolemAnimusBinding",
      "golemancy.GolemBreakerCore",
      "golemancy.GolemGathererCore",
      "golemancy.GolemGuardianCore",
      "golemancy.GolemHarvesterCore",
      "golemancy.GolemLumberCore",
      "golemancy.GolemNodeWeaving",
      "golemancy.GolemRancherCore",
      "golemancy.GolemRunicAugments",
      "golemancy.GolemSorterCore",
      "herbalism.HerbalismCraftableCobweb",
      "herbalism.HerbalismCraftableMushroomBlocks",
      "herbalism.HerbalismMyconid",
      "herbalism.HerbalismTerralid",
      "hunter.HunterSnareLine",
      "ranged.RangedWebBomb",
      "rift.RiftAccess",
      "rift.RiftGate",
      "sword.SwordsMachete",
      "tragoul.TragoulSkeletalServant"
  );

  @ParameterizedTest(name = "{0}")
  @MethodSource("adaptationClasses")
  void concreteAdaptationLoadsRendersEveryLevelAndAppliesDisabledReload(String className) throws Exception {
    assumeFalse(SERVER_REGISTRY_CONSTRUCTORS.contains(className.replace("art.arcane.adapt.content.adaptation.", "")),
        "Constructor requires the live server recipe, block, or potion registry");
    SimpleAdaptation<?> adaptation = construct(className);
    assertThat(adaptation.getConfigurationClass()).isNotNull();
    assertThat(adaptation.getDescription()).as("description").doesNotContain("[missing:");
    assertThat(ComponentHost.of(adaptation).reloadConfigFromDisk(false)).as("configuration load").isTrue();
    assertThat(adaptation.getConfig()).isNotNull();
    assertThat(adaptation.getMaxLevel()).isPositive();
    assertThat(adaptation.getLevelPercent(Integer.MIN_VALUE)).isZero();
    assertThat(adaptation.getLevelPercent(0)).isZero();
    assertThat(adaptation.getLevelPercent(Integer.MAX_VALUE)).isEqualTo(1D);
    double previous = 0D;
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      double progress = adaptation.getLevelPercent(level);
      assertThat(progress).as("level %s", level).isFinite().isBetween(previous, 1D);
      assertThat(adaptation.getCostFor(level)).as("purchase cost at level %s", level).isPositive();
      StatLines stats = new StatLines();
      adaptation.addStats(level, stats);
      for (String line : stats.lines()) {
        assertThat(line).as("stat text at level %s", progress).doesNotContain("NaN", "Infinity", "[missing:");
      }
      previous = progress;
    }
    assertThat(previous).isEqualTo(1D);
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
    assertThat(ComponentHost.of(adaptation).reloadConfigSnapshot("enabled = false\nmaxLevel = 1\n", dataFolder.resolve("disabled.toml").toFile(), false))
        .as("disabled configuration reload").isTrue();
    assertThat(adaptation.isEnabled()).isFalse();
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
    assertThat(adaptation.getLevelPercent(2)).isEqualTo(1D);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("adaptationClasses")
  void everyConfigurationRoundTripsItsDefaultsThroughToml(String className) throws Exception {
    Class<?> adaptationType = Class.forName(className, false, getClass().getClassLoader());
    ParameterizedType superclass = (ParameterizedType) adaptationType.getGenericSuperclass();
    Class<?> configType = (Class<?>) superclass.getActualTypeArguments()[0];
    roundTripConfiguration(configType);
  }

  private <T> void roundTripConfiguration(Class<T> type) throws Exception {
    Constructor<T> constructor = type.getDeclaredConstructor();
    constructor.setAccessible(true);
    T defaults = constructor.newInstance();
    Path file = dataFolder.resolve("config.toml");
    T written = ConfigFileSupport.load(file.toFile(), null, type, defaults, true, "adaptation:test", "");
    T loaded = ConfigFileSupport.parseSnapshot(Files.readString(file), file.toFile(), type, "adaptation:test", null);
    assertThat(loaded).usingRecursiveComparison().isEqualTo(written);
    assertThat(loaded).isInstanceOf(AdaptationConfig.class);
    AdaptationConfig shared = (AdaptationConfig) loaded;
    assertThat(shared.maxLevel).isPositive();
    assertThat(shared.costFactor).isFinite().isNotNegative();
  }

  private static SimpleAdaptation<?> construct(String className) throws Exception {
    Constructor<?> constructor = Class.forName(className).getConstructors()[0];
    Class<?>[] types = constructor.getParameterTypes();
    Object[] arguments = new Object[types.length];
    for (int index = 0; index < types.length; index++) {
      arguments[index] = mock(types[index]);
    }
    return TestContent.adaptation(skillOf(className), () -> {
      try {
        return (SimpleAdaptation<?>) constructor.newInstance(arguments);
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException("Cannot build " + className, failure);
      }
    });
  }

  /** The id of the built-in skill whose source registers {@code className}. */
  private static Id skillOf(String className) throws IOException {
    String simpleName = className.substring(className.lastIndexOf('.') + 1);
    Pattern registers = Pattern.compile("\\b" + simpleName + "(?:\\.ID|::new)\\b");
    try (Stream<Path> files = Files.list(SKILL_SOURCES)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file);
        Matcher id = SKILL_ID.matcher(source);
        if (registers.matcher(source).find() && id.find()) {
          return Id.adapt(id.group(1));
        }
      }
    }
    throw new IllegalStateException("No built-in skill registers " + className);
  }

  static List<String> adaptationClasses() throws IOException {
    List<String> classes = new ArrayList<>();
    for (Path sourceRoot : SOURCE_ROOTS) {
      Path adaptationRoot = sourceRoot.resolve(ADAPTATION_PACKAGE);
      if (!Files.isDirectory(adaptationRoot)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(adaptationRoot)) {
        files.filter(path -> path.toString().endsWith(".java"))
            .filter(AdaptationCatalogExecutionTest::isAdaptation)
            .map(path -> sourceRoot.relativize(path).toString().replace(path.getFileSystem().getSeparator(), ".").replace(".java", ""))
            .forEach(classes::add);
      }
    }
    classes.sort(null);
    assertThat(classes).hasSize(333);
    return classes;
  }

  private static boolean isAdaptation(Path path) {
    try {
      return CONCRETE_ADAPTATION.matcher(Files.readString(path)).find();
    } catch (IOException error) {
      throw new IllegalStateException("Cannot read " + path, error);
    }
  }
}
