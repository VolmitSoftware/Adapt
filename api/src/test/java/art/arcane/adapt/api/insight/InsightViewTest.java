package art.arcane.adapt.api.insight;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InsightViewTest {
  private static final Id WOLF = Id.minecraft("wolf");

  @Test
  void moreEffectsThanTheLimitAreRefusedWhenBuiltNamingTheField() {
    InsightView.Builder view = InsightView.builder(1, WOLF, InsightKind.PASSIVE).health(10D, 20D);
    for (int index = 0; index <= InsightView.MAX_EFFECTS; index++) {
      view.effect(InsightEffect.beneficial(Id.minecraft("speed"), 0, index));
    }

    assertThatThrownBy(view::build).isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("effects holds 33 entries");
  }

  @Test
  void moreStatsOrTagsThanTheLimitAreRefusedNamingTheField() {
    InsightView.Builder stats = InsightView.builder(1, WOLF, InsightKind.PASSIVE);
    InsightView.Builder tags = InsightView.builder(1, WOLF, InsightKind.PASSIVE);
    for (int index = 0; index <= InsightView.MAX_STATS; index++) {
      stats.stat(InsightStat.of(Id.of("example", "stat_" + index), index));
      tags.tag(Id.of("example", "tag_" + index));
    }

    assertThatThrownBy(stats::build).hasMessageStartingWith("stats holds");
    assertThatThrownBy(tags::build).hasMessageStartingWith("tags holds");
  }

  @Test
  void anOverlongOwnerNameOrANegativeAmountIsRefusedNamingTheField() {
    assertThatThrownBy(() -> InsightView.builder(1, WOLF, InsightKind.PASSIVE)
        .bond(InsightBond.OWNED, "x".repeat(InsightView.MAX_OWNER_NAME_BYTES + 1)).build())
        .hasMessageStartingWith("ownerName is 257 UTF-8 bytes");
    assertThatThrownBy(() -> InsightView.builder(1, WOLF, InsightKind.PASSIVE).health(-1D, 20D).build())
        .hasMessageStartingWith("health must be finite");
    assertThatThrownBy(() -> InsightStat.of(Id.minecraft("armor"), Double.NaN))
        .hasMessageContaining("minecraft:armor");
    assertThatThrownBy(() -> InsightMark.of(1, InsightKind.HOSTILE, 1.5D)).hasMessageStartingWith("health");
  }

  @Test
  void viewsBuiltFromTheSameValuesAreEqual() {
    InsightView first = InsightView.builder(3, WOLF, InsightKind.PASSIVE).health(4D, 8D)
        .tag(Id.adapt("stable_hand")).stat(InsightStat.of(Id.minecraft("movement_speed"), 0.3D)).build();
    InsightView second = InsightView.builder(3, WOLF, InsightKind.PASSIVE).health(4D, 8D)
        .tag(Id.adapt("stable_hand")).stat(InsightStat.of(Id.minecraft("movement_speed"), 0.3D)).build();

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    assertThat(first.tags()).containsExactly(Id.adapt("stable_hand"));
  }
}
