package art.arcane.adapt.content.golem.plinth;

import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.content.golem.GolemRuneType;
import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InfusionTintTest {
  @Test
  void everyAspectAndTheMasterworkHeartHaveTheirOwnTint() {
    Set<Rgb> colors = new HashSet<>();
    for (GolemRuneType aspect : GolemRuneType.values()) {
      InfusionTint tint = InfusionTint.of(aspect);
      assertThat(tint).as("%s", aspect).isNotNull();
      assertThat(tint.dust()).as("%s", aspect).hasSize(1);
      assertThat(colors.add(tint.color())).as("%s has a colour of its own", aspect).isTrue();
    }
    InfusionTint heart = InfusionTint.of(infusion("golemancy:masterwork_heart"));
    assertThat(heart.dust()).hasSize(InfusionVortex.ARMS);
    for (ParticleData.Dust dust : heart.dust()) {
      assertThat(colors.add(dust.color())).as("prismatic heart colour %s", dust.color()).isTrue();
    }
  }

  @Test
  void eachAspectReadsAsItsElement() {
    assertThat(hue(GolemRuneType.SWIFTNESS)).as("pale cyan").matches(c -> c.blue() > 200 && c.green() > 200 && c.red() < c.green());
    assertThat(hue(GolemRuneType.MIGHT)).as("iron grey").matches(c -> spread(c) < 24 && c.red() > 120 && c.red() < 220);
    assertThat(hue(GolemRuneType.EMBER)).as("flame orange").matches(c -> c.red() > 200 && c.green() > c.blue() && c.green() < c.red());
    assertThat(hue(GolemRuneType.TIDE)).as("deep blue").matches(c -> c.blue() > 150 && c.red() < 80 && c.green() < c.blue());
    assertThat(hue(GolemRuneType.ORDER)).as("gold").matches(c -> c.red() > 200 && c.green() > 150 && c.blue() < 100);
    assertThat(hue(GolemRuneType.ENTROPY)).as("purple").matches(c -> c.red() > c.green() && c.blue() > c.green() && c.blue() > 150);
    assertThat(hue(GolemRuneType.BULWARK)).as("obsidian violet").matches(c -> c.blue() > c.green() && c.red() > c.green() && c.blue() < 140);
    assertThat(InfusionTint.of(GolemRuneType.SWIFTNESS).accent()).isEqualTo(Particles.CLOUD);
    assertThat(InfusionTint.of(GolemRuneType.EMBER).accent()).isEqualTo(Particles.FLAME);
    assertThat(InfusionTint.of(GolemRuneType.TIDE).accent()).isEqualTo(Particles.BUBBLE_POP);
  }

  @Test
  void accentParticlesNeedNoData() {
    for (GolemRuneType aspect : GolemRuneType.values()) {
      assertThat(InfusionTint.of(aspect).accent().data()).as("%s", aspect).isInstanceOf(ParticleData.None.class);
    }
    assertThat(InfusionTint.of(infusion("golemancy:masterwork_heart")).accent().data()).isInstanceOf(ParticleData.None.class);
  }

  @Test
  void anInfusionTakesTheTintOfTheRuneItMakes() {
    for (GolemRuneType aspect : GolemRuneType.values()) {
      for (int tier = GolemRuneType.MIN_TIER; tier <= GolemRuneType.MAX_TIER; tier++) {
        assertThat(InfusionTint.of(infusion("golemancy:rune/" + aspect.id() + "/" + tier))).as("%s %d", aspect, tier).isSameAs(InfusionTint.of(aspect));
      }
    }
    assertThat(InfusionTint.of(infusion("golemancy:masterwork_heart"))).isNotIn(tints());
  }

  @Test
  void dustCyclesThroughThePrismaticColours() {
    InfusionTint heart = InfusionTint.of(infusion("golemancy:masterwork_heart"));
    assertThat(heart.dust(0)).isSameAs(heart.dust(InfusionVortex.ARMS));
    assertThat(heart.dust(1)).isNotSameAs(heart.dust(0));
    assertThat(heart.dust(-1)).isSameAs(heart.dust(InfusionVortex.ARMS - 1));
    InfusionTint tide = InfusionTint.of(GolemRuneType.TIDE);
    assertThat(tide.dust(3)).isSameAs(tide.dust(0));
    assertThat(tide.color()).isEqualTo(tide.dust(0).color());
  }

  @Test
  void trailDustSharesTheColourAtASmallerSize() {
    for (GolemRuneType aspect : GolemRuneType.values()) {
      InfusionTint tint = InfusionTint.of(aspect);
      assertThat(tint.trail()).hasSameSizeAs(tint.dust());
      assertThat(tint.trail(0).color()).as("%s", aspect).isEqualTo(tint.color());
      assertThat(tint.trail(0).size()).as("%s", aspect).isLessThan(tint.dust(0).size());
    }
    InfusionTint heart = InfusionTint.of(infusion("golemancy:masterwork_heart"));
    for (int index = 0; index < InfusionVortex.ARMS; index++) {
      assertThat(heart.trail(index).color()).isEqualTo(heart.dust(index).color());
    }
  }

  private static Rgb hue(GolemRuneType aspect) {
    return InfusionTint.of(aspect).color();
  }

  private static int spread(Rgb color) {
    int max = Math.max(color.red(), Math.max(color.green(), color.blue()));
    int min = Math.min(color.red(), Math.min(color.green(), color.blue()));
    return max - min;
  }

  private static List<InfusionTint> tints() {
    return List.of(InfusionTint.of(GolemRuneType.SWIFTNESS), InfusionTint.of(GolemRuneType.MIGHT), InfusionTint.of(GolemRuneType.EMBER),
        InfusionTint.of(GolemRuneType.TIDE), InfusionTint.of(GolemRuneType.ORDER), InfusionTint.of(GolemRuneType.ENTROPY),
        InfusionTint.of(GolemRuneType.BULWARK));
  }

  static Infusion infusion(String result) {
    return infusion(result, 100);
  }

  static Infusion infusion(String result, int durationTicks) {
    return new Infusion("test", Id.parse(result), () -> mock(ItemRef.class), item -> true, List.of(item -> true, item -> true,
        item -> true, item -> true), 2, durationTicks, InfusionRecipe.RUNIC_AUGMENTS.toString(), 1);
  }
}
