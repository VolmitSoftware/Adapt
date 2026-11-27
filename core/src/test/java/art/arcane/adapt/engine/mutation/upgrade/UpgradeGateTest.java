package art.arcane.adapt.engine.mutation.upgrade;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UpgradeGateTest {
  private static final Id SNARE = Id.adapt("hunter-snare");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Gatekeeper", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillHunter.class, SkillBrewing.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    platform.scheduler().advance(200);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void hasAdaptationFindsOriginalsWhoseIdDoesNotStartWithTheirSkill() {
    assertThat(profile.hasAdaptation(Id.adapt("sword/bloody_blade"))).isFalse();
    profile.getData().getSkillLine("swords").setAdaptation(registry.getAdaptation(Id.adapt("sword/bloody_blade")), 2);

    assertThat(profile.hasAdaptation(Id.adapt("sword/bloody_blade"))).isTrue();
    assertThat(profile.hasAdaptation(Id.adapt("sword/machete"))).isFalse();
    assertThat(profile.hasAdaptation(Id.adapt("no-such-adaptation"))).isFalse();
  }

  @Test
  void aMutatedPlayerHoldsTheUpgradeTheOriginalsItFusesAndKeepsTheRestAtLevelOne() {
    mutate();
    Adaptation<?> witherblade = registry.getAdaptation(Id.adapt("swords/upgrade/witherblade"));
    Adaptation<?> bloody = registry.getAdaptation(Id.adapt("sword/bloody_blade"));
    profile.getData().getSkillLine("swords").setAdaptation(witherblade, 1);

    assertThat(profile.hasAdaptation(Id.adapt("swords/upgrade/witherblade"))).isTrue();
    assertThat(profile.hasAdaptation(Id.adapt("sword/bloody_blade"))).isTrue();
    assertThat(profile.hasAdaptation(Id.adapt("sword/poison_blade"))).isTrue();
    assertThat(profile.hasAdaptation(Id.adapt("sword/machete"))).isTrue();
    assertThat(AdaptationHost.of(registry.getAdaptation(Id.adapt("sword/machete"))).level(profile)).isEqualTo(1);
    assertThat(Lending.level(bloody, (Adaptation<?> held) -> AdaptationHost.of(held).level(profile))).isEqualTo(bloody.getMaxLevel() - 2);
    profile.getData().getSkillLine("swords").setAdaptation(witherblade, 3);
    assertThat(Lending.level(bloody, (Adaptation<?> held) -> AdaptationHost.of(held).level(profile))).isEqualTo(bloody.getMaxLevel());
  }

  @Test
  void anOriginalsRecipeStaysCraftableThroughTheUpgradeThatFusesIt() {
    assertThat(registry.getRequiredAdaptation(SNARE)).isSameAs(registry.getAdaptation(Id.adapt("hunter/snare_line")));
    mutate();
    assertThat(craft().isCancelled()).isTrue();

    profile.getData().getSkillLine("hunter").setAdaptation(registry.getAdaptation(Id.adapt("hunter/upgrade/stalkers_trail")), 3);

    assertThat(craft().isCancelled()).isFalse();
  }

  @Test
  void aMutatedBrewerStillHoldsTheBottledPotionsItsUpgradeFuses() {
    mutate("brewing", "swords");
    assertThat(profile.hasAdaptation(Id.adapt("brewing/blindness"))).isFalse();

    profile.getData().getSkillLine("brewing").setAdaptation(registry.getAdaptation(Id.adapt("brewing/upgrade/witchs_shelf")), 1);

    assertThat(profile.hasAdaptation(Id.adapt("brewing/upgrade/witchs_shelf"))).isTrue();
    assertThat(profile.hasAdaptation(Id.adapt("brewing/blindness"))).isTrue();
    assertThat(profile.hasAdaptation(Id.adapt("brewing/healthboost"))).isFalse();
  }

  private CraftItemEvent craft() {
    return adapt.post(new CraftItemEvent(player, InventoryKind.WORKBENCH, 0, 0, ClickKind.LEFT,
        InventoryAction.PICKUP_ALL, -1, FakeItem.of("string"), FakeItem.empty(), SNARE,
        CraftItemEvent.RecipeKind.SHAPED, List.of()));
  }

  private void mutate() {
    mutate("swords", "hunter");
  }

  private void mutate(String lead, String partner) {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of(lead, partner)) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill(lead), registry.getSkill(partner), "Gatekeeper"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }
}
