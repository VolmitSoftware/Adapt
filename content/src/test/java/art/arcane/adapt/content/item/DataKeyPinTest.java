package art.arcane.adapt.content.item;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.engine.item.DataBoundItem;
import art.arcane.adapt.engine.item.ExperienceOrb;
import art.arcane.adapt.engine.item.KnowledgeOrb;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DataKeyPinTest {
  static Stream<Arguments> items() {
    return Stream.of(
        Arguments.of(BackpackItem.ITEM, "455783656"),
        Arguments.of(BoundSnowBall.ITEM, "669102293"),
        Arguments.of(ChronoTimeBombItem.ITEM, "703518614"),
        Arguments.of(BoundRedstoneTorch.ITEM, "1798296241"),
        Arguments.of(ChronoTimeBottle.ITEM, "1823321605"),
        Arguments.of(BoundEnderPearl.ITEM, "2115257945"),
        Arguments.of(BoundEyeOfEnder.ITEM, "-963509705"));
  }

  static Stream<Arguments> orbs() {
    return Stream.of(
        Arguments.of(KnowledgeOrb.io, "730856154"),
        Arguments.of(ExperienceOrb.io, "-933346656"));
  }

  @ParameterizedTest
  @MethodSource("items")
  void persistedDataKeysNeverChange(CustomItem.Definition<?> item, String path) {
    assertThat(item.dataKey()).isEqualTo(Id.of("adapt", path));
  }

  @ParameterizedTest
  @MethodSource("orbs")
  void persistedOrbDataKeysNeverChange(DataBoundItem<?> item, String path) {
    assertThat(item.dataKey().id()).isEqualTo(Id.of("adapt", path));
  }
}
