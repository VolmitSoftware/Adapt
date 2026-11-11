package art.arcane.adapt.command;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.util.director.context.AdaptationListingHandler;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.localization.TextKey;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CommandDefaultTest extends AdaptTestBase {
  private Field configField;
  private AdaptConfig previousConfig;
  private CommandSender sender;
  private TestSkill mining;
  private TestSkill fishing;
  private TestAdaptation veinMiner;
  private TestAdaptation autosmelt;
  private TestAdaptation lure;

  @BeforeAll
  static void initializeSounds() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<Sound> sounds = Registry.SOUNDS;
      doAnswer(call -> mock(Sound.class)).when(sounds).getOrThrow(any(Key.class));
      assertThat(Sound.BLOCK_DEEPSLATE_BREAK).isNotNull();
    }
  }

  @BeforeEach
  void prepareRuntime() throws Exception {
    configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    previousConfig = (AdaptConfig) configField.get(null);
    configField.set(null, null);

    when(plugin.getDataFolder(any(String[].class))).thenAnswer(invocation -> {
      Object[] arguments = invocation.getArguments();
      String[] path = new String[arguments.length];
      for (int i = 0; i < arguments.length; i++) {
        path[i] = String.valueOf(arguments[i]);
      }
      File folder = new File(dataFolder, String.join(File.separator, path));
      folder.mkdirs();
      return folder;
    });

    mining = new TestSkill("mining");
    fishing = new TestSkill("fishing");
    veinMiner = new TestAdaptation("mining-vein-miner", false);
    autosmelt = new TestAdaptation("mining-autosmelt", false);
    lure = new TestAdaptation("fishing-lure", false);
    mining.registerAdaptation(veinMiner);
    mining.registerAdaptation(autosmelt);
    fishing.registerAdaptation(lure);

    AdaptServer server = mock(AdaptServer.class);
    SkillRegistry registry = mock(SkillRegistry.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getSkillRegistry()).thenReturn(registry);
    when(registry.getAllSkills()).thenAnswer(invocation -> new ArrayList<Skill<?>>(List.of(mining, fishing)));
    when(registry.getSkill(anyString())).thenAnswer(invocation -> skill(invocation.getArgument(0)));
    when(registry.hotReloadSkillConfig(anyString())).thenAnswer(invocation -> hotReloadSkill(invocation.getArgument(0)));
    when(registry.hotReloadAdaptationConfig(anyString())).thenAnswer(invocation -> hotReloadAdaptation(invocation.getArgument(0)));

    sender = mock(CommandSender.class);
    when(sender.hasPermission("adapt.configurator")).thenReturn(true);
    BukkitDirectorContext.touch(sender);
  }

  @AfterEach
  void restoreRuntime() throws Exception {
    BukkitDirectorContext.remove();
    configField.set(null, previousConfig);
  }

  @Test
  void allArchivesEveryConfigThenRecreatesItWithDefaults() throws IOException {
    customizeEverything();

    new CommandDefault().all();

    assertThat(archived("adapt.toml")).contains("welcomeMessage = false");
    assertThat(archived("skills/mining.toml")).contains("reward = 77");
    assertThat(archived("skills/fishing.toml")).contains("reward = 77");
    assertThat(archived("adaptations/mining-vein-miner.toml")).contains("retainedValue = 9");
    assertThat(archived("adaptations/mining-autosmelt.toml")).contains("retainedValue = 9");
    assertThat(archived("adaptations/fishing-lure.toml")).contains("retainedValue = 9");

    assertThat(live("adapt.toml")).contains("welcomeMessage = true");
    assertThat(live("skills/mining.toml")).contains("reward = 10");
    assertThat(live("skills/fishing.toml")).contains("reward = 10");
    assertThat(live("adaptations/mining-vein-miner.toml")).contains("retainedValue = 3");
    assertThat(live("adaptations/mining-autosmelt.toml")).contains("retainedValue = 3");
    assertThat(live("adaptations/fishing-lure.toml")).contains("retainedValue = 3");

    assertThat(AdaptConfig.get().isWelcomeMessage()).isTrue();
    assertThat(mining.getConfig().reward).isEqualTo(10D);
    assertThat(fishing.getConfig().reward).isEqualTo(10D);
    assertThat(veinMiner.getConfig().retainedValue).isEqualTo(3);
    assertThat(autosmelt.getConfig().retainedValue).isEqualTo(3);
    assertThat(lure.getConfig().retainedValue).isEqualTo(3);

    assertThat(messages()).anySatisfy(message -> assertThat(message).contains("Archived 6 config files"));
    assertThat(messages()).anySatisfy(message -> assertThat(message).contains("Reset 6 configs"));
    assertThat(messages()).noneSatisfy(message -> assertThat(message).contains("Failed"));
  }

  @Test
  void allNamesTheConfigThatFailedAndStillResetsTheRest() throws IOException {
    TestAdaptation broken = new TestAdaptation("mining-broken", true);
    mining.registerAdaptation(broken);
    customizeEverything();
    Files.writeString(file("adaptations/mining-broken.toml"), "enabled = true\nretainedValue = 9\n");
    assertThat(broken.reloadConfigFromDisk(false)).isTrue();

    new CommandDefault().all();

    assertThat(archived("adaptations/mining-broken.toml")).contains("retainedValue = 9");
    assertThat(live("adaptations/mining-broken.toml")).contains("retainedValue = 9");
    assertThat(broken.getConfig().retainedValue).isEqualTo(9);

    assertThat(live("skills/mining.toml")).contains("reward = 10");
    assertThat(live("adaptations/mining-vein-miner.toml")).contains("retainedValue = 3");
    assertThat(live("adaptations/fishing-lure.toml")).contains("retainedValue = 3");
    assertThat(mining.getConfig().reward).isEqualTo(10D);
    assertThat(veinMiner.getConfig().retainedValue).isEqualTo(3);
    assertThat(AdaptConfig.get().isWelcomeMessage()).isTrue();

    assertThat(messages()).anySatisfy(message -> assertThat(message).contains("Archived 7 config files"));
    assertThat(messages()).anySatisfy(message -> assertThat(message).contains("Reset 6 configs"));
    assertThat(messages()).filteredOn(message -> message.contains("Failed"))
        .singleElement()
        .satisfies(message -> assertThat(message).contains("mining-broken"));
  }

  @Test
  void skillResetRecreatesOnlyThatSkillConfigWithDefaults() throws IOException {
    customizeEverything();

    new CommandDefault().skill(new AdaptationListingHandler.SkillProvider("mining"));

    assertThat(live("skills/mining.toml")).contains("reward = 10");
    assertThat(mining.getConfig().reward).isEqualTo(10D);
    assertThat(live("skills/fishing.toml")).contains("reward = 77");
    assertThat(live("adaptations/mining-vein-miner.toml")).contains("retainedValue = 9");
    assertThat(veinMiner.getConfig().retainedValue).isEqualTo(9);
    assertThat(messages()).singleElement().satisfies(message -> assertThat(message).contains("Reset config for skill mining"));
  }

  @Test
  void adaptationResetRecreatesOnlyThatAdaptationConfigWithDefaults() throws IOException {
    customizeEverything();

    new CommandDefault().adaptation(new AdaptationListingHandler.AdaptationProvider("mining:mining-vein-miner"));

    assertThat(live("adaptations/mining-vein-miner.toml")).contains("retainedValue = 3");
    assertThat(veinMiner.getConfig().retainedValue).isEqualTo(3);
    assertThat(live("adaptations/mining-autosmelt.toml")).contains("retainedValue = 9");
    assertThat(autosmelt.getConfig().retainedValue).isEqualTo(9);
    assertThat(live("skills/mining.toml")).contains("reward = 77");
    assertThat(messages()).singleElement()
        .satisfies(message -> assertThat(message).contains("Reset config for adaptation mining-vein-miner"));
  }

  @Test
  void resetReportsTheConfigByNameWhenDefaultsCannotBeWritten() throws IOException {
    TestAdaptation broken = new TestAdaptation("mining-broken", true);
    mining.registerAdaptation(broken);
    Files.createDirectories(file("adaptations"));
    Files.writeString(file("adaptations/mining-broken.toml"), "enabled = true\nretainedValue = 9\n");
    assertThat(broken.reloadConfigFromDisk(false)).isTrue();

    new CommandDefault().adaptation(new AdaptationListingHandler.AdaptationProvider("mining:mining-broken"));

    assertThat(live("adaptations/mining-broken.toml")).contains("retainedValue = 9");
    assertThat(messages()).singleElement()
        .satisfies(message -> assertThat(message).contains("Failed").contains("mining-broken"));
  }

  private void customizeEverything() throws IOException {
    Files.createDirectories(file("skills"));
    Files.createDirectories(file("adaptations"));
    Files.writeString(file("adapt.toml"), "welcomeMessage = false\n");
    assertThat(AdaptConfig.get().isWelcomeMessage()).isFalse();
    for (TestSkill skill : List.of(mining, fishing)) {
      Files.writeString(file("skills/" + skill.getName() + ".toml"), "enabled = true\nreward = 77\n");
      assertThat(skill.reloadConfigFromDisk(false)).isTrue();
      assertThat(skill.getConfig().reward).isEqualTo(77D);
    }
    for (TestAdaptation adaptation : List.of(veinMiner, autosmelt, lure)) {
      Files.writeString(file("adaptations/" + adaptation.getName() + ".toml"), "enabled = true\nretainedValue = 9\n");
      assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
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
      if ((method.equals("sendRichMessage") || method.equals("sendMessage")) && invocation.getArguments().length == 1
          && invocation.getArgument(0) instanceof String text) {
        sent.add(text);
      }
    }
    return sent;
  }

  private Skill<?> skill(String name) {
    for (TestSkill skill : List.of(mining, fishing)) {
      if (skill.getName().equalsIgnoreCase(name)) {
        return skill;
      }
    }
    return null;
  }

  private boolean hotReloadSkill(String name) {
    if (!(skill(name) instanceof TestSkill skill) || !skill.validateConfigFromDisk()) {
      return false;
    }
    List<SimpleAdaptation<?>> adaptations = new ArrayList<>();
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (!(adaptation instanceof SimpleAdaptation<?> simple)) {
        continue;
      }
      if (!simple.validateConfigFromDisk()) {
        return false;
      }
      adaptations.add(simple);
    }
    if (!skill.reloadConfigFromDisk(true)) {
      return false;
    }
    for (SimpleAdaptation<?> adaptation : adaptations) {
      if (!adaptation.reloadConfigFromDisk(false)) {
        return false;
      }
    }
    return true;
  }

  private boolean hotReloadAdaptation(String name) {
    for (TestSkill skill : List.of(mining, fishing)) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation.getName().equalsIgnoreCase(name)) {
          return adaptation instanceof SimpleAdaptation<?> simple
              && simple.validateConfigFromDisk()
              && simple.reloadConfigFromDisk(true);
        }
      }
    }
    return false;
  }

  private static final class TestSkill extends SimpleSkill<TestSkillConfig> {
    private TestSkill(String name) {
      super(name, SkillPresentation.of(
          TextKey.of("test.skill.name", "Test Skill"),
          TextKey.of("test.skill.icon", "Test"),
          TextKey.of("test.skill.description", "Test skill description")
      ));
      registerConfiguration(TestSkillConfig.class);
    }

    @Override
    public boolean isEnabled() {
      return getConfig().enabled;
    }
  }

  public static final class TestSkillConfig {
    boolean enabled = true;
    double reward = 10D;

    public TestSkillConfig() {
    }
  }

  private static final class TestAdaptation extends SimpleAdaptation<TestAdaptationConfig> {
    private final boolean rejectsDefaults;

    private TestAdaptation(String name, boolean rejectsDefaults) {
      super(name);
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
    public void addStats(int level, Element element) {
    }
  }

  public static final class TestAdaptationConfig extends AdaptationConfig {
    private int retainedValue = 3;

    public TestAdaptationConfig() {
    }
  }
}
