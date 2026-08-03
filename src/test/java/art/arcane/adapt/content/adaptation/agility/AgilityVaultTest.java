package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.world.PlayerSkillLine;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgilityVaultTest {
  @Test
  void savedConfigCannotRestoreTheOldFourLevelVault() {
    AgilityVault adaptation = new AgilityVault();
    AgilityVault.Config config = new AgilityVault.Config();
    config.costFactor = 0.5D;
    config.maxLevel = 4;

    adaptation.onConfigReload(null, config);

    assertThat(config.costFactor).isZero();
    assertThat(config.maxLevel).isEqualTo(1);
    assertThat(adaptation.getCostFactor()).isZero();
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
    assertThat(adaptation.getInterval()).isEqualTo(1000L);
  }

  @Test
  void vaultHeightAlwaysClearsAStandardFence() {
    AgilityVault.Config config = new AgilityVault.Config();
    double height = AgilityVault.vaultHeight(config.jumpHeight);
    double strength = AgilityJumpPhysics.strengthForHeight(height);

    assertThat(height).isCloseTo(1.75D, within(1.0E-9D));
    assertThat(height).isGreaterThan(1.5D);
    assertThat(AgilityJumpPhysics.heightForStrength(strength)).isCloseTo(height, within(1.0E-9D));
    assertThat(AgilityVault.vaultHeight(Double.NaN)).isGreaterThan(1.5D);
  }

  @Test
  void movementDirectionWinsAndFacingIsAStationaryFallback() {
    Vector movement = new Vector(3D, 4D, 0D);
    Vector facing = new Vector(0D, -1D, 5D);

    assertThat(AgilityVault.horizontalDirection(movement, facing)).isEqualTo(new Vector(1D, 0D, 0D));
    assertThat(AgilityVault.horizontalDirection(new Vector(), facing)).isEqualTo(new Vector(0D, 0D, 1D));
    assertThat(AgilityVault.horizontalDirection(new Vector(Double.NaN, 0D, 0D), new Vector())).isEqualTo(new Vector());
  }

  @Test
  void groundedActivePlayersPreArmOnlyWhenFacingAnActualFence() {
    assertThat(AgilityVault.shouldPreArm(1, true, true, true)).isTrue();
    assertThat(AgilityVault.shouldPreArm(0, true, true, true)).isFalse();
    assertThat(AgilityVault.shouldPreArm(1, false, true, true)).isFalse();
    assertThat(AgilityVault.shouldPreArm(1, true, false, true)).isFalse();
    assertThat(AgilityVault.shouldPreArm(1, true, true, false)).isFalse();
  }

  @Test
  void fullBlockBeforeFenceStopsTheFenceProbe() {
    World world = mock(World.class);
    Block stone = mock(Block.class);
    Block fence = mock(Block.class);
    when(stone.getType()).thenReturn(Material.STONE);
    when(stone.isPassable()).thenReturn(false);
    when(fence.getType()).thenReturn(Material.OAK_FENCE);
    when(world.getBlockAt(any(Location.class))).thenAnswer(invocation -> {
      Location probe = invocation.getArgument(0);
      return probe.getBlockX() == 1 ? stone : fence;
    });

    Block result = AgilityVault.findFence(new Location(world, 0.9D, 64D, 0.1D), new Vector(1D, 0D, 0D),
        material -> material == Material.OAK_FENCE);

    assertThat(result).isNull();
    verify(fence, never()).getType();
  }

  @Test
  void delayedCorrectionReachesTheRemainingApexWithoutChangingHorizontalVelocity() {
    double risenHeight = 0.42D;
    Vector existing = new Vector(0.31D, 0.20D, -0.17D);
    Vector corrected = AgilityVault.correctedVelocity(existing, risenHeight, 1.75D);

    assertThat(corrected.getX()).isCloseTo(existing.getX(), within(1.0E-12D));
    assertThat(corrected.getZ()).isCloseTo(existing.getZ(), within(1.0E-12D));
    assertThat(risenHeight + AgilityJumpPhysics.heightForStrength(corrected.getY()))
        .isCloseTo(1.75D, within(1.0E-9D));
    assertThat(AgilityVault.correctedVerticalVelocity(0.8D, risenHeight, 1.75D)).isEqualTo(0.8D);
  }

  @Test
  void delayedCorrectionSynchronizesAnAlreadyStrongServerJumpWithThePlayer() {
    Player player = mock(Player.class);
    Vector existing = new Vector(0.31D, 0.8D, -0.17D);

    AgilityVault.synchronizeCorrectedVelocity(player, existing, 0.42D, 1.75D);

    verify(player).setVelocity(existing);
  }

  @Test
  void legacyStoredLevelsAreClampedThroughPlayerSkillLine() {
    AgilityVault adaptation = new AgilityVault();
    PlayerSkillLine legacy = mock(PlayerSkillLine.class);
    PlayerSkillLine current = mock(PlayerSkillLine.class);
    when(legacy.getAdaptationLevel(adaptation.getName())).thenReturn(4);
    when(current.getAdaptationLevel(adaptation.getName())).thenReturn(1);

    assertThat(adaptation.normalizeStoredLevel(legacy)).isTrue();
    assertThat(adaptation.normalizeStoredLevel(current)).isFalse();

    verify(legacy).setAdaptation(adaptation, 1);
    verify(current, never()).setAdaptation(adaptation, 1);
  }

  @Test
  void movementPreArmAndJumpCorrectionObserveFinalCancellationState() throws ReflectiveOperationException {
    Method movementHandler = AgilityVault.class.getDeclaredMethod("on", PlayerMoveEvent.class);
    Method jumpHandler = Class.forName("art.arcane.adapt.content.adaptation.agility.AgilityVault$JumpListener")
        .getDeclaredMethod("on", PlayerJumpEvent.class);
    EventHandler movementPolicy = movementHandler.getAnnotation(EventHandler.class);
    EventHandler jumpPolicy = jumpHandler.getAnnotation(EventHandler.class);

    assertThat(movementPolicy).isNotNull();
    assertThat(movementPolicy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(movementPolicy.ignoreCancelled()).isTrue();
    assertThat(jumpPolicy).isNotNull();
    assertThat(jumpPolicy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(jumpPolicy.ignoreCancelled()).isTrue();
  }
}
