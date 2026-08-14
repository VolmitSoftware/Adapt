package art.arcane.adapt.content.adaptation.discovery;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscoveryXpResistTest {
  @Test
  void spendsVanillaLevelsWhenResistanceTriggers() {
    Player player = mock(Player.class);
    when(player.getLevel()).thenReturn(5);
    when(player.getExp()).thenReturn(0.4F);

    assertThat(DiscoveryXpResist.spendLevels(player, 3)).isTrue();
    InOrder changes = inOrder(player);
    changes.verify(player).giveExpLevels(-3);
    changes.verify(player).sendExperienceChange(0.4F, 2);
  }

  @Test
  void refusesMissingOrInvalidLevelCost() {
    Player player = mock(Player.class);
    when(player.getLevel()).thenReturn(2);

    assertThat(DiscoveryXpResist.spendLevels(player, 3)).isFalse();
    assertThat(DiscoveryXpResist.spendLevels(player, 0)).isFalse();
    assertThat(DiscoveryXpResist.spendLevels(null, 1)).isFalse();
    verify(player, never()).giveExpLevels(anyInt());
  }

  @Test
  void clampsCorruptProgressBeforeTheImmediateHudUpdate() {
    Player player = mock(Player.class);
    when(player.getLevel()).thenReturn(4);
    when(player.getExp()).thenReturn(1.5F);

    assertThat(DiscoveryXpResist.spendLevels(player, 1)).isTrue();
    verify(player).giveExpLevels(-1);
    verify(player).sendExperienceChange(1.0F, 3);
  }

  @Test
  void defaultCostReceiptOnlyReportsAnActualVanillaCharge() {
    Player player = mock(Player.class);
    when(player.getLevel()).thenReturn(6);
    when(player.getExp()).thenReturn(0.25F);
    DiscoveryXpResist.ExperienceLevelCharge charge =
        new DiscoveryXpResist.ExperienceLevelCharge(player, 2);

    assertThat(charge.wasTaken()).isFalse();
    assertThat(charge.take()).isTrue();
    assertThat(charge.take()).isTrue();
    assertThat(charge.wasTaken()).isTrue();
    verify(player).giveExpLevels(-2);
    verify(player).sendExperienceChange(0.25F, 4);
  }

  @Test
  void damageHandlerIgnoresCancelledTransactions() throws ReflectiveOperationException {
    Method handler = DiscoveryXpResist.class.getDeclaredMethod("on", EntityDamageEvent.class);
    EventHandler annotation = handler.getAnnotation(EventHandler.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }

  @Test
  void criticalPredictionUsesFinalDamageWithoutSubtractingAbsorptionTwice() {
    assertThat(DiscoveryXpResist.isCriticalHealthDamage(20D, 6D, 10D)).isFalse();
    assertThat(DiscoveryXpResist.isCriticalHealthDamage(20D, 10D, 10D)).isTrue();
    assertThat(DiscoveryXpResist.isCriticalHealthDamage(4D, 0D, 10D)).isFalse();
    assertThat(DiscoveryXpResist.isCriticalHealthDamage(4D, 1D, 10D)).isTrue();
    assertThat(DiscoveryXpResist.isCriticalHealthDamage(20D, Double.NaN, 10D)).isFalse();
  }
}
