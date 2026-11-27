package art.arcane.adapt.content.adaptation.discovery.upgraded;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryArmor;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryBetterMending;
import art.arcane.adapt.content.adaptation.discovery.WorldArmor;
import art.arcane.adapt.content.skill.SkillDiscovery;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.ComponentPort;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unity Prime runs World Armor and Better Mending at their top levels, and the kept originals stand down meanwhile. */
class UnityPrimeOriginalsTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private SkillRegistry registry;
  private FakePlayer player;
  private AdaptPlayer profile;
  private UnityPrime unity;
  private DiscoveryArmor armor;
  private DiscoveryBetterMending mending;
  private ComponentPort experience;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    platform.items().hardness("stone", 1.5F, 6F);
    player = platform.player("Seeker", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillDiscovery.class, SkillSwords.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    unity = find(SkillHost.of(registry.getSkill("discovery")).getUpgrades(), UnityPrime.class);
    armor = find(registry.getSkill("discovery").getAdaptations(), DiscoveryArmor.class);
    mending = find(registry.getSkill("discovery").getAdaptations(), DiscoveryBetterMending.class);
  }

  @AfterEach
  void stop() {
    if (experience != null) {
      AdaptPorts.uninstall(ComponentPort.class);
    }
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theOriginalsStandDownOnlyWhileUnityPrimeIsHeld() {
    assertThat(UnityPrime.covers(armor, player)).isFalse();
    mutate();
    assertThat(armor.getLevel(player)).isEqualTo(1);
    assertThat(UnityPrime.covers(armor, player)).isFalse();

    profile.getData().getSkillLine("discovery").setAdaptation(unity, 1);

    assertThat(UnityPrime.covers(armor, player)).isTrue();
    assertThat(UnityPrime.covers(mending, player)).isTrue();
  }

  @Test
  void theOriginalsKeepWorkingWhileUnityPrimeIsSwitchedOff() {
    mutate();
    profile.getData().getSkillLine("discovery").setAdaptation(unity, 1);
    profile.getData().getPreferences().set(unity.getId().toString(), CommonPreferences.ENABLED.id(),
        CommonPreferences.Toggle.OFF.name());

    assertThat(UnityPrime.covers(armor, player)).isFalse();
    assertThat(UnityPrime.covers(mending, player)).isFalse();
  }

  @Test
  void unityPrimeAppliesWorldArmorAtItsTopLevel() {
    stoneAround();
    mutate();
    profile.getData().getSkillLine("discovery").setAdaptation(unity, 1);
    double base = player.attributeValue(Attributes.ARMOR);

    unity.boostOwned(player);

    double expected = 0.3D * new WorldArmor(armor, new HashMap<>()).armorAt(player.location(), armor.getMaxLevel());
    assertThat(expected).isPositive();
    assertThat(player.attributeValue(Attributes.ARMOR) - base).isCloseTo(expected, within(1.0E-6D));
  }

  @Test
  void noWorldArmorWithoutTheMutation() {
    stoneAround();
    profile.getData().getSkillLine("discovery").setAdaptation(unity, 1);
    double base = player.attributeValue(Attributes.ARMOR);

    unity.boostOwned(player);

    assertThat(player.attributeValue(Attributes.ARMOR)).isEqualTo(base);
  }

  @Test
  void unityPrimeMendsAtBetterMendingsTopLevelOnce() {
    mutate();
    profile.getData().getSkillLine("discovery").setAdaptation(unity, 1);
    installExperience(25);
    FakeItem pick = FakeItem.tool("iron_pickaxe", 250);
    pick.enchant("mending", 1);
    pick.setDamage(100);
    player.holding(pick);
    player.setSneaking(true);

    PlayerInteractEvent press = press();

    ItemRef held = player.inventory().mainHand();
    assertThat(press.isCancelled()).isTrue();
    assertThat(held.damage()).isZero();
    verify(experience, times(1)).setExperiencePoints(player, 25 - 17);
  }

  @Test
  void withoutUnityPrimeTheKeptBetterMendingStillWorksAtLevelOne() {
    mutate();
    installExperience(25);
    FakeItem pick = FakeItem.tool("iron_pickaxe", 250);
    pick.enchant("mending", 1);
    pick.setDamage(100);
    player.holding(pick);
    player.setSneaking(true);

    press();

    assertThat(player.inventory().mainHand().damage()).isLessThan(100).isGreaterThan(0);
  }

  @Test
  void anUnmutatedPlayerGetsNoMendingFromUnityPrime() {
    profile.getData().getSkillLine("discovery").setAdaptation(unity, 1);
    installExperience(25);
    FakeItem pick = FakeItem.tool("iron_pickaxe", 250);
    pick.enchant("mending", 1);
    pick.setDamage(100);
    player.holding(pick);
    player.setSneaking(true);

    press();

    assertThat(player.inventory().mainHand().damage()).isEqualTo(100);
    verify(experience, never()).setExperiencePoints(any(), anyInt());
  }

  private PlayerInteractEvent press() {
    return adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private void installExperience(int points) {
    experience = mock(ComponentPort.class);
    when(experience.totalExperiencePoints(player)).thenReturn(points);
    AdaptPorts.install(ComponentPort.class, experience);
  }

  private void stoneAround() {
    for (int x = -3; x <= 3; x++) {
      for (int z = -3; z <= 3; z++) {
        platform.world().set(x, 60, z, "stone");
      }
    }
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("discovery", "swords")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("discovery"), registry.getSkill("swords"), "Seeker"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private static <A> A find(List<Adaptation<?>> adaptations, Class<A> type) {
    return adaptations.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
  }
}
