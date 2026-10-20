package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.api.golem.GolemRuneType;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InfusionTintTest {
  @Test
  void everyAspectAndTheMasterworkHeartHaveTheirOwnTint() {
    Set<Color> colors = new HashSet<>();
    for (GolemRuneType aspect : GolemRuneType.values()) {
      InfusionTint tint = InfusionTint.of(aspect);
      assertThat(tint).as("%s", aspect).isNotNull();
      assertThat(tint.dust()).as("%s", aspect).hasSize(1);
      assertThat(colors.add(tint.color())).as("%s has a colour of its own", aspect).isTrue();
    }
    InfusionTint heart = InfusionTint.of(infusion("golemancy:masterwork_heart"));
    assertThat(heart.dust()).hasSize(InfusionVortex.ARMS);
    for (Particle.DustOptions dust : heart.dust()) {
      assertThat(colors.add(dust.getColor())).as("prismatic heart colour %s", dust.getColor()).isTrue();
    }
  }

  @Test
  void eachAspectReadsAsItsElement() {
    assertThat(hue(GolemRuneType.SWIFTNESS)).as("pale cyan").matches(c -> c.getBlue() > 200 && c.getGreen() > 200 && c.getRed() < c.getGreen());
    assertThat(hue(GolemRuneType.MIGHT)).as("iron grey").matches(c -> spread(c) < 24 && c.getRed() > 120 && c.getRed() < 220);
    assertThat(hue(GolemRuneType.EMBER)).as("flame orange").matches(c -> c.getRed() > 200 && c.getGreen() > c.getBlue() && c.getGreen() < c.getRed());
    assertThat(hue(GolemRuneType.TIDE)).as("deep blue").matches(c -> c.getBlue() > 150 && c.getRed() < 80 && c.getGreen() < c.getBlue());
    assertThat(hue(GolemRuneType.ORDER)).as("gold").matches(c -> c.getRed() > 200 && c.getGreen() > 150 && c.getBlue() < 100);
    assertThat(hue(GolemRuneType.ENTROPY)).as("purple").matches(c -> c.getRed() > c.getGreen() && c.getBlue() > c.getGreen() && c.getBlue() > 150);
    assertThat(hue(GolemRuneType.BULWARK)).as("obsidian violet").matches(c -> c.getBlue() > c.getGreen() && c.getRed() > c.getGreen() && c.getBlue() < 140);
    assertThat(InfusionTint.of(GolemRuneType.SWIFTNESS).accent()).isEqualTo(Particle.CLOUD);
    assertThat(InfusionTint.of(GolemRuneType.EMBER).accent()).isEqualTo(Particle.FLAME);
    assertThat(InfusionTint.of(GolemRuneType.TIDE).accent()).isEqualTo(Particle.BUBBLE_POP);
  }

  @Test
  void accentParticlesNeedNoData() {
    for (GolemRuneType aspect : GolemRuneType.values()) {
      assertThat(InfusionTint.of(aspect).accent().getDataType()).as("%s", aspect).isEqualTo(Void.class);
    }
    assertThat(InfusionTint.of(infusion("golemancy:masterwork_heart")).accent().getDataType()).isEqualTo(Void.class);
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
    assertThat(tide.color()).isEqualTo(tide.dust(0).getColor());
  }

  @Test
  void trailDustSharesTheColourAtASmallerSize() {
    for (GolemRuneType aspect : GolemRuneType.values()) {
      InfusionTint tint = InfusionTint.of(aspect);
      assertThat(tint.trail()).hasSameSizeAs(tint.dust());
      assertThat(tint.trail(0).getColor()).as("%s", aspect).isEqualTo(tint.color());
      assertThat(tint.trail(0).getSize()).as("%s", aspect).isLessThan(tint.dust(0).getSize());
    }
    InfusionTint heart = InfusionTint.of(infusion("golemancy:masterwork_heart"));
    for (int index = 0; index < InfusionVortex.ARMS; index++) {
      assertThat(heart.trail(index).getColor()).isEqualTo(heart.dust(index).getColor());
    }
  }

  private static Color hue(GolemRuneType aspect) {
    return InfusionTint.of(aspect).color();
  }

  private static int spread(Color color) {
    int max = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
    int min = Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue()));
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
    return new Infusion("test", NamespacedKey.fromString(result), () -> mock(ItemStack.class), item -> true, List.of(item -> true, item -> true,
        item -> true, item -> true), 2, durationTicks, InfusionRecipe.RUNIC_AUGMENTS, 1);
  }
}
