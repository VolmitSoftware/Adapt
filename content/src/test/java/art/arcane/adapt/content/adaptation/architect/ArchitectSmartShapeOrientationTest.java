package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectSmartShapeOrientationTest {
  private static final List<String> ALL_SIX = List.of("north", "east", "south", "west", "up", "down");
  private static final List<String> HORIZONTAL_ONLY = List.of("north", "east", "south", "west");

  @Test
  void sixWayBlocksCycleTheFourYawFacesAndNeverPointUpOrDown() {
    BlockStateRef barrel = state("barrel", "facing", "north", ALL_SIX);
    List<String> visited = new ArrayList<>();

    for (int rotation = 0; rotation < 8; rotation++) {
      ArchitectSmartShape.Rotation turned = ArchitectSmartShape.rotateData(barrel);
      assertThat(turned.options()).isEqualTo(4);
      barrel = turned.state();
      visited.add(barrel.property("facing"));
    }

    assertThat(visited).containsExactly(
        "east", "south", "west", "north",
        "east", "south", "west", "north"
    );
    assertThat(visited).doesNotContain("up", "down");
  }

  @Test
  void aBlockAlreadyFacingUpOrDownIsLeftAlone() {
    BlockStateRef facingUp = state("barrel", "facing", "up", ALL_SIX);
    BlockStateRef facingDown = state("barrel", "facing", "down", ALL_SIX);

    ArchitectSmartShape.Rotation up = ArchitectSmartShape.rotateData(facingUp);
    ArchitectSmartShape.Rotation down = ArchitectSmartShape.rotateData(facingDown);

    assertThat(up.options()).isZero();
    assertThat(down.options()).isZero();
    assertThat(up.state()).isSameAs(facingUp);
    assertThat(down.state()).isSameAs(facingDown);
  }

  @Test
  void verticalOnlyBlocksAreNeverFlipped() {
    BlockStateRef dripstone = state("pointed_dripstone", "facing", "down", List.of("up", "down"));

    ArchitectSmartShape.Rotation turned = ArchitectSmartShape.rotateData(dripstone);

    assertThat(turned.options()).isZero();
    assertThat(turned.state()).isSameAs(dripstone);
  }

  @Test
  void horizontalBlocksKeepTheirFullFourStepCycle() {
    BlockStateRef stairs = state("oak_stairs", "facing", "west", HORIZONTAL_ONLY);

    ArchitectSmartShape.Rotation turned = ArchitectSmartShape.rotateData(stairs);

    assertThat(turned.options()).isEqualTo(4);
    assertThat(turned.state().property("facing")).isEqualTo("north");
  }

  @Test
  void yawFacesDropEveryNonCardinalOption() {
    assertThat(ArchitectSmartShape.yawFaces(ALL_SIX))
        .containsExactly("north", "east", "south", "west");
    assertThat(ArchitectSmartShape.yawFaces(Set.of("up", "down"))).isEmpty();
    assertThat(ArchitectSmartShape.yawFaces(Set.of())).isEmpty();
    assertThat(ArchitectSmartShape.yawFaces(null)).isEmpty();
    assertThat(ArchitectSmartShape.nextYawFace("north", List.of("north"))).isNull();
    assertThat(ArchitectSmartShape.nextYawFace("up", List.of("north", "south")))
        .isNull();
  }

  @Test
  void signsKeepTheVanillaSixteenStepYawCycle() {
    BlockStateRef sign = state("oak_sign", "rotation", "8", List.of());

    ArchitectSmartShape.Rotation turned = ArchitectSmartShape.rotateData(sign);

    assertThat(turned.options()).isEqualTo(16);
    assertThat(turned.state().property("rotation")).isEqualTo("9");
    assertThat(ArchitectSmartShape.nextRotation(7)).isEqualTo(8);
    assertThat(ArchitectSmartShape.nextRotation(-1)).isEqualTo(8);
  }

  @Test
  void pillarsCycleEverySupportedAxis() {
    BlockStateRef log = state("oak_log", "axis", "x", List.of("x", "y", "z"));

    ArchitectSmartShape.Rotation first = ArchitectSmartShape.rotateData(log);
    assertThat(first.options()).isEqualTo(3);
    assertThat(first.state().property("axis")).isEqualTo("y");

    ArchitectSmartShape.Rotation second = ArchitectSmartShape.rotateData(first.state());
    assertThat(second.options()).isEqualTo(3);
    assertThat(second.state().property("axis")).isEqualTo("z");

    ArchitectSmartShape.Rotation third = ArchitectSmartShape.rotateData(second.state());
    assertThat(third.options()).isEqualTo(3);
    assertThat(third.state().property("axis")).isEqualTo("x");
  }

  @Test
  void pillarsSkipUnsupportedAxesInsteadOfRewritingTheirOwnState() {
    BlockStateRef portal = state("nether_portal", "axis", "x", List.of("x", "z"));
    BlockStateRef pinned = state("oak_log", "axis", "y", List.of("y"));

    ArchitectSmartShape.Rotation portalTurn = ArchitectSmartShape.rotateData(portal);
    assertThat(portalTurn.options()).isEqualTo(2);
    assertThat(portalTurn.state().property("axis")).isEqualTo("z");

    ArchitectSmartShape.Rotation pinnedTurn = ArchitectSmartShape.rotateData(pinned);
    assertThat(pinnedTurn.options()).isZero();
    assertThat(pinnedTurn.state()).isSameAs(pinned);
    assertThat(ArchitectSmartShape.nextAxis("x", null)).isNull();
  }

  /** A block state that only accepts the listed values for its one property; an empty list accepts any value. */
  private static BlockStateRef state(String type, String property, String value, List<String> allowed) {
    return new RestrictedState(Id.minecraft(type), Map.of(property, value), property, allowed);
  }

  private record RestrictedState(Id type, Map<String, String> properties, String restricted, List<String> allowed)
      implements BlockStateRef {
    @Override
    public boolean isAir() {
      return false;
    }

    @Override
    public boolean isSolid() {
      return true;
    }

    @Override
    public String property(String name) {
      return properties.get(name);
    }

    @Override
    public BlockStateRef with(String name, String value) {
      if (!properties.containsKey(name) || (name.equals(restricted) && !allowed.isEmpty() && !allowed.contains(value))) {
        return this;
      }
      Map<String, String> next = new LinkedHashMap<>(properties);
      next.put(name, value);
      return new RestrictedState(type, next, restricted, allowed);
    }

    @Override
    public int age() {
      return -1;
    }

    @Override
    public int maxAge() {
      return -1;
    }

    @Override
    public Id placementItem() {
      return type;
    }

    @Override
    public Id breakSound() {
      return Id.minecraft("block.stone.break");
    }

    @Override
    public boolean isPreferredTool(ItemRef tool) {
      return true;
    }

    @Override
    public boolean isCorrectTool(ItemRef tool) {
      return false;
    }

    @Override
    public String asString() {
      return type + properties.toString();
    }

    @Override
    public Object unwrap() {
      return this;
    }
  }
}
