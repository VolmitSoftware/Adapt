package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.inventorygui.Element;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AdaptationRequirementsTest {
  @TempDir
  Path temporaryDirectory;

  private Adapt previousAdapt;
  private AdaptConfig previousConfig;
  private Field configField;
  private Logger logger;
  private RecordingHandler handler;
  private MockedStatic<ComponentLogger> componentLogger;
  private Fixture fixture;

  @BeforeEach
  void setUp() throws Exception {
    previousAdapt = Adapt.instance;
    configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    previousConfig = (AdaptConfig) configField.get(null);
    configField.set(null, new AdaptConfig());
    logger = Logger.getLogger("AdaptationRequirementsTest-" + UUID.randomUUID());
    logger.setUseParentHandlers(false);
    logger.setLevel(Level.ALL);
    handler = new RecordingHandler();
    handler.setLevel(Level.ALL);
    logger.addHandler(handler);
    componentLogger = mockStatic(ComponentLogger.class);
    fixture = new Fixture(logger);
    Adapt.instance = fixture.plugin;
  }

  @AfterEach
  void tearDown() throws Exception {
    componentLogger.close();
    logger.removeHandler(handler);
    Adapt.instance = previousAdapt;
    configField.set(null, previousConfig);
  }

  @Test
  void validEntriesParseInOrderWithoutWarnings() {
    List<AdaptationRequirement> parsed = AdaptationRequirement.parseAll(
        "beta-cross",
        List.of("alpha-base:2", " Rift-Blink : 1 ")
    );

    assertThat(parsed).containsExactly(
        new AdaptationRequirement("alpha-base", 2),
        new AdaptationRequirement("rift-blink", 1)
    );
    assertThat(handler.messages(Level.WARNING)).isEmpty();
  }

  @Test
  void malformedEntriesAreDroppedWithOneWarningEach() {
    List<AdaptationRequirement> parsed = AdaptationRequirement.parseAll(
        "beta-cross",
        List.of("alpha-base", "alpha-base:two", "alpha-base:0", "alpha-base:-1", ":2", "alpha-base:3")
    );

    assertThat(parsed).containsExactly(new AdaptationRequirement("alpha-base", 3));
    assertThat(handler.messages(Level.WARNING))
        .hasSize(5)
        .allMatch(message -> message.contains("beta-cross"));
  }

  @Test
  void ownerEditedTomlListBecomesTheAdaptationRequirements() {
    TestAdaptation adaptation = new TestAdaptation(temporaryDirectory.resolve("owner.toml"));

    boolean reloaded = adaptation.reloadConfigSnapshot(
        "requires = [\"alpha-base:2\", \"broken\"]\n",
        temporaryDirectory.resolve("owner.toml").toFile(),
        false
    );

    assertThat(reloaded).isTrue();
    assertThat(adaptation.getRequirements()).containsExactly(new AdaptationRequirement("alpha-base", 2));
    assertThat(handler.messages(Level.WARNING)).hasSize(1).allMatch(message -> message.contains("broken"));
  }

  @Test
  void configConstructorDefaultsBecomeTheAdaptationRequirements() {
    TestAdaptation adaptation = new TestAdaptation(temporaryDirectory.resolve("defaults.toml"));

    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();

    assertThat(adaptation.getRequirements()).containsExactly(new AdaptationRequirement("alpha-chained", 1));
  }

  @Test
  void learningIsDeniedUntilTheRequirementIsLearnedThenAllowed() {
    fixture.requires(fixture.dependent, new AdaptationRequirement("alpha-base", 2));
    fixture.setLevel(fixture.base, 1);

    assertThat(fixture.learn(fixture.dependent, 1)).isEqualTo(AdaptationLearningTransaction.Result.REQUIREMENTS_MISSING);
    assertThat(fixture.level(fixture.dependent)).isZero();
    assertThat(C.stripColor(AdaptationRequirements.describe(
        AdaptationRequirements.missing(fixture.dependent, fixture.data)
    ))).isEqualTo("Alpha Base Lv 2");

    fixture.setLevel(fixture.base, 2);

    assertThat(fixture.learn(fixture.dependent, 1)).isEqualTo(AdaptationLearningTransaction.Result.LEARNED);
    assertThat(fixture.level(fixture.dependent)).isEqualTo(1);
  }

  @Test
  void levelUpIsDeniedWhileARequirementIsOnlyPartiallyMet() {
    fixture.requires(
        fixture.dependent,
        new AdaptationRequirement("alpha-base", 2),
        new AdaptationRequirement("beta-other", 1)
    );
    fixture.setLevel(fixture.base, 2);
    fixture.setLevel(fixture.dependent, 1);

    assertThat(fixture.learn(fixture.dependent, 2)).isEqualTo(AdaptationLearningTransaction.Result.REQUIREMENTS_MISSING);
    assertThat(fixture.level(fixture.dependent)).isEqualTo(1);
    assertThat(AdaptationRequirements.missing(fixture.dependent, fixture.data))
        .containsExactly(new AdaptationRequirement("beta-other", 1));
  }

  @Test
  void regionGrantedLevelsDoNotSatisfyARequirement() {
    fixture.requires(fixture.dependent, new AdaptationRequirement("alpha-base", 1));
    fixture.setLevel(fixture.base, 1);
    fixture.line(fixture.base).getAdaptation("alpha-base").setRegionGranted(true);

    assertThat(fixture.learn(fixture.dependent, 1)).isEqualTo(AdaptationLearningTransaction.Result.REQUIREMENTS_MISSING);
  }

  @Test
  void unknownRequirementIsUnsatisfiableAndNamedInTheDenial() {
    fixture.requires(fixture.dependent, new AdaptationRequirement("ghost-adaptation", 1));

    assertThat(fixture.learn(fixture.dependent, 1)).isEqualTo(AdaptationLearningTransaction.Result.REQUIREMENTS_MISSING);
    assertThat(C.stripColor(AdaptationRequirements.describe(
        AdaptationRequirements.missing(fixture.dependent, fixture.data)
    ))).isEqualTo("ghost-adaptation Lv 1");
  }

  @Test
  void unknownRequirementWarnsOnceAtLoad() {
    fixture.requires(fixture.dependent, new AdaptationRequirement("ghost-adaptation", 1));
    fixture.requires(fixture.cross, new AdaptationRequirement("alpha-base", 1));

    AdaptationRequirements.warnUnresolved(List.of(fixture.alpha, fixture.beta));

    assertThat(handler.messages(Level.WARNING))
        .hasSize(1)
        .allMatch(message -> message.contains("alpha-dependent") && message.contains("ghost-adaptation"));
  }

  @Test
  void unlearningIsBlockedWhileALearnedDependentNeedsTheLevel() {
    fixture.requires(fixture.dependent, new AdaptationRequirement("alpha-base", 2));
    fixture.setLevel(fixture.base, 3);
    fixture.setLevel(fixture.dependent, 1);

    assertThat(fixture.unlearn(fixture.base, 1, false))
        .isEqualTo(AdaptationLearningTransaction.Result.REQUIRED_BY_DEPENDENTS);
    assertThat(fixture.level(fixture.base)).isEqualTo(3);
    assertThat(AdaptationRequirements.dependents(fixture.base, fixture.data, 1)).containsExactly(fixture.dependent);
    assertThat(C.stripColor(AdaptationRequirements.names(List.of(fixture.dependent)))).isEqualTo("Alpha Dependent");

    assertThat(fixture.unlearn(fixture.base, 2, false)).isEqualTo(AdaptationLearningTransaction.Result.UNLEARNED);
    assertThat(fixture.level(fixture.base)).isEqualTo(2);
  }

  @Test
  void crossSkillRequirementGatesLearningAndUnlearning() {
    fixture.requires(fixture.cross, new AdaptationRequirement("alpha-base", 1));

    assertThat(fixture.learn(fixture.cross, 1)).isEqualTo(AdaptationLearningTransaction.Result.REQUIREMENTS_MISSING);

    fixture.setLevel(fixture.base, 1);

    assertThat(fixture.learn(fixture.cross, 1)).isEqualTo(AdaptationLearningTransaction.Result.LEARNED);
    assertThat(fixture.unlearn(fixture.base, 0, false))
        .isEqualTo(AdaptationLearningTransaction.Result.REQUIRED_BY_DEPENDENTS);
    assertThat(fixture.level(fixture.base)).isEqualTo(1);
  }

  @Test
  void forcedRemovalCascadesThroughDependentsAndLogsEachRemoval() {
    fixture.requires(fixture.dependent, new AdaptationRequirement("alpha-base", 2));
    fixture.requires(fixture.cross, new AdaptationRequirement("alpha-dependent", 1));
    fixture.setLevel(fixture.base, 2);
    fixture.setLevel(fixture.dependent, 1);
    fixture.setLevel(fixture.cross, 1);
    fixture.setLevel(fixture.other, 2);
    fixture.line(fixture.dependent).getStorage().put("vault-learning-refund-alpha-dependent-level-1", 4D);

    assertThat(fixture.unlearn(fixture.base, 1, true)).isEqualTo(AdaptationLearningTransaction.Result.UNLEARNED);

    assertThat(fixture.level(fixture.base)).isEqualTo(1);
    assertThat(fixture.level(fixture.dependent)).isZero();
    assertThat(fixture.level(fixture.cross)).isZero();
    assertThat(fixture.level(fixture.other)).isEqualTo(2);
    assertThat(fixture.line(fixture.dependent).getStorage())
        .doesNotContainKey("vault-learning-refund-alpha-dependent-level-1");
    assertThat(handler.messages(Level.INFO))
        .hasSize(2)
        .anyMatch(message -> message.contains("alpha-dependent") && message.contains("Steve"))
        .anyMatch(message -> message.contains("beta-cross") && message.contains("Steve"));
  }

  @Test
  void powerBudgetPruneRemovesDependentsLeftWithoutTheirRequirement() {
    fixture.requires(fixture.dependent, new AdaptationRequirement("alpha-base", 1));
    fixture.setLevel(fixture.base, 1);
    fixture.setLevel(fixture.dependent, 2);
    fixture.data.setRegionPowerBonus(2);

    fixture.data.pruneAdaptationsForPowerBudget();

    assertThat(fixture.level(fixture.base)).isZero();
    assertThat(fixture.level(fixture.dependent)).isZero();
    assertThat(handler.messages(Level.INFO)).hasSize(1).allMatch(message -> message.contains("alpha-dependent"));
  }

  private static final class Fixture {
    private final Adapt plugin = mock(Adapt.class);
    private final AdaptServer server = mock(AdaptServer.class);
    private final SkillRegistry registry = mock(SkillRegistry.class);
    private final Player player = mock(Player.class);
    private final AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    private final PlayerData data = new PlayerData();
    private final Skill<?> alpha = skill("alpha");
    private final Skill<?> beta = skill("beta");
    private final Adaptation<?> base = adaptation(alpha, "alpha-base", "Alpha Base");
    private final Adaptation<?> dependent = adaptation(alpha, "alpha-dependent", "Alpha Dependent");
    private final Adaptation<?> other = adaptation(beta, "beta-other", "Beta Other");
    private final Adaptation<?> cross = adaptation(beta, "beta-cross", "Beta Cross");

    private Fixture(Logger logger) {
      when(plugin.getLogger()).thenReturn(logger);
      when(plugin.getAdaptServer()).thenReturn(server);
      when(server.getSkillRegistry()).thenReturn(registry);
      when(registry.getSkills()).thenReturn(List.of(alpha, beta));
      doReturn(adaptations(base, dependent)).when(alpha).getAdaptations();
      doReturn(adaptations(other, cross)).when(beta).getAdaptations();
      when(player.getName()).thenReturn("Steve");
      when(adaptPlayer.getData()).thenReturn(data);
      data.setRegionPowerBonus(64);
      for (Skill<?> skill : List.of(alpha, beta)) {
        PlayerSkillLine line = new PlayerSkillLine();
        line.setLine(skill.getName());
        line.setKnowledge(100L);
        data.getSkillLines().put(skill.getName(), line);
        when(adaptPlayer.getSkillLine(skill.getName())).thenReturn(line);
      }
    }

    private Skill<?> skill(String name) {
      Skill<?> skill = mock(Skill.class);
      when(skill.getName()).thenReturn(name);
      return skill;
    }

    private Adaptation<?> adaptation(Skill<?> skill, String name, String displayName) {
      Adaptation<?> adaptation = mock(Adaptation.class);
      doReturn(skill).when(adaptation).getSkill();
      when(adaptation.getName()).thenReturn(name);
      when(adaptation.getDisplayName()).thenReturn(C.BOLD + displayName);
      when(adaptation.getMaxLevel()).thenReturn(5);
      when(adaptation.getPlayer(player)).thenReturn(adaptPlayer);
      return adaptation;
    }

    private KList<Adaptation<?>> adaptations(Adaptation<?> first, Adaptation<?> second) {
      KList<Adaptation<?>> list = new KList<>();
      list.add(first);
      list.add(second);
      return list;
    }

    private void requires(Adaptation<?> adaptation, AdaptationRequirement... requirements) {
      when(adaptation.getRequirements()).thenReturn(List.of(requirements));
    }

    private PlayerSkillLine line(Adaptation<?> adaptation) {
      return data.getSkillLines().get(adaptation.getSkill().getName());
    }

    private void setLevel(Adaptation<?> adaptation, int level) {
      line(adaptation).setAdaptation(adaptation, level);
    }

    private int level(Adaptation<?> adaptation) {
      return line(adaptation).getAdaptationLevel(adaptation.getName());
    }

    private AdaptationLearningTransaction.Result learn(Adaptation<?> adaptation, int level) {
      return AdaptationLearningTransaction.learn(adaptation, player, level, false);
    }

    private AdaptationLearningTransaction.Result unlearn(Adaptation<?> adaptation, int level, boolean force) {
      return AdaptationLearningTransaction.unlearn(adaptation, player, level, force);
    }
  }

  private static final class TestAdaptation extends SimpleAdaptation<TestConfig> {
    private final File configFile;

    private TestAdaptation(Path configPath) {
      super("alpha-test");
      configFile = configPath.toFile();
      registerConfiguration(TestConfig.class);
    }

    @Override
    protected File getConfigFile() {
      return configFile;
    }

    @Override
    protected TestConfig createDefaultConfig() {
      return new TestConfig();
    }

    @Override
    public void addStats(int level, Element element) {
    }
  }

  private static final class TestConfig extends AdaptationConfig {
    private TestConfig() {
      requires = new ArrayList<>(List.of("alpha-chained:1"));
    }
  }

  private static final class RecordingHandler extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    private List<String> messages(Level level) {
      List<String> messages = new ArrayList<>();
      for (LogRecord record : records) {
        if (record.getLevel() == level) {
          messages.add(record.getMessage());
        }
      }
      return messages;
    }
  }
}
