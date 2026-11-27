package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityOutcome;
import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.port.BukkitAdaptationPorts;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.testkit.TestContent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptationCostFunnelTest extends AdaptTestBase {
  private final AtomicInteger taken = new AtomicInteger();

  @AfterEach
  void tearDown() {
    BukkitAdaptationPorts.uninstall();
  }

  @Test
  void withNoProviderRegisteredEveryCostKindStillTakesTheBuiltInCost() {
    TestAdaptation adaptation = adaptation();
    ItemRef unit = BukkitRefs.item(mock(ItemStack.class));

    assertThat(adaptation.payItemCost(null, "pellet", unit, 1, this::take)).isTrue();
    assertThat(adaptation.payHungerCost(null, "food", 2, this::take)).isTrue();
    assertThat(adaptation.payHealthCost(null, "health", 3, this::take)).isTrue();
    assertThat(adaptation.payDurabilityCost(null, "durability", 4, this::take)).isTrue();
    assertThat(adaptation.payExperienceCost(null, "xp", 5, this::take)).isTrue();
    assertThat(taken.get()).isEqualTo(5);
  }

  @Test
  void aBuiltInCostThePlayerCannotAffordDeniesTheActivation() {
    TestAdaptation adaptation = adaptation();

    assertThat(adaptation.payItemCost(null, "pellet", null, 1, () -> false)).isFalse();
    assertThat(adaptation.payHungerCost(null, "food", 1, () -> false)).isFalse();
    assertThat(adaptation.payHealthCost(null, "health", 1, () -> false)).isFalse();
    assertThat(adaptation.payDurabilityCost(null, "durability", 1, () -> false)).isFalse();
    assertThat(adaptation.payExperienceCost(null, "xp", 1, () -> false)).isFalse();
  }

  @Test
  void anAnchorWithoutABuiltInCostIsAllowed() {
    assertThat(adaptation().payItemCost(null, "pellet", null, 1, null)).isTrue();
  }

  @Test
  void theDeferredItemFunnelTakesTheBuiltInCostAndReportsAllowedDefault() {
    TestAdaptation adaptation = adaptation();

    AbilityCharge allowed = adaptation.payItemCostDeferred(null, "throw", null, 1, this::take);
    AbilityCharge denied = adaptation.payItemCostDeferred(null, "throw", null, 1, () -> false);

    assertThat(allowed.allowed()).isTrue();
    assertThat(allowed.outcome()).isEqualTo(AbilityOutcome.ALLOWED_DEFAULT);
    assertThat(denied.allowed()).isFalse();
    assertThat(denied.outcome()).isEqualTo(AbilityOutcome.DENIED_INSUFFICIENT);
    assertThat(taken.get()).isOne();
  }

  @Test
  void withTheAbilityApiInstalledAndNoProviderRegisteredTheBuiltInCostIsStillTaken() {
    Adapt plugin = mock(Adapt.class);
    when(plugin.getLogger()).thenReturn(Logger.getLogger("AdaptationCostFunnelTest"));

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
      BukkitAdaptationPorts.install(plugin);
      TestAdaptation adaptation = adaptation();

      assertThat(adaptation.payItemCost(null, "pellet", null, 1, this::take)).isTrue();
      assertThat(adaptation.payHealthCost(null, "health", 1, this::take)).isTrue();
      assertThat(adaptation.payItemCost(null, "pellet", null, 1, () -> false)).isFalse();
      assertThat(taken.get()).isEqualTo(2);
    }
  }

  @Test
  void theMigratedDurabilityAnchorStillReachesTheVanillaItemDamagePath() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    PlayerRef ref = BukkitRefs.player(player);
    Adapt plugin = mock(Adapt.class);
    when(plugin.getLogger()).thenReturn(Logger.getLogger("AdaptationCostFunnelTest"));

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
      BukkitAdaptationPorts.install(plugin);
      TestAdaptation adaptation = adaptation();
      adaptation.damageHand(ref, 3);
      adaptation.damageOffHand(ref, 3);
    }

    verify(player).damageItemStack(EquipmentSlot.HAND, 3);
    verify(player).damageItemStack(EquipmentSlot.OFF_HAND, 3);
  }

  @Test
  void aMigratedContentAnchorPassesItsOwnCostKeyAndUnitThrough() {
    TestAdaptation adaptation = adaptation();
    ItemRef unit = BukkitRefs.item(mock(ItemStack.class));

    assertThat(adaptation.payItemCost(null, "pellet", unit, 1, this::take)).isTrue();
    assertThat(AdaptationRuntimeGuards.adaptationRewardKey(adaptation, "pellet"))
        .isEqualTo("adaptation:adapt:test-cost-funnel:pellet");
    assertThat(taken.get()).isOne();
  }

  private static TestAdaptation adaptation() {
    return TestContent.adaptation(Id.adapt("test"), TestAdaptation::new);
  }

  private boolean take() {
    taken.incrementAndGet();
    return true;
  }

  private static final class TestAdaptation extends SimpleAdaptation<AdaptationConfig> {
    private TestAdaptation() {
      super(Id.adapt("test-cost-funnel"));
      registerConfiguration(AdaptationConfig.class);
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }
}
