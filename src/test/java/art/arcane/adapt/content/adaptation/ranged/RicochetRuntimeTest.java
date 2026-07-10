package art.arcane.adapt.content.adaptation.ranged;

import net.kyori.adventure.util.TriState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LingeringPotion;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.SplashPotion;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RicochetRuntimeTest {
  @Test
  void regionHandoffIsOnlyRequiredForForeignFoliaDestinations() {
    assertThat(RicochetTransferRules.requiresRegionHandoff(true, false)).isTrue();
    assertThat(RicochetTransferRules.requiresRegionHandoff(true, true)).isFalse();
    assertThat(RicochetTransferRules.requiresRegionHandoff(false, false)).isFalse();
  }

  @Test
  void resolvesEverySupportedProjectileWithoutCollapsingPotionSubtypes() {
    assertKind(SpectralArrow.class, RicochetProjectileKind.SPECTRAL_ARROW);
    assertKind(Trident.class, RicochetProjectileKind.TRIDENT);
    assertKind(Arrow.class, RicochetProjectileKind.ARROW);
    assertKind(Snowball.class, RicochetProjectileKind.SNOWBALL);
    assertKind(Egg.class, RicochetProjectileKind.EGG);
    assertKind(EnderPearl.class, RicochetProjectileKind.ENDER_PEARL);
    assertKind(LingeringPotion.class, RicochetProjectileKind.LINGERING_POTION);
    assertKind(SplashPotion.class, RicochetProjectileKind.SPLASH_POTION);
    assertKind(ThrownPotion.class, RicochetProjectileKind.THROWN_POTION);
    assertKind(ThrownExpBottle.class, RicochetProjectileKind.EXPERIENCE_BOTTLE);
  }

  @Test
  void emptyPersistentDataAvoidsSerializationAndRestoreWork() throws Exception {
    PersistentDataContainer source = mock(PersistentDataContainer.class);
    PersistentDataContainer target = mock(PersistentDataContainer.class);
    when(source.isEmpty()).thenReturn(true);

    byte[] snapshot = RicochetPersistentData.snapshot(source);
    RicochetPersistentData.restore(target, snapshot);

    assertThat(snapshot).isNull();
    verify(source, never()).serializeToBytes();
    verify(target, never()).readFromBytes(any(), anyBoolean());
  }

  @Test
  void persistentDataUsesPaperByteSerializationAcrossTheHandoff() throws Exception {
    PersistentDataContainer source = mock(PersistentDataContainer.class);
    PersistentDataContainer target = mock(PersistentDataContainer.class);
    byte[] encoded = new byte[]{4, 8, 15, 16, 23, 42};
    when(source.isEmpty()).thenReturn(false);
    when(source.serializeToBytes()).thenReturn(encoded);

    byte[] snapshot = RicochetPersistentData.snapshot(source);
    RicochetPersistentData.restore(target, snapshot);

    assertThat(snapshot).containsExactly(encoded);
    verify(target).readFromBytes(snapshot, true);
  }

  @Test
  void detachedTemplateProtectsMutableFlightAndPersistentState() {
    World world = mock(World.class);
    Player shooter = mock(Player.class);
    ItemStack item = mock(ItemStack.class);
    when(item.clone()).thenReturn(item);
    Location spawn = new Location(world, 8D, 72D, -4D);
    Location hit = new Location(world, 7D, 72D, -4D);
    Vector velocity = new Vector(1.5D, 0.25D, -0.5D);
    Vector reflected = new Vector(-1D, 0D, 0D);
    byte[] persistentData = new byte[]{1, 2, 3};
    RicochetCommonState common = new RicochetCommonState(
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
        0,
        persistentData
    );
    RicochetTransition transition = new RicochetTransition(reflected, 2D, 1, 0.5D);
    RicochetProjectileTemplate template = new RicochetProjectileTemplate(
        RicochetProjectileKind.SNOWBALL,
        world,
        spawn,
        velocity,
        shooter,
        new RicochetProfile(3, 0.1D, 0.5D, 0.01D, 0.4D),
        1,
        3,
        0.5D,
        hit,
        transition,
        common,
        new RicochetThrowablePayload(item)
    );

    spawn.add(100D, 0D, 0D);
    velocity.zero();
    reflected.setX(1D);
    persistentData[0] = 99;
    byte[] firstRead = common.persistentData();
    firstRead[1] = 99;

    assertThat(template.spawnLocation().getX()).isEqualTo(8D);
    assertThat(template.velocity()).isEqualTo(new Vector(1.5D, 0.25D, -0.5D));
    assertThat(template.transition().direction()).isEqualTo(new Vector(-1D, 0D, 0D));
    assertThat(common.persistentData()).containsExactly(1, 2, 3);
  }

  private <T extends Projectile> void assertKind(
      Class<T> projectileClass,
      RicochetProjectileKind expected
  ) {
    Projectile projectile = mock(projectileClass);
    assertThat(RicochetProjectileKind.resolve(projectile)).isEqualTo(expected);
    assertThat(expected.projectileClass()).isEqualTo(projectileClass);
  }
}
