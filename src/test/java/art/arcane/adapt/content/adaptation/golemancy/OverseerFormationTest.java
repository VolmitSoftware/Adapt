package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemHandle;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class OverseerFormationTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000701");
  private static final UUID OTHER_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000702");
  private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000711");
  private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000712");

  private final World world = mock(World.class);
  private final AtomicReference<Double> radius = new AtomicReference<>(2.5D);
  private final OverseerFormation formation = new OverseerFormation(radius::get);

  @Test
  void membersStandOnTheirRingSlotAroundTheOwner() {
    formation.update(OWNER, List.of(SECOND, FIRST));

    assertSlot(formation.slot(handle(OWNER, FIRST), owner()), 12.5D, 20D);
    assertSlot(formation.slot(handle(OWNER, SECOND), owner()), 7.5D, 20D);
  }

  @Test
  void golemsOutsideTheRingAndOwnersWithoutOneGetNoSlot() {
    formation.update(OWNER, List.of(FIRST));

    assertThat(formation.slot(handle(OWNER, SECOND), owner())).isNull();
    assertThat(formation.slot(handle(OTHER_OWNER, FIRST), owner())).isNull();
  }

  @Test
  void theRingRadiusComesFromTheConfig() {
    formation.update(OWNER, List.of(FIRST));
    radius.set(4D);

    assertSlot(formation.slot(handle(OWNER, FIRST), owner()), 14D, 20D);
  }

  @Test
  void forgettingOrNotRetainingAnOwnerDropsTheirRing() {
    formation.update(OWNER, List.of(FIRST));
    formation.update(OTHER_OWNER, List.of(SECOND));

    formation.forget(OWNER);
    formation.retain(Set.of(OWNER));

    assertThat(formation.slot(handle(OWNER, FIRST), owner())).isNull();
    assertThat(formation.slot(handle(OTHER_OWNER, SECOND), owner())).isNull();
  }

  @Test
  void anEmptyMembershipClearsTheRing() {
    formation.update(OWNER, List.of(FIRST));

    formation.update(OWNER, List.of());

    assertThat(formation.slot(handle(OWNER, FIRST), owner())).isNull();
  }

  private Location owner() {
    return new Location(world, 10D, 64D, 20D);
  }

  private static GolemHandle handle(UUID owner, UUID id) {
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.owner()).thenReturn(owner);
    lenient().when(handle.id()).thenReturn(id);
    return handle;
  }

  private void assertSlot(Location slot, double x, double z) {
    assertThat(slot).isNotNull();
    assertThat(slot.getWorld()).isSameAs(world);
    assertThat(slot.getX()).isCloseTo(x, within(1.0E-9));
    assertThat(slot.getY()).isEqualTo(64D);
    assertThat(slot.getZ()).isCloseTo(z, within(1.0E-9));
  }
}
