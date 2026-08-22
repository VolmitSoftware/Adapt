package art.arcane.adapt.util.common.compat;

import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaperCompatTest {
  @Test
  void hasChangedBlockIsFalseForFractionalMoveWithinSameBlock() {
    Location from = new Location(null, 10.2D, 64.0D, -3.9D);
    Location to = new Location(null, 10.8D, 64.9D, -3.1D);

    assertThat(PaperCompat.hasChangedBlock(from, to)).isFalse();
  }

  @Test
  void hasChangedBlockDetectsCrossingEachAxis() {
    Location from = new Location(null, 10.5D, 64.5D, -3.5D);

    assertThat(PaperCompat.hasChangedBlock(from, new Location(null, 11.1D, 64.5D, -3.5D))).isTrue();
    assertThat(PaperCompat.hasChangedBlock(from, new Location(null, 10.5D, 63.9D, -3.5D))).isTrue();
    assertThat(PaperCompat.hasChangedBlock(from, new Location(null, 10.5D, 64.5D, -2.9D))).isTrue();
  }

  @Test
  void hasChangedBlockFloorsNegativeCoordinates() {
    Location from = new Location(null, -0.4D, 64.0D, 0.0D);
    Location to = new Location(null, 0.4D, 64.0D, 0.0D);

    assertThat(PaperCompat.hasChangedBlock(from, to)).isTrue();
  }

  @Test
  void hasChangedBlockIsFalseForNullDestination() {
    Location from = new Location(null, 1.0D, 2.0D, 3.0D);

    assertThat(PaperCompat.hasChangedBlock(from, null)).isFalse();
  }

  @Test
  void hasClassReportsPresentAndMissingClasses() {
    assertThat(PaperCompat.hasClass("org.bukkit.Location")).isTrue();
    assertThat(PaperCompat.hasClass("io.papermc.paper.event.player.DoesNotExistEvent")).isFalse();
    // cached second lookup stays stable
    assertThat(PaperCompat.hasClass("io.papermc.paper.event.player.DoesNotExistEvent")).isFalse();
  }

  @Test
  void isReplaceableReportsWhatAPlacementWouldOverwrite() {
    Block grass = mock(Block.class);
    Block stone = mock(Block.class);
    when(grass.isReplaceable()).thenReturn(true);
    when(stone.isReplaceable()).thenReturn(false);

    assertThat(PaperCompat.isReplaceable(grass)).isTrue();
    assertThat(PaperCompat.isReplaceable(stone)).isFalse();
    assertThat(PaperCompat.isReplaceable(null)).isFalse();
  }

  @Test
  void tamedOwnerIdResolvesOwnerUuidWithoutPaperApi() {
    UUID ownerId = UUID.randomUUID();
    AnimalTamer owner = mock(AnimalTamer.class);
    when(owner.getUniqueId()).thenReturn(ownerId);
    Tameable tamed = mock(Tameable.class);
    when(tamed.getOwner()).thenReturn(owner);

    assertThat(PaperCompat.tamedOwnerId(tamed)).isEqualTo(ownerId);
  }

  @Test
  void tamedOwnerIdIsNullForOwnerlessEntity() {
    Tameable tamed = mock(Tameable.class);
    when(tamed.getOwner()).thenReturn(null);

    assertThat(PaperCompat.tamedOwnerId(tamed)).isNull();
  }

  @Test
  void nearbyEntityQueryStopsBeforeWorldAccessWhenFoliaDoesNotOwnTheFootprint() {
    World world = mock(World.class);
    Location center = new Location(world, 15.5D, 64.0D, 15.5D);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(center, 8.0D, 8.0D)).thenReturn(false);

      assertThat(PaperCompat.nearbyEntitiesByType(Player.class, center, 8.0D)).isEmpty();
    }

    verifyNoInteractions(world);
  }
}
