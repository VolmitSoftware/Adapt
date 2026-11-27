package art.arcane.adapt.client.view;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillMenus;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptDebugMode;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.gui.SkillsGui;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The access a client receives must match what the chest menus decide for the same player. */
class PlayerAccessParityTest {
  @TempDir
  Path data;

  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private SkillRegistry registry;
  private boolean showAll;
  private boolean noRefunds;

  @BeforeEach
  void start() throws ReflectiveOperationException {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Viewer", 0.5D, 64D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillRift.class, SkillAgility.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    showAll = flag("guiShowAllSkills");
    noRefunds = flag("hardcoreNoRefunds");
  }

  @AfterEach
  void stop() throws ReflectiveOperationException {
    setFlag("guiShowAllSkills", showAll);
    setFlag("hardcoreNoRefunds", noRefunds);
    AdaptDebugMode.setActive(player.uuid(), false);
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void skillsListShowsOnlySkillsWithProgressByDefault() {
    profile.getData().getSkillLine("rift").giveKnowledge(3L);

    PlayerAccess access = access();

    assertThat(access.skills()).containsExactly("adapt:rift").isEqualTo(chestSkills());
    assertThat(access.adaptationRows().keySet()).containsExactlyInAnyOrder("adapt:rift", "adapt:agility", "adapt:stealth");
    assertThat(access.refunds()).isTrue();
    assertThat(access.debug()).isFalse();
  }

  @Test
  void showAllListsEveryEnabledSkillInChestOrder() throws ReflectiveOperationException {
    setFlag("guiShowAllSkills", true);

    PlayerAccess access = access();

    assertThat(access.skills()).containsExactlyInAnyOrder("adapt:rift", "adapt:agility", "adapt:stealth").isEqualTo(chestSkills());
  }

  @Test
  void deniedSkillsAndAdaptationsAreLeftOut() throws ReflectiveOperationException {
    setFlag("guiShowAllSkills", true);
    player.setOp(false);
    player.deny("adapt.use.adapt.agility");
    player.deny("adapt.use.adapt.rift.blink");

    PlayerAccess access = access();

    assertThat(access.skills()).doesNotContain("adapt:agility").isEqualTo(chestSkills());
    assertThat(access.canOpen("adapt:agility")).isFalse();
    assertThat(access.shows("adapt:rift", "adapt:rift/blink")).isFalse();
    assertThat(access.rows("adapt:rift")).isEqualTo(chestRows("adapt:rift")).isNotEmpty();
  }

  @Test
  void adaptationRowsFollowTheSkillMenu() {
    PlayerAccess access = access();

    for (Skill<?> skill : registry.getSkills()) {
      assertThat(access.rows(SkillHost.lineKey(skill))).as(SkillHost.lineKey(skill)).isEqualTo(chestRows(SkillHost.lineKey(skill)));
    }
  }

  @Test
  void hardcoreNoRefundsAndDebugModeReachTheClient() throws ReflectiveOperationException {
    setFlag("hardcoreNoRefunds", true);
    AdaptDebugMode.setActive(player.uuid(), true);

    PlayerAccess access = access();

    assertThat(access.refunds()).isFalse();
    assertThat(access.debug()).isTrue();
    assertThat(access.skills()).containsExactlyInAnyOrder("adapt:rift", "adapt:agility", "adapt:stealth").isEqualTo(chestSkills());
  }

  @Test
  void catalogNamesCarryNoSymbolOrColour() {
    CatalogView catalog = CatalogViews.build(registry, player.uuid(), List.of(), false);

    for (SkillView skill : catalog.skills()) {
      assertThat(skill.name()).as(skill.id().toString()).doesNotContain("§").matches("^[\\p{L}\\p{N}].*");
      for (AdaptationView adaptation : skill.adaptations()) {
        assertThat(adaptation.name()).as(adaptation.id().toString()).doesNotContain("§").isNotBlank();
      }
    }
    assertThat(catalog.skills()).extracting(SkillView::name).contains("Rift", "Agility", "Stealth");
  }

  private PlayerAccess access() {
    return PlayerViews.build(registry, profile).access();
  }

  private List<String> chestSkills() {
    List<String> names = new ArrayList<>();
    for (Skill<?> skill : SkillsGui.visibleSkills(profile, player)) {
      names.add(SkillHost.lineKey(skill));
    }
    return names;
  }

  private List<List<String>> chestRows(String skillId) {
    List<List<String>> rows = new ArrayList<>();
    for (List<Adaptation<?>> row : SkillMenus.adaptationRows(registry.getSkill(skillId), player)) {
      List<String> ids = new ArrayList<>();
      for (Adaptation<?> adaptation : row) {
        ids.add(adaptation.getId().toString());
      }
      rows.add(ids);
    }
    return rows;
  }

  private static boolean flag(String name) throws ReflectiveOperationException {
    Field field = AdaptConfig.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.getBoolean(AdaptConfig.get());
  }

  private static void setFlag(String name, boolean value) throws ReflectiveOperationException {
    Field field = AdaptConfig.class.getDeclaredField(name);
    field.setAccessible(true);
    field.setBoolean(AdaptConfig.get(), value);
  }
}
