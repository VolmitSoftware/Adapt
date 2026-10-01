package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.volmlib.util.entity.StackExclusion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StealthShadowDecoyAnchorTest extends AdaptTestBase {
  @Test
  void fallbackShowsTheArmorStandWhenNoPacketPlayerIsAvailable() throws Exception {
    StealthShadowDecoy adaptation = new StealthShadowDecoy();
    ArmorStand anchor = mock(ArmorStand.class);
    Player owner = mock(Player.class);
    Method configure = StealthShadowDecoy.class.getDeclaredMethod("configureLegacyVisual", ArmorStand.class, Player.class);
    configure.setAccessible(true);

    configure.invoke(adaptation, anchor, owner);

    verify(anchor).setVisibleByDefault(true);
    verify(anchor).setVisible(true);
    verify(anchor).setInvisible(false);
  }

  @Test
  void anchorRemainsAttackableToMobsWithoutSpawningOnClients() throws Exception {
    StealthShadowDecoy adaptation = new StealthShadowDecoy();
    World world = mock(World.class);
    Player owner = mock(Player.class);
    ArmorStand anchor = mock(ArmorStand.class);
    Location location = new Location(world, 0, 64, 0);
    when(owner.getLocation()).thenReturn(location);
    when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
    when(anchor.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
    when(world.spawn(eq(location), eq(ArmorStand.class), ArgumentMatchers.<Consumer<ArmorStand>>any())).thenAnswer(invocation -> {
      Consumer<ArmorStand> initializer = invocation.getArgument(2);
      initializer.accept(anchor);
      return anchor;
    });
    Method spawn = StealthShadowDecoy.class.getDeclaredMethod("spawnAnchor", Player.class);
    spawn.setAccessible(true);
    try (MockedStatic<StackExclusion> exclusion = mockStatic(StackExclusion.class)) {
      assertThat(spawn.invoke(adaptation, owner)).isSameAs(anchor);
    }

    verify(anchor).setVisible(true);
    verify(anchor).setInvisible(false);
    verify(anchor).setVisibleByDefault(false);
    verify(anchor, never()).setInvisible(true);
    verify(anchor).setMarker(false);
    verify(anchor).setInvulnerable(false);
  }
}
