package art.arcane.adapt.bukkit.platform.entity;

import net.kyori.adventure.util.TriState;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.SplashPotion;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BukkitProjectileSnapshotTest {
  @Test
  void resolvesEverySupportedProjectileWithoutCollapsingPotionSubtypes() {
    assertKind(SpectralArrow.class, BukkitProjectileSnapshot.Kind.SPECTRAL_ARROW);
    assertKind(Trident.class, BukkitProjectileSnapshot.Kind.TRIDENT);
    assertKind(Arrow.class, BukkitProjectileSnapshot.Kind.ARROW);
    assertKind(Snowball.class, BukkitProjectileSnapshot.Kind.SNOWBALL);
    assertKind(Egg.class, BukkitProjectileSnapshot.Kind.EGG);
    assertKind(EnderPearl.class, BukkitProjectileSnapshot.Kind.ENDER_PEARL);
    assertKind(LingeringPotion.class, BukkitProjectileSnapshot.Kind.LINGERING_POTION);
    assertKind(SplashPotion.class, BukkitProjectileSnapshot.Kind.SPLASH_POTION);
    assertKind(ThrownPotion.class, BukkitProjectileSnapshot.Kind.THROWN_POTION);
    assertKind(ThrownExpBottle.class, BukkitProjectileSnapshot.Kind.EXPERIENCE_BOTTLE);
  }

  @Test
  void emptyPersistentDataAvoidsSnapshotAndRestoreWork() {
    PersistentDataContainer source = mock(PersistentDataContainer.class);
    PersistentDataContainer target = mock(PersistentDataContainer.class);
    when(source.isEmpty()).thenReturn(true);

    PersistentDataContainer snapshot = BukkitProjectileSnapshot.PersistentData.snapshot(source);
    BukkitProjectileSnapshot.PersistentData.restore(target, snapshot);

    assertThat(snapshot).isNull();
    verify(source, never()).copyTo(any(), anyBoolean());
    verifyNoInteractions(target);
  }

  @Test
  void persistentDataCopiesThroughDetachedContainerAcrossTheHandoff() {
    PersistentDataContainer source = mock(PersistentDataContainer.class);
    PersistentDataContainer detached = mock(PersistentDataContainer.class);
    PersistentDataContainer target = mock(PersistentDataContainer.class);
    PersistentDataAdapterContext context = mock(PersistentDataAdapterContext.class);
    when(source.isEmpty()).thenReturn(false);
    when(source.getAdapterContext()).thenReturn(context);
    when(context.newPersistentDataContainer()).thenReturn(detached);

    PersistentDataContainer snapshot = BukkitProjectileSnapshot.PersistentData.snapshot(source);
    BukkitProjectileSnapshot.PersistentData.restore(target, snapshot);

    assertThat(snapshot).isSameAs(detached);
    verify(source).copyTo(detached, true);
    verify(detached).copyTo(target, true);
  }

  @Test
  void detachedTemplateProtectsMutableFlightAndPersistentState() {
    ItemStack item = mock(ItemStack.class);
    when(item.clone()).thenReturn(item);
    PersistentDataContainer persistentData = mock(PersistentDataContainer.class);
    BukkitProjectileSnapshot.CommonState common = new BukkitProjectileSnapshot.CommonState(
        true,
        10,
        true,
        true,
        false,
        20,
        0F,
        TriState.NOT_SET,
        0,
        false,
        false,
        false,
        false,
        false,
        0,
        null,
        false,
        true,
        Set.of("ricochet"),
        persistentData
    );
    BukkitProjectileSnapshot.ThrowablePayload payload = new BukkitProjectileSnapshot.ThrowablePayload(item);

    assertThat(common.persistentData()).isSameAs(persistentData);
    assertThat(common.scoreboardTags()).containsExactly("ricochet");
    assertThat(payload.item()).isSameAs(item);
  }

  private <T extends Projectile> void assertKind(
      Class<T> projectileClass,
      BukkitProjectileSnapshot.Kind expected
  ) {
    Projectile projectile = mock(projectileClass);
    assertThat(BukkitProjectileSnapshot.Kind.resolve(projectile)).isEqualTo(expected);
    assertThat(expected.projectileClass()).isEqualTo(projectileClass);
  }
}
