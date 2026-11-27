package art.arcane.adapt.client.view;

import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class IconSpritesTest {
  @TempDir
  Path dataFolder;

  @Test
  void conventionPathsFollowKindAndId() {
    assertThat(IconSprites.skill(Id.adapt("agility"))).isEqualTo("adapt:textures/gui/skill/agility.png");
    assertThat(IconSprites.adaptation(Id.adapt("agility/wall_jump"))).isEqualTo("adapt:textures/gui/adaptation/agility-wall-jump.png");
    assertThat(IconSprites.toast("level_up")).isEqualTo("adapt:textures/gui/toast/level_up.png");
    assertThat(IconSprites.hud("rift_gate")).isEqualTo("adapt:textures/gui/hud/rift_gate.png");
  }

  @Test
  void contentArtLivesInTheContentsOwnNamespace() {
    Id alchemy = Id.of("alchemist", "alchemy");
    Id transmute = Id.of("alchemist", "alchemy/transmute");
    assertThat(IconSprites.skill(alchemy)).isEqualTo("alchemist:textures/gui/skill/alchemy.png");
    assertThat(IconSprites.adaptation(transmute)).isEqualTo("alchemist:textures/gui/adaptation/alchemy/transmute.png");
    assertThat(IconSprites.emblem(alchemy)).isEqualTo("alchemist:textures/gui/emblem/alchemy.png");
    assertThat(IconSprites.trim(alchemy)).isEqualTo("alchemist:textures/gui/trim/alchemy.png");
    assertThat(IconSprites.tool(Id.of("alchemist", "alchemy/tool/still"))).isEqualTo("alchemist:textures/gui/tool/alchemy/tool/still.png");
    assertThat(IconSprites.preferenceChoice(transmute, "mode", "FAST"))
        .isEqualTo("alchemist:textures/gui/preference/alchemy/transmute/mode/fast.png");
    assertThat(IconSprites.preferenceMember(transmute, "herbs"))
        .isEqualTo("alchemist:textures/gui/preference/alchemy/transmute/herbs.png");
    assertThat(IconSprites.preferenceGroup(transmute, "plants"))
        .isEqualTo("alchemist:textures/gui/preference/alchemy/transmute/group-plants.png");
    assertThat(IconSprites.commonChoice("HALF")).startsWith("adapt:");
  }

  @Test
  void contentIdsResolveViewKeys() {
    assertThat(IconSprites.contentId("agility")).isEqualTo(Id.adapt("agility"));
    assertThat(IconSprites.contentId("alchemist:alchemy")).isEqualTo(Id.of("alchemist", "alchemy"));
    assertThat(IconSprites.contentId("adapt:swords+alchemist:alchemy"))
        .isEqualTo(new SkillPair(Id.adapt("swords"), Id.of("alchemist", "alchemy")).id());
  }

  @Test
  void preferencePathsNameOwnerPreferenceAndChoice() {
    assertThat(IconSprites.preferenceChoice(Id.adapt("agility/wall_jump"), "control", "SNEAK"))
        .isEqualTo("adapt:textures/gui/preference/agility-wall-jump/control/sneak.png");
    assertThat(IconSprites.commonChoice("HALF")).isEqualTo("adapt:textures/gui/preference/common/choice/half.png");
    assertThat(IconSprites.preferenceMember(Id.adapt("herbalism/growables"), "crops"))
        .isEqualTo("adapt:textures/gui/preference/herbalism-growables/crops.png");
    assertThat(IconSprites.preferenceGroup(Id.adapt("herbalism/growables"), "plants"))
        .isEqualTo("adapt:textures/gui/preference/herbalism-growables/group-plants.png");
  }

  @Test
  void pathSegmentLowercasesAndReplacesCharactersResourcePathsReject() {
    assertThat(IconSprites.pathSegment("Trag'Oul Blood")).isEqualTo("trag_oul_blood");
    assertThat(IconSprites.pathSegment("a/b:c")).isEqualTo("a_b_c");
    assertThat(IconSprites.pathSegment("")).isEqualTo("_");
  }

  @Test
  void stackedSpritesRoundTripTheirLayersAndTints() {
    String stack = IconSprites.stack(IconSprites.texture("glyph", "graft"),
        IconSprites.tinted(IconSprites.texture("glyph", "graft-lead"), 0x9cd86a),
        IconSprites.tinted(IconSprites.texture("glyph", "graft-partner"), 0x04a6b0));
    assertThat(stack).isEqualTo("adapt:textures/gui/glyph/graft.png|adapt:textures/gui/glyph/graft-lead.png#9cd86a"
        + "|adapt:textures/gui/glyph/graft-partner.png#04a6b0");
    assertThat(IconSprites.layers(stack)).containsExactly(
        new IconSprites.Layer("adapt:textures/gui/glyph/graft.png", IconSprites.NO_TINT),
        new IconSprites.Layer("adapt:textures/gui/glyph/graft-lead.png", 0x9cd86a),
        new IconSprites.Layer("adapt:textures/gui/glyph/graft-partner.png", 0x04a6b0));
    assertThat(IconSprites.layers("adapt:textures/gui/skill/agility.png"))
        .containsExactly(new IconSprites.Layer("adapt:textures/gui/skill/agility.png", IconSprites.NO_TINT));
  }

  @Test
  void mutationArtNamesLeadEmblemPartnerTrimAndTintedGlyphs() {
    List<Class<? extends SimpleSkill<?>>> types = BuiltinAddon.skills().stream()
        .<Class<? extends SimpleSkill<?>>>map(BuiltinAddon.Entry::type).toList();
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(dataFolder).build(), types)) {
      SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
      SkillPair pair = new SkillPair(Id.adapt("swords"), Id.adapt("chronos"));
      Skill<?> swords = skill(registry, "swords");
      Skill<?> chronos = skill(registry, "chronos");
      assertThat(MutationIcons.tile(pair)).isEqualTo("adapt:textures/gui/emblem/swords.png|adapt:textures/gui/trim/chronos.png");
      assertThat(IconSprites.layers(MutationIcons.glyph(swords, chronos, Recipe.ECHO))).containsExactly(
          new IconSprites.Layer("adapt:textures/gui/glyph/echo-lead.png", MutationIcons.tint(swords)),
          new IconSprites.Layer("adapt:textures/gui/glyph/echo-partner.png", MutationIcons.tint(chronos)),
          new IconSprites.Layer("adapt:textures/gui/glyph/echo.png", IconSprites.NO_TINT));
      for (String skill : List.of("tragoul", "blocking", "swords", "excavation")) {
        assertThat(MutationIcons.luminance(MutationIcons.tint(skill(registry, skill)))).as(skill).isGreaterThanOrEqualTo(MutationIcons.MIN_TINT_LUMINANCE);
      }
      assertThat(MutationIcons.tint(skill(registry, "excavation"))).isEqualTo(0xb07048);
      assertThat(MutationIcons.tint(skill(registry, "blocking"))).isEqualTo(0x6b97ff);
    }
    assertThat(MutationIcons.slug("Shieldbearer's Pack")).isEqualTo("shieldbearers-pack");
    assertThat(MutationIcons.slug("Cutting on the Fly")).isEqualTo("cutting-on-the-fly");
  }

  @Test
  void tintsHoldEveryRegisteredSkillTintAndWhite() {
    List<Class<? extends SimpleSkill<?>>> types = BuiltinAddon.skills().stream()
        .<Class<? extends SimpleSkill<?>>>map(BuiltinAddon.Entry::type).toList();
    try (TestAdapt adapt = TestAdapt.start(TestPlatform.builder(dataFolder).build(), types)) {
      Set<Integer> expected = new TreeSet<>();
      for (Skill<?> skill : adapt.core().getAdaptServer().getSkillRegistry().getSkills()) {
        expected.add(MutationIcons.tint(skill));
      }
      expected.add(IconSprites.NO_TINT);
      assertThat(MutationIcons.tints(adapt.core().assets())).isEqualTo(expected).contains(0x6b97ff, 0xb07048);
    }
  }

  @Test
  void withSpriteKeepsAnExplicitSprite() {
    IconRef explicit = new IconRef("minecraft:feather", 0, null, "adapt:textures/gui/custom.png");
    assertThat(IconSprites.withSprite(explicit, IconSprites.skill(Id.adapt("agility")))).isSameAs(explicit);
    IconRef filled = IconSprites.withSprite(new IconRef("minecraft:feather", 7, "adapt:feather", null),
        IconSprites.skill(Id.adapt("agility")));
    assertThat(filled).isEqualTo(new IconRef("minecraft:feather", 7, "adapt:feather", "adapt:textures/gui/skill/agility.png"));
    assertThat(IconSprites.withSprite(null, "x")).isNull();
  }

  private static Skill<?> skill(SkillRegistry registry, String path) {
    return registry.getSkill(Id.adapt(path));
  }
}
