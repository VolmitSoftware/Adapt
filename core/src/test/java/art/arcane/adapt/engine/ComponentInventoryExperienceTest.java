package art.arcane.adapt.engine;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.engine.component.ComponentSupport;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComponentInventoryExperienceTest {
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

    boolean removed = ComponentSupport.takeAll(inventory, template, 5);

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

    boolean removed = ComponentSupport.takeAll(inventory, template);

    assertThat(removed).isFalse();
    verify(existing, never()).setAmount(anyInt());
    verify(existing, never()).copyWithAmount(anyInt());
    verify(inventory, never()).setItem(anyInt(), any(ItemRef.class));
    verify(inventory, never()).clear(anyInt());
  }

  @Test
  void vanillaExperienceCurveUsesCorrectLevelBands() {
    assertThat(ComponentSupport.getExpToLevel(0)).isEqualTo(7);
    assertThat(ComponentSupport.getExpToLevel(14)).isEqualTo(35);
    assertThat(ComponentSupport.getExpToLevel(15)).isEqualTo(37);
    assertThat(ComponentSupport.getExpToLevel(29)).isEqualTo(107);
    assertThat(ComponentSupport.getExpToLevel(30)).isEqualTo(112);
  }

  @Test
  void recalculateUsesServersConstantTimeExperienceTotal() {
    PlayerRef player = mock(PlayerRef.class);
    when(port.totalExperiencePoints(player)).thenReturn(1_234);

    ComponentSupport.recalcTotalExp(player);

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
        expected += ComponentSupport.getExpToLevel(below);
      }
      expected += Math.round(ComponentSupport.getExpToLevel(level) * 0.5D);

      assertThat(ComponentPort.NONE.totalExperiencePoints(player)).as("level %d", level).isEqualTo((int) expected);
    }
  }

  @Test
  void takeBarExperienceSubtractsOnlyRequestedAmount() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.totalExperience()).thenReturn(100);
    when(player.level()).thenReturn(3);
    when(player.expProgress()).thenReturn(0.5F);

    ComponentSupport.takeExp(player, 3, false);

    verify(port).setTotalExperience(player, 0);
    verify(player).giveExp(97);
    verify(port, never()).awardExperience(any(PlayerRef.class), anyInt());
  }

  @Test
  void earnedExperienceGoesThroughThePlatformExperienceHook() {
    PlayerRef player = mock(PlayerRef.class);

    ComponentSupport.giveExp(player, 12);

    verify(port).awardExperience(player, 12);
    verify(player, never()).giveExp(anyInt());
  }
}
