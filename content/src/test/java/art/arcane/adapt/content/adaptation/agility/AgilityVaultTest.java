package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgilityVaultTest extends AdaptTestBase {
  @Test
  void configReloadAppliesConfiguredProgressionValues() {
    AgilityVault adaptation = TestContent.adaptation(Id.adapt("agility"), AgilityVault::new);

    boolean reloaded = ComponentHost.of(adaptation).reloadConfigSnapshot("""
        costFactor = 0.5
        maxLevel = 4
        """, new File("agility-vault.toml"), false);

    assertThat(reloaded).isTrue();
    assertThat(adaptation.getConfig().costFactor).isEqualTo(0.5D);
    assertThat(adaptation.getConfig().maxLevel).isEqualTo(4);
    assertThat(adaptation.getCostFactor()).isEqualTo(0.5D);
    assertThat(adaptation.getMaxLevel()).isEqualTo(4);
    assertThat(adaptation.getInterval()).isEqualTo(1000L);
    adaptation.unregister();
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
    Vec3 movement = new Vec3(3D, 4D, 0D);
    Vec3 facing = new Vec3(0D, -1D, 5D);

    assertThat(AgilityVault.horizontalDirection(movement, facing)).isEqualTo(new Vec3(1D, 0D, 0D));
    assertThat(AgilityVault.horizontalDirection(Vec3.ZERO, facing)).isEqualTo(new Vec3(0D, 0D, 1D));
    assertThat(AgilityVault.horizontalDirection(new Vec3(Double.NaN, 0D, 0D), Vec3.ZERO)).isEqualTo(Vec3.ZERO);
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
    WorldRef world = mock(WorldRef.class);
    BlockRef stone = mock(BlockRef.class);
    BlockRef fence = mock(BlockRef.class);
    Id oakFence = Id.minecraft("oak_fence");
    when(stone.type()).thenReturn(Id.minecraft("stone"));
    when(stone.isPassable()).thenReturn(false);
    when(fence.type()).thenReturn(oakFence);
    when(world.blockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
      int blockX = invocation.getArgument(0);
      return blockX == 1 ? stone : fence;
    });

    BlockRef result = AgilityVault.findFence(new Loc(world, 0.9D, 64D, 0.1D), new Vec3(1D, 0D, 0D),
        material -> material.equals(oakFence));

    assertThat(result).isNull();
    verify(fence, never()).type();
  }

  @Test
  void delayedCorrectionReachesTheRemainingApexWithoutChangingHorizontalVelocity() {
    double risenHeight = 0.42D;
    Vec3 existing = new Vec3(0.31D, 0.20D, -0.17D);
    Vec3 corrected = AgilityVault.correctedVelocity(existing, risenHeight, 1.75D);

    assertThat(corrected.x()).isCloseTo(existing.x(), within(1.0E-12D));
    assertThat(corrected.z()).isCloseTo(existing.z(), within(1.0E-12D));
    assertThat(risenHeight + AgilityJumpPhysics.heightForStrength(corrected.y()))
        .isCloseTo(1.75D, within(1.0E-9D));
    assertThat(AgilityVault.correctedVerticalVelocity(0.8D, risenHeight, 1.75D)).isEqualTo(0.8D);
  }

  @Test
  void delayedCorrectionSynchronizesAnAlreadyStrongServerJumpWithThePlayer() {
    PlayerRef player = mock(PlayerRef.class);
    Vec3 existing = new Vec3(0.31D, 0.8D, -0.17D);

    AgilityVault.synchronizeCorrectedVelocity(player, existing, 0.42D, 1.75D);

    verify(player).setVelocity(existing);
  }

  @Test
  void movementPreArmAndJumpCorrectionObserveFinalCancellationState() throws ReflectiveOperationException {
    Method movementHandler = AgilityVault.class.getDeclaredMethod("on", PlayerMoveEvent.class);
    Method jumpHandler = AgilityVault.class.getDeclaredMethod("on", PlayerJumpEvent.class);
    Handle movementPolicy = movementHandler.getAnnotation(Handle.class);
    Handle jumpPolicy = jumpHandler.getAnnotation(Handle.class);

    assertThat(movementPolicy).isNotNull();
    assertThat(movementPolicy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(movementPolicy.ignoreCancelled()).isTrue();
    assertThat(jumpPolicy).isNotNull();
    assertThat(jumpPolicy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(jumpPolicy.ignoreCancelled()).isTrue();
  }
}
