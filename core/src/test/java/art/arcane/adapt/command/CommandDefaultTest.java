package art.arcane.adapt.command;

import static org.mockito.ArgumentMatchers.any;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.testkit.MockedEngine;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import art.arcane.volmlib.util.localization.TextKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandDefaultTest extends AdaptTestBase {
  private Field configField;
  private AdaptConfig previousConfig;
  private CommandSource sender;
  private TestSkill mining;
  private TestSkill fishing;
  private TestAdaptation veinMiner;
  private TestAdaptation autosmelt;
  private TestAdaptation lure;
  private MockedEngine engine;

  @BeforeEach
  void prepareRuntime() throws Exception {
    configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    previousConfig = (AdaptConfig) configField.get(null);
    configField.set(null, null);

    engine = new MockedEngine(core);
    mining = engine.skill(() -> new TestSkill("mining"));
    fishing = engine.skill(() -> new TestSkill("fishing"));
    veinMiner = engine.adaptation(mining, () -> new TestAdaptation("mining-vein-miner", false));
    autosmelt = engine.adaptation(mining, () -> new TestAdaptation("mining-autosmelt", false));
    lure = engine.adaptation(fishing, () -> new TestAdaptation("fishing-lure", false));

    AdaptServer server = mock(AdaptServer.class);
    SkillRegistry registry = mock(SkillRegistry.class);
    doReturn(server).when(core).getAdaptServer();
    when(server.getSkillRegistry()).thenReturn(registry);
    when(registry.getAllSkills()).thenAnswer(invocation -> new ArrayList<Skill<?>>(List.of(mining, fishing)));
    when(registry.getAnySkill(any(Id.class))).thenAnswer(invocation -> skill(invocation.getArgument(0).toString()));
    when(registry.hotReloadSkillConfig(any(Id.class))).thenAnswer(invocation -> hotReloadSkill(invocation.getArgument(0)));
    when(registry.hotReloadAdaptationConfig(any(Id.class))).thenAnswer(invocation -> hotReloadAdaptation(invocation.getArgument(0)));

    sender = mock(CommandSource.class);
    when(sender.hasPermission("adapt.configurator")).thenReturn(true);
    CommandContext.touch(sender);
  }

  @AfterEach
  void restoreRuntime() throws Exception {
    CommandContext.remove();
    engine.close();
    configField.set(null, previousConfig);
  }

  @Test
  void allArchivesEveryConfigThenRecreatesItWithDefaults() throws IOException {
    customizeEverything();

    new CommandDefault().all();

    assertThat(archived("adapt.toml")).contains("welcomeMessage = false");
    assertThat(archived("skills/adapt/mining.toml")).contains("reward = 77");
    assertThat(archived("skills/adapt/fishing.toml")).contains("reward = 77");
    assertThat(archived("adaptations/adapt/mining-vein-miner.toml")).contains("retainedValue = 9");
    assertThat(archived("adaptations/adapt/mining-autosmelt.toml")).contains("retainedValue = 9");
    assertThat(archived("adaptations/adapt/fishing-lure.toml")).contains("retainedValue = 9");

    assertThat(live("adapt.toml")).contains("welcomeMessage = true");
    assertThat(live("skills/adapt/mining.toml")).contains("reward = 10");
    assertThat(live("skills/adapt/fishing.toml")).contains("reward = 10");
    assertThat(live("adaptations/adapt/mining-vein-miner.toml")).contains("retainedValue = 3");
    assertThat(live("adaptations/adapt/mining-autosmelt.toml")).contains("retainedValue = 3");
    assertThat(live("adaptations/adapt/fishing-lure.toml")).contains("retainedValue = 3");

    assertThat(AdaptConfig.get().isWelcomeMessage()).isTrue();
    assertThat(mining.getConfig().reward).isEqualTo(10D);
    assertThat(fishing.getConfig().reward).isEqualTo(10D);
    assertThat(veinMiner.getConfig().retainedValue).isEqualTo(3);
    assertThat(autosmelt.getConfig().retainedValue).isEqualTo(3);
    assertThat(lure.getConfig().retainedValue).isEqualTo(3);

    assertThat(messages()).anySatisfy(message -> assertThat(message).contains("Archived 6 config files"));
    assertThat(messages()).anySatisfy(message -> assertThat(message).contains("Reset 18 configs"));
    assertThat(messages()).noneSatisfy(message -> assertThat(message).contains("Failed"));
  }

  @Test
  void allNamesTheConfigThatFailedAndStillResetsTheRest() throws IOException {
    TestAdaptation broken = engine.adaptation(mining, () -> new TestAdaptation("mining-broken", true));
    customizeEverything();
    Files.writeString(file("adaptations/adapt/mining-broken.toml"), "enabled = true\nretainedValue = 9\n");
    assertThat(ComponentHost.of(broken).reloadConfigFromDisk(false)).isTrue();

    new CommandDefault().all();

    assertThat(archived("adaptations/adapt/mining-broken.toml")).contains("retainedValue = 9");
    assertThat(live("adaptations/adapt/mining-broken.toml")).contains("retainedValue = 9");
    assertThat(broken.getConfig().retainedValue).isEqualTo(9);

    assertThat(live("skills/adapt/mining.toml")).contains("reward = 10");
    assertThat(live("adaptations/adapt/mining-vein-miner.toml")).contains("retainedValue = 3");
    assertThat(live("adaptations/adapt/fishing-lure.toml")).contains("retainedValue = 3");
    assertThat(mining.getConfig().reward).isEqualTo(10D);
    assertThat(veinMiner.getConfig().retainedValue).isEqualTo(3);
    assertThat(AdaptConfig.get().isWelcomeMessage()).isTrue();

    assertThat(messages()).anySatisfy(message -> assertThat(message).contains("Archived 7 config files"));
    assertThat(messages()).anySatisfy(message -> assertThat(message).contains("Reset 18 configs"));
    assertThat(messages()).filteredOn(message -> message.contains("Failed"))
        .singleElement()
        .satisfies(message -> assertThat(message).contains("mining-broken"));
  }

  @Test
  void skillResetRecreatesOnlyThatSkillConfigWithDefaults() throws IOException {
    customizeEverything();

    new CommandDefault().skill(new AdaptationListingHandler.SkillProvider("mining"));

    assertThat(live("skills/adapt/mining.toml")).contains("reward = 10");
    assertThat(mining.getConfig().reward).isEqualTo(10D);
    assertThat(live("skills/adapt/fishing.toml")).contains("reward = 77");
    assertThat(live("adaptations/adapt/mining-vein-miner.toml")).contains("retainedValue = 9");
    assertThat(veinMiner.getConfig().retainedValue).isEqualTo(9);
    assertThat(messages()).singleElement().satisfies(message -> assertThat(message).contains("Reset config for skill adapt:mining"));
  }

  @Test
  void adaptationResetRecreatesOnlyThatAdaptationConfigWithDefaults() throws IOException {
    customizeEverything();

    new CommandDefault().adaptation(new AdaptationListingHandler.AdaptationProvider("mining-vein-miner"));

    assertThat(live("adaptations/adapt/mining-vein-miner.toml")).contains("retainedValue = 3");
    assertThat(veinMiner.getConfig().retainedValue).isEqualTo(3);
    assertThat(live("adaptations/adapt/mining-autosmelt.toml")).contains("retainedValue = 9");
    assertThat(autosmelt.getConfig().retainedValue).isEqualTo(9);
    assertThat(live("skills/adapt/mining.toml")).contains("reward = 77");
    assertThat(messages()).singleElement()
        .satisfies(message -> assertThat(message).contains("Reset config for adaptation adapt:mining-vein-miner"));
  }

  @Test
  void resetReportsTheConfigByNameWhenDefaultsCannotBeWritten() throws IOException {
    TestAdaptation broken = engine.adaptation(mining, () -> new TestAdaptation("mining-broken", true));
    Files.createDirectories(file("adaptations/adapt"));
    Files.writeString(file("adaptations/adapt/mining-broken.toml"), "enabled = true\nretainedValue = 9\n");
    assertThat(ComponentHost.of(broken).reloadConfigFromDisk(false)).isTrue();

    new CommandDefault().adaptation(new AdaptationListingHandler.AdaptationProvider("mining-broken"));

    assertThat(live("adaptations/adapt/mining-broken.toml")).contains("retainedValue = 9");
    assertThat(messages()).singleElement()
        .satisfies(message -> assertThat(message).contains("Failed").contains("mining-broken"));
  }

  private void customizeEverything() throws IOException {
    Files.createDirectories(file("skills/adapt"));
    Files.createDirectories(file("adaptations/adapt"));
    Files.writeString(file("adapt.toml"), "welcomeMessage = false\n");
    assertThat(AdaptConfig.get().isWelcomeMessage()).isFalse();
    for (TestSkill skill : List.of(mining, fishing)) {
      Files.writeString(file("skills/adapt/" + skill.getId().path() + ".toml"), "enabled = true\nreward = 77\n");
      assertThat(ComponentHost.of(skill).reloadConfigFromDisk(false)).isTrue();
      assertThat(skill.getConfig().reward).isEqualTo(77D);
    }
    for (TestAdaptation adaptation : List.of(veinMiner, autosmelt, lure)) {
      Files.writeString(file("adaptations/adapt/" + adaptation.getId().path() + ".toml"), "enabled = true\nretainedValue = 9\n");
      assertThat(ComponentHost.of(adaptation).reloadConfigFromDisk(false)).isTrue();
      assertThat(adaptation.getConfig().retainedValue).isEqualTo(9);
    }
  }

  private Path file(String relative) {
    return dataFolder.toPath().resolve(relative);
  }

  private String live(String relative) throws IOException {
    Path path = file(relative);
    assertThat(path).exists();
    return Files.readString(path);
  }

  private String archived(String relative) throws IOException {
    Path archiveRoot = file("config-archive");
    assertThat(archiveRoot).isDirectory();
    try (Stream<Path> archives = Files.list(archiveRoot)) {
      Path archive = archives.findFirst().orElseThrow();
      Path path = archive.resolve(relative);
      assertThat(path).exists();
      return Files.readString(path);
    }
  }

  private List<String> messages() {
    List<String> sent = new ArrayList<>();
    for (Invocation invocation : Mockito.mockingDetails(sender).getInvocations()) {
      String method = invocation.getMethod().getName();
      if (method.equals("sendSection") && invocation.getArguments().length == 1
          && invocation.getArgument(0) instanceof String text) {
        sent.add(text);
      }
    }
    return sent;
  }

  private Skill<?> skill(String name) {
    for (TestSkill skill : List.of(mining, fishing)) {
      if (skill.getId().equals(Id.tryParseAdapt(name))) {
        return skill;
      }
    }
    return null;
  }

  private boolean hotReloadSkill(Id name) {
    if (!(skill(name.toString()) instanceof TestSkill skill) || !ComponentHost.of(skill).validateConfigFromDisk()) {
      return false;
    }
    List<SimpleAdaptation<?>> adaptations = new ArrayList<>();
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (!(adaptation instanceof SimpleAdaptation<?> simple)) {
        continue;
      }
      if (!ComponentHost.of(simple).validateConfigFromDisk()) {
        return false;
      }
      adaptations.add(simple);
    }
    if (!ComponentHost.of(skill).reloadConfigFromDisk(true)) {
      return false;
    }
    for (SimpleAdaptation<?> adaptation : adaptations) {
      if (!ComponentHost.of(adaptation).reloadConfigFromDisk(false)) {
        return false;
      }
    }
    return true;
  }

  private boolean hotReloadAdaptation(Id name) {
    for (TestSkill skill : List.of(mining, fishing)) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation.getId().equals(name)) {
          return adaptation instanceof SimpleAdaptation<?> simple
              && ComponentHost.of(simple).validateConfigFromDisk()
              && ComponentHost.of(simple).reloadConfigFromDisk(true);
        }
      }
    }
    return false;
  }

  private static final class TestSkill extends SimpleSkill<TestSkillConfig> {
    private TestSkill(String name) {
      super(Id.adapt(name));
      registerConfiguration(TestSkillConfig.class);
    }

    @Override
    public boolean isEnabled() {
      return getConfig().enabled;
    }
  }

  public static final class TestSkillConfig extends SkillConfig {
    double reward = 10D;

    public TestSkillConfig() {
    }
  }

  private static final class TestAdaptation extends SimpleAdaptation<TestAdaptationConfig> {
    private final boolean rejectsDefaults;

    private TestAdaptation(String name, boolean rejectsDefaults) {
      super(Id.parseAdapt(name));
      this.rejectsDefaults = rejectsDefaults;
      registerConfiguration(TestAdaptationConfig.class);
    }

    @Override
    protected void normalizeLoadedConfig(TestAdaptationConfig loadedConfig) {
      if (rejectsDefaults && loadedConfig.retainedValue == 3) {
        throw new IllegalStateException("defaults rejected");
      }
    }

    @Override
    public void addStats(int level, StatLines element) {
    }
  }

  public static final class TestAdaptationConfig extends AdaptationConfig {
    private int retainedValue = 3;

    public TestAdaptationConfig() {
    }
  }
}
