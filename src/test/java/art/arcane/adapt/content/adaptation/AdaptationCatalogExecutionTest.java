package art.arcane.adapt.content.adaptation;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AdaptationCatalogExecutionTest extends AdaptTestBase {
  private static final Path SOURCE_ROOT = Path.of("src/main/java");
  private static final Path ADAPTATION_ROOT = SOURCE_ROOT.resolve("art/arcane/adapt/content/adaptation");
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
      "excavation.ExcavationEarthMover",
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
    when(plugin.namespace()).thenReturn("adapt");
    SimpleAdaptation<?> adaptation = construct(className);
    assertThat(adaptation.getConfigurationClass()).isNotNull();
    assertThat(adaptation.reloadConfigFromDisk(false)).as("configuration load").isTrue();
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
      Element stats = mock(Element.class, RETURNS_SELF);
      when(stats.addLore(anyString())).thenAnswer(invocation -> {
        String line = invocation.getArgument(0);
        assertThat(line).as("stat text at level %s", progress).doesNotContain("NaN", "Infinity");
        return stats;
      });
      adaptation.addStats(level, stats);
      previous = progress;
    }
    assertThat(previous).isEqualTo(1D);
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
    assertThat(adaptation.reloadConfigSnapshot("enabled = false\nmaxLevel = 1\n", dataFolder.toPath().resolve("disabled.toml").toFile(), false))
        .as("disabled configuration reload").isTrue();
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
      assertThat(adaptation.isEnabled()).isFalse();
    }
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
    Path file = dataFolder.toPath().resolve("config.toml");
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
    return (SimpleAdaptation<?>) constructor.newInstance(arguments);
  }

  static List<String> adaptationClasses() throws IOException {
    try (Stream<Path> files = Files.walk(ADAPTATION_ROOT)) {
      List<String> classes = files.filter(path -> path.toString().endsWith(".java"))
          .filter(AdaptationCatalogExecutionTest::isAdaptation)
          .map(path -> SOURCE_ROOT.relativize(path).toString().replace('/', '.').replace(".java", ""))
          .sorted()
          .toList();
      assertThat(classes).hasSize(312);
      return classes;
    }
  }

  private static boolean isAdaptation(Path path) {
    try {
      return CONCRETE_ADAPTATION.matcher(Files.readString(path)).find();
    } catch (IOException error) {
      throw new IllegalStateException("Cannot read " + path, error);
    }
  }
}
