package art.arcane.adapt.api.advancement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptAdvancementLayoutTest {

  @Test
  @DisplayName("advancement branches use adjacent rows instead of flattened subtree offsets")
  void branchesUseAdjacentRows() {
    AdaptAdvancement firstLeaf = node("first-leaf");
    AdaptAdvancement firstMiddle = node("first-middle", firstLeaf);
    AdaptAdvancement first = node("first", firstMiddle);
    AdaptAdvancement secondLeaf = node("second-leaf");
    AdaptAdvancement second = node("second", secondLeaf);
    AdaptAdvancement thirdLeaf = node("third-leaf");
    AdaptAdvancement third = node("third", thirdLeaf);
    AdaptAdvancement fourthLeaf = node("fourth-leaf");
    AdaptAdvancement fourth = node("fourth", fourthLeaf);
    AdaptAdvancement root = node("root", first, second, third, fourth);

    Map<AdaptAdvancement, AdaptAdvancement.LayoutPosition> positions = root.layoutPositions();

    assertThat(positions.get(first).y()).isEqualTo(1F);
    assertThat(positions.get(second).y()).isEqualTo(2F);
    assertThat(positions.get(third).y()).isEqualTo(3F);
    assertThat(positions.get(fourth).y()).isEqualTo(4F);
    assertThat(positions.get(root).y()).isEqualTo(2.5F);
    assertThat(positions.get(first).x()).isEqualTo(2F);
    assertThat(positions.get(firstMiddle).x()).isEqualTo(3F);
    assertThat(positions.get(firstLeaf).x()).isEqualTo(4F);
    assertThat(positions.get(firstLeaf).y()).isEqualTo(positions.get(first).y());
  }

  @Test
  @DisplayName("branch parents are centered over their children")
  void branchParentsAreCentered() {
    AdaptAdvancement upper = node("upper");
    AdaptAdvancement upperMiddle = node("upper-middle");
    AdaptAdvancement lowerMiddle = node("lower-middle");
    AdaptAdvancement lower = node("lower");
    AdaptAdvancement branch = node("branch", upper, upperMiddle, lowerMiddle, lower);
    AdaptAdvancement root = node("root", branch);

    Map<AdaptAdvancement, AdaptAdvancement.LayoutPosition> positions = root.layoutPositions();

    assertThat(positions.get(upper).y()).isEqualTo(1F);
    assertThat(positions.get(lower).y()).isEqualTo(4F);
    assertThat(positions.get(branch).y()).isEqualTo(2.5F);
    assertThat(positions.get(root).y()).isEqualTo(2.5F);
  }

  private AdaptAdvancement node(String key, AdaptAdvancement... children) {
    return AdaptAdvancement.builder()
        .key(key)
        .children(List.of(children))
        .build();
  }
}
