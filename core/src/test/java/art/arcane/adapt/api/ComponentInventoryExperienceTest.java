package art.arcane.adapt.api;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComponentInventoryExperienceTest {
  private final Component component = mock(Component.class, CALLS_REAL_METHODS);
  private ComponentPort port;

  @BeforeEach
  void installPort() {
    port = mock(ComponentPort.class);
    AdaptPorts.install(ComponentPort.class, port);
  }

  @AfterEach
  void uninstallPort() {
    AdaptPorts.uninstall(ComponentPort.class);
  }

  @Test
  void takeAllHonorsExplicitAmountAcrossStacks() {
    InventoryRef inventory = mock(InventoryRef.class);
    ItemRef template = mock(ItemRef.class);
    ItemRef requested = mock(ItemRef.class);
    ItemRef first = mock(ItemRef.class);
    ItemRef second = mock(ItemRef.class);
    ItemRef reduced = mock(ItemRef.class);
    when(template.copyWithAmount(5)).thenReturn(requested);
    when(requested.amount()).thenReturn(5);
    when(first.isSimilar(requested)).thenReturn(true);
    when(second.isSimilar(requested)).thenReturn(true);
    when(first.amount()).thenReturn(3);
    when(second.amount()).thenReturn(4);
    when(second.copyWithAmount(2)).thenReturn(reduced);
    when(inventory.size()).thenReturn(2);
    when(inventory.item(0)).thenReturn(first);
    when(inventory.item(1)).thenReturn(second);

    boolean removed = component.takeAll(inventory, template, 5);

    assertThat(removed).isTrue();
    verify(inventory).clear(0);
    verify(inventory).setItem(1, reduced);
    verify(template).copyWithAmount(5);
    verify(second).copyWithAmount(2);
  }

  @Test
  void insufficientInventoryIsNotMutated() {
    InventoryRef inventory = mock(InventoryRef.class);
    ItemRef existing = mock(ItemRef.class);
    ItemRef template = mock(ItemRef.class);
    when(template.amount()).thenReturn(3);
    when(existing.isSimilar(template)).thenReturn(true);
    when(existing.amount()).thenReturn(2);
    when(inventory.size()).thenReturn(1);
    when(inventory.item(0)).thenReturn(existing);

    boolean removed = component.takeAll(inventory, template);

    assertThat(removed).isFalse();
    verify(existing, never()).setAmount(anyInt());
    verify(existing, never()).copyWithAmount(anyInt());
    verify(inventory, never()).setItem(anyInt(), any(ItemRef.class));
    verify(inventory, never()).clear(anyInt());
  }

  @Test
  void vanillaExperienceCurveUsesCorrectLevelBands() {
    assertThat(component.getExpToLevel(0)).isEqualTo(7);
    assertThat(component.getExpToLevel(14)).isEqualTo(35);
    assertThat(component.getExpToLevel(15)).isEqualTo(37);
    assertThat(component.getExpToLevel(29)).isEqualTo(107);
    assertThat(component.getExpToLevel(30)).isEqualTo(112);
  }

  @Test
  void recalculateUsesServersConstantTimeExperienceTotal() {
    PlayerRef player = mock(PlayerRef.class);
    when(port.totalExperiencePoints(player)).thenReturn(1_234);

    component.recalcTotalExp(player);

    verify(port).setTotalExperience(player, 1_234);
    verify(player, never()).level();
  }

  @Test
  void neutralExperienceTotalMatchesTheSummedLevelCurve() {
    for (int level = 0; level <= 60; level++) {
      PlayerRef player = mock(PlayerRef.class);
      when(player.level()).thenReturn(level);
      when(player.expProgress()).thenReturn(0.5F);
      long expected = 0L;
      for (int below = 0; below < level; below++) {
        expected += component.getExpToLevel(below);
      }
      expected += Math.round(component.getExpToLevel(level) * 0.5D);

      assertThat(ComponentPort.NONE.totalExperiencePoints(player)).as("level %d", level).isEqualTo((int) expected);
    }
  }

  @Test
  void takeBarExperienceSubtractsOnlyRequestedAmount() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.totalExperience()).thenReturn(100);
    when(player.level()).thenReturn(3);
    when(player.expProgress()).thenReturn(0.5F);

    component.takeExp(player, 3, false);

    verify(port).setTotalExperience(player, 0);
    verify(player).giveExp(97);
    verify(port, never()).awardExperience(any(PlayerRef.class), anyInt());
  }

  @Test
  void earnedExperienceGoesThroughThePlatformExperienceHook() {
    PlayerRef player = mock(PlayerRef.class);

    component.giveExp(player, 12);

    verify(port).awardExperience(player, 12);
    verify(player, never()).giveExp(anyInt());
  }
}
