package art.arcane.adapt.util.common.compat;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Tameable;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
}
