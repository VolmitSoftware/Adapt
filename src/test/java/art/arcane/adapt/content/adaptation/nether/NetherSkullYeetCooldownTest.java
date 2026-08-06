package art.arcane.adapt.content.adaptation.nether;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetherSkullYeetCooldownTest {
  @Test
  void cooldownScalesDownPerLevelFromDefaults() {
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 1)).isEqualTo(10);
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 2)).isEqualTo(5);
  }

  @Test
  void cooldownNeverDropsBelowOneSecondAtOrBeyondMaxLevel() {
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 3)).isEqualTo(1);
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 4)).isEqualTo(1);
    assertThat(NetherSkullYeet.cooldownSeconds(15, 5, 100)).isEqualTo(1);
  }

  @Test
  void longBombDistanceRequiresTheSameWorld() {
    World sourceWorld = mock(World.class);
    World targetWorld = mock(World.class);
    Location source = mock(Location.class);
    Location target = mock(Location.class);
    when(source.getWorld()).thenReturn(sourceWorld);
    when(target.getWorld()).thenReturn(targetWorld);

    assertThat(NetherSkullYeet.isLongBomb(source, target, 40D)).isFalse();
    verify(source, never()).distanceSquared(target);

    when(target.getWorld()).thenReturn(sourceWorld);
    when(source.distanceSquared(target)).thenReturn(1600D);
    assertThat(NetherSkullYeet.isLongBomb(source, target, 40D)).isTrue();
  }

  @Test
  void itemCooldownIsAppliedOnlyAfterCostSettlement() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/content/adaptation/nether/NetherSkullYeet.java"));
    int charge = source.indexOf("payItemCost(p, \"skull\"");
    int cooldown = source.indexOf("cooldowns.mark(p, cooldownMillis)", charge);

    assertThat(charge).isGreaterThanOrEqualTo(0);
    assertThat(cooldown).isGreaterThan(charge);
  }

  @Test
  void theSweepAndTheGateReadOneCooldownStateInsteadOfTwo() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/content/adaptation/nether/NetherSkullYeet.java"));

    assertThat(source).contains("ItemCooldowns.forMaterial(Material.WITHER_SKELETON_SKULL)");
    assertThat(source).doesNotContain("p.setCooldown(Material.WITHER_SKELETON_SKULL");
    assertThat(source).doesNotContain("p.hasCooldown(");
  }
}
