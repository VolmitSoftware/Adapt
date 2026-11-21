package art.arcane.adapt.client.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IconSpritesTest {
  @Test
  void conventionPathsFollowKindAndId() {
    assertThat(IconSprites.skill("agility")).isEqualTo("adapt:textures/gui/skill/agility.png");
    assertThat(IconSprites.adaptation("agility-wall-jump")).isEqualTo("adapt:textures/gui/adaptation/agility-wall-jump.png");
    assertThat(IconSprites.toast("level_up")).isEqualTo("adapt:textures/gui/toast/level_up.png");
    assertThat(IconSprites.hud("rift_gate")).isEqualTo("adapt:textures/gui/hud/rift_gate.png");
  }

  @Test
  void preferencePathsNameOwnerPreferenceAndChoice() {
    assertThat(IconSprites.preferenceChoice("agility-wall-jump", "control", "SNEAK"))
        .isEqualTo("adapt:textures/gui/preference/agility-wall-jump/control/sneak.png");
    assertThat(IconSprites.commonChoice("HALF")).isEqualTo("adapt:textures/gui/preference/common/choice/half.png");
    assertThat(IconSprites.preferenceMember("herbalism-growables", "crops"))
        .isEqualTo("adapt:textures/gui/preference/herbalism-growables/crops.png");
    assertThat(IconSprites.preferenceGroup("herbalism-growables", "plants"))
        .isEqualTo("adapt:textures/gui/preference/herbalism-growables/group-plants.png");
  }

  @Test
  void pathSegmentLowercasesAndReplacesCharactersResourcePathsReject() {
    assertThat(IconSprites.pathSegment("Trag'Oul Blood")).isEqualTo("trag_oul_blood");
    assertThat(IconSprites.pathSegment("a/b:c")).isEqualTo("a_b_c");
    assertThat(IconSprites.pathSegment("")).isEqualTo("_");
  }

  @Test
  void withSpriteKeepsAnExplicitSprite() {
    IconRef explicit = new IconRef("minecraft:feather", 0, null, "adapt:textures/gui/custom.png");
    assertThat(IconSprites.withSprite(explicit, IconSprites.skill("agility"))).isSameAs(explicit);
    IconRef filled = IconSprites.withSprite(new IconRef("minecraft:feather", 7, "adapt:feather", null), IconSprites.skill("agility"));
    assertThat(filled).isEqualTo(new IconRef("minecraft:feather", 7, "adapt:feather", "adapt:textures/gui/skill/agility.png"));
    assertThat(IconSprites.withSprite(null, "x")).isNull();
  }
}
