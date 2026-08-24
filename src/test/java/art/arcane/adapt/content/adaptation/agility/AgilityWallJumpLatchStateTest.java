package art.arcane.adapt.content.adaptation.agility;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AgilityWallJumpLatchStateTest {
  private static final Path SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/agility/AgilityWallJump.java");

  @Test
  void unregisterClearsAllLatchTrackingState() throws Exception {
    AgilityWallJump adaptation = new AgilityWallJump();
    UUID id = UUID.randomUUID();
    Map<UUID, Double> airjumps = field(adaptation, "airjumps");
    Map<UUID, Vector> horizontalIntent = field(adaptation, "horizontalIntent");
    Map<UUID, Long> horizontalIntentTime = field(adaptation, "horizontalIntentTime");
    Map<UUID, Boolean> sneakState = field(adaptation, "sneakState");
    Map<UUID, Block> latchedWalls = field(adaptation, "latchedWalls");
    airjumps.put(id, 1.5D);
    horizontalIntent.put(id, new Vector(1, 0, 0));
    horizontalIntentTime.put(id, 1_000L);
    sneakState.put(id, true);
    latchedWalls.put(id, mock(Block.class));

    adaptation.unregister();

    assertThat(airjumps).isEmpty();
    assertThat(horizontalIntent).isEmpty();
    assertThat(horizontalIntentTime).isEmpty();
    assertThat(sneakState).isEmpty();
    assertThat(latchedWalls).isEmpty();
  }

  @Test
  void latchStateDrivesJumpReleaseDecision() {
    assertThat(AgilityWallJump.shouldReleaseJump(false, true)).isTrue();
    assertThat(AgilityWallJump.shouldReleaseJump(true, true)).isFalse();
    assertThat(AgilityWallJump.shouldReleaseJump(false, false)).isFalse();
    assertThat(AgilityWallJump.shouldReleaseJump(true, false)).isFalse();
  }

  @Test
  void latchFallProtectionClearsAccumulatedFallDistance() {
    Player player = mock(Player.class);

    AgilityWallJump.clearLatchedFallDistance(player);

    verify(player).setFallDistance(0F);
  }

  @Test
  void activeAndEndingLatchesBothClearFallDistance() throws Exception {
    String source = Files.readString(SOURCE);
    int latch = source.indexOf("latchedWalls.put(id, stickBlock);");
    int activeReset = source.indexOf("clearLatchedFallDistance(p);", latch);
    int attributeApply = source.indexOf("AdaptAttributeService.get().apply", activeReset);
    int release = source.indexOf("private boolean releaseLatch(Player p)");
    int releaseReset = source.indexOf("clearLatchedFallDistance(p);", release);
    int attributeRemove = source.indexOf("AdaptAttributeService.get().remove", releaseReset);

    assertThat(latch).isGreaterThanOrEqualTo(0);
    assertThat(activeReset).isGreaterThan(latch);
    assertThat(attributeApply).isGreaterThan(activeReset);
    assertThat(release).isGreaterThan(attributeApply);
    assertThat(releaseReset).isGreaterThan(release);
    assertThat(attributeRemove).isGreaterThan(releaseReset);
  }

  @SuppressWarnings("unchecked")
  private static <T> T field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return (T) field.get(target);
  }
}
