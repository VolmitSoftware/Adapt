package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityOutcome;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptationCostFunnelTest {
  private final AtomicInteger taken = new AtomicInteger();

  @AfterEach
  void tearDown() {
    AbilityApiBridge.uninstall();
  }

  @Test
  void withNoProviderRegisteredEveryCostKindStillTakesTheBuiltInCost() {
    TestAdaptation adaptation = new TestAdaptation();
    ItemStack unit = mock(ItemStack.class);

    assertThat(adaptation.payItemCost(null, "pellet", unit, 1, this::take)).isTrue();
    assertThat(adaptation.payHungerCost(null, "food", 2, this::take)).isTrue();
    assertThat(adaptation.payHealthCost(null, "health", 3, this::take)).isTrue();
    assertThat(adaptation.payDurabilityCost(null, "durability", 4, this::take)).isTrue();
    assertThat(adaptation.payExperienceCost(null, "xp", 5, this::take)).isTrue();
    assertThat(taken.get()).isEqualTo(5);
  }

  @Test
  void aBuiltInCostThePlayerCannotAffordDeniesTheActivation() {
    TestAdaptation adaptation = new TestAdaptation();

    assertThat(adaptation.payItemCost(null, "pellet", null, 1, () -> false)).isFalse();
    assertThat(adaptation.payHungerCost(null, "food", 1, () -> false)).isFalse();
    assertThat(adaptation.payHealthCost(null, "health", 1, () -> false)).isFalse();
    assertThat(adaptation.payDurabilityCost(null, "durability", 1, () -> false)).isFalse();
    assertThat(adaptation.payExperienceCost(null, "xp", 1, () -> false)).isFalse();
  }

  @Test
  void anAnchorWithoutABuiltInCostIsAllowed() {
    assertThat(new TestAdaptation().payItemCost(null, "pellet", null, 1, null)).isTrue();
  }

  @Test
  void theDeferredItemFunnelTakesTheBuiltInCostAndReportsAllowedDefault() {
    TestAdaptation adaptation = new TestAdaptation();

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
    ServicesManager services = mock(ServicesManager.class);
    when(services.getRegistrations(AbilityCostProvider.class)).thenReturn(List.of());
    when(services.getRegistrations(AbilityUsePolicy.class)).thenReturn(List.of());
    Plugin plugin = mock(Plugin.class);
    when(plugin.getLogger()).thenReturn(Logger.getLogger("AdaptationCostFunnelTest"));

    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getServicesManager).thenReturn(services);
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
      AbilityApiBridge.install(plugin);
      TestAdaptation adaptation = new TestAdaptation();

      assertThat(adaptation.payItemCost(null, "pellet", null, 1, this::take)).isTrue();
      assertThat(adaptation.payHealthCost(null, "health", 1, this::take)).isTrue();
      assertThat(adaptation.payItemCost(null, "pellet", null, 1, () -> false)).isFalse();
      assertThat(taken.get()).isEqualTo(2);
    }
  }

  @Test
  void theMigratedDurabilityAnchorStillReachesTheVanillaItemDamagePath() {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    ItemStack held = mock(ItemStack.class);
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.getItemInMainHand()).thenReturn(held);
    when(inventory.getItemInOffHand()).thenReturn(held);
    when(held.getItemMeta()).thenReturn(null);

    TestAdaptation adaptation = new TestAdaptation();
    adaptation.damageHand(player, 3);
    adaptation.damageOffHand(player, 3);

    verify(inventory).getItemInMainHand();
    verify(inventory).getItemInOffHand();
  }

  @Test
  void aMigratedContentAnchorPassesItsOwnCostKeyAndUnitThrough() {
    TestAdaptation adaptation = new TestAdaptation();
    ItemStack unit = mock(ItemStack.class);

    assertThat(adaptation.payItemCost(null, "pellet", unit, 1, this::take)).isTrue();
    assertThat(AdaptationRuntimeGuards.adaptationRewardKey(adaptation, "pellet"))
        .isEqualTo("adaptation:test-cost-funnel:pellet");
    assertThat(taken.get()).isOne();
  }

  private boolean take() {
    taken.incrementAndGet();
    return true;
  }

  private static final class TestAdaptation extends SimpleAdaptation<AdaptationConfig> {
    private TestAdaptation() {
      super("test-cost-funnel");
    }

    @Override
    public void addStats(int level, Element element) {
    }
  }
}
