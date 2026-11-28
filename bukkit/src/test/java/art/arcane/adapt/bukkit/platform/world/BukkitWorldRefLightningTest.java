package art.arcane.adapt.bukkit.platform.world;

import art.arcane.adapt.platform.value.Vec3;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BukkitWorldRefLightningTest {
  @Test
  void aLightningEffectReachesTheBukkitWorldAtThePosition() {
    World world = mock(World.class);
    when(world.getUID()).thenReturn(UUID.randomUUID());
    when(world.getName()).thenReturn("world");
    when(world.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));

    new BukkitWorldRef(world).strikeLightningEffect(new Vec3(2.5D, 61D, -4.5D));

    ArgumentCaptor<Location> at = ArgumentCaptor.forClass(Location.class);
    verify(world).strikeLightningEffect(at.capture());
    assertThat(at.getValue().getWorld()).isSameAs(world);
    assertThat(at.getValue().getX()).isEqualTo(2.5D);
    assertThat(at.getValue().getY()).isEqualTo(61D);
    assertThat(at.getValue().getZ()).isEqualTo(-4.5D);
  }
}
