package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiftEnderTaglockPearlDiscardTest extends AdaptTestBase {
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");

  @Test
  void aTaglockedHitDiscardsThePearlSoNoPlatformTeleportsTheThrower() {
    ProjectileRef pearl = pearl(UUID.randomUUID().toString());

    new RiftEnderTaglock().on(new ProjectileHitEvent(pearl, mock(LivingRef.class), null, null));

    verify(pearl).remove();
  }

  @Test
  void aCancelledBlockHitStillDiscardsThePearlBecauseVanillaResolvesItsImpact() {
    ProjectileRef pearl = pearl(UUID.randomUUID().toString());
    ProjectileHitEvent hit = new ProjectileHitEvent(pearl, null, mock(BlockRef.class), BlockFace.UP);
    hit.setCancelled(true);

    new RiftEnderTaglock().on(hit);

    verify(pearl).remove();
  }

  @Test
  void aCancelledEntityHitLetsThePearlFlyOn() {
    ProjectileRef pearl = pearl(UUID.randomUUID().toString());
    ProjectileHitEvent hit = new ProjectileHitEvent(pearl, mock(LivingRef.class), null, null);
    hit.setCancelled(true);

    new RiftEnderTaglock().on(hit);

    verify(pearl, never()).remove();
  }

  @Test
  void aPlainPearlKeepsItsVanillaImpact() {
    ProjectileRef pearl = pearl(null);

    new RiftEnderTaglock().on(new ProjectileHitEvent(pearl, mock(LivingRef.class), null, null));

    verify(pearl, never()).remove();
  }

  @SuppressWarnings("unchecked")
  private static ProjectileRef pearl(String taggedTarget) {
    ProjectileRef pearl = mock(ProjectileRef.class);
    DataHolder data = mock(DataHolder.class);
    when(pearl.type()).thenReturn(ENDER_PEARL);
    PlayerRef thrower = mock(PlayerRef.class);
    when(thrower.uuid()).thenReturn(UUID.randomUUID());
    when(pearl.shooter()).thenReturn(thrower);
    when(pearl.isValid()).thenReturn(true);
    when(pearl.data()).thenReturn(data);
    when(pearl.location()).thenReturn(new Loc(mock(WorldRef.class), 4D, 100D, 0D));
    when(data.get(any(DataKey.class))).thenReturn(taggedTarget);
    return pearl;
  }
}
