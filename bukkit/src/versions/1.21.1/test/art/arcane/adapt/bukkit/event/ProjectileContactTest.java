package art.arcane.adapt.bukkit.event;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Before 1.21.2 a projectile posts its block hit from where its tick began; handlers see it at the contact point. */
class ProjectileContactTest {
  private final World world = mock(World.class);
  private final Projectile projectile = mock(Projectile.class);
  private final AtomicReference<Location> position = new AtomicReference<>(new Location(world, 0, 100, 0));

  ProjectileContactTest() {
    when(projectile.getLocation()).thenAnswer(invocation -> position.get().clone());
    when(projectile.getVelocity()).thenReturn(new Vector(2, 0, 0));
    when(projectile.isValid()).thenReturn(true);
    when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true)))
        .thenReturn(new RayTraceResult(new Vector(1.2, 100, 0), mock(Block.class), BlockFace.WEST));
  }

  @Test
  void blockHitsPostAtTheContactPointAndGoBackAfterwards() {
    AtomicReference<Location> seen = new AtomicReference<>();
    ProjectileContact.post(blockHit(), () -> seen.set(projectile.getLocation()), this::place);

    assertThat(seen.get().getX()).isEqualTo(1.2);
    assertThat(position.get().getX()).isZero();
  }

  @Test
  void aHandlerThatMovesTheProjectileKeepsItsPosition() {
    ProjectileContact.post(blockHit(), () -> position.set(new Location(world, 5, 100, 5)), this::place);

    assertThat(position.get().getX()).isEqualTo(5);
  }

  @Test
  void entityHitsPostWhereTheProjectileIs() {
    ProjectileHitEvent event = mock(ProjectileHitEvent.class);
    when(event.getEntity()).thenReturn(projectile);
    AtomicReference<Location> seen = new AtomicReference<>();

    AtomicReference<Integer> placements = new AtomicReference<>(0);
    ProjectileContact.post(event, () -> seen.set(projectile.getLocation()), (moved, at) -> {
      placements.set(placements.get() + 1);
      return place(moved, at);
    });

    assertThat(seen.get().getX()).isZero();
    assertThat(placements.get()).isZero();
  }

  private boolean place(Projectile moved, Vector at) {
    position.set(at.toLocation(world));
    return true;
  }

  private ProjectileHitEvent blockHit() {
    ProjectileHitEvent event = mock(ProjectileHitEvent.class);
    when(event.getEntity()).thenReturn(projectile);
    when(event.getHitBlock()).thenReturn(mock(Block.class));
    return event;
  }
}
