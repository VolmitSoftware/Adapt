package art.arcane.adapt.engine.skill;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.api.content.AdaptationRequirement;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.advancement.AdaptAdvancement;
import art.arcane.adapt.engine.bridge.ComponentBuilder;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContentIdFormsTest {
  private static final Id LUCK = Id.adapt("herbalism/luck");
  private static final Id ADDON = Id.of("alchemist", "alchemy/transmute");

  @Test
  void localizationKeysKeepTheBuiltInKeysAndPrefixOtherNamespaces() {
    assertThat(ComponentBuilder.builtInKey(Id.adapt("herbalism/green_thumb"))).isEqualTo("adapt.herbalism.green_thumb");
    assertThat(TextKeys.adaptation(ADDON)).isEqualTo("alchemist.adaptation.alchemy.transmute");
  }

  @Test
  void usePermissionsNameTheNamespaceAndKeepEveryPathCharacter() {
    assertThat(UsePermissions.useNode(LUCK)).isEqualTo("adapt.use.adapt.herbalism.luck");
    assertThat(UsePermissions.useNode(Id.adapt("swords"))).isEqualTo("adapt.use.adapt.swords");
    assertThat(UsePermissions.useNode(Id.adapt("a/b-c"))).isNotEqualTo(UsePermissions.useNode(Id.adapt("a/bc")));
  }

  @Test
  void configFilesLiveUnderTheirNamespace() {
    assertThat(AdaptEnvironment.contentConfigPath("adaptations", LUCK)).isEqualTo("adaptations/adapt/herbalism/luck.toml");
    assertThat(AdaptEnvironment.contentIdOfConfig("adapt/herbalism/luck.toml")).isEqualTo(LUCK);
    assertThat(AdaptEnvironment.contentIdOfConfig("alchemist/alchemy/transmute.toml")).isEqualTo(ADDON);
    assertThat(AdaptEnvironment.contentIdOfConfig("luck.toml")).isNull();
  }

  @Test
  void advancementKeysCarryTheFullIdAndLeadBackToTheAdaptation() {
    assertThat(AdaptAdvancement.skillKey(Id.adapt("swords"))).isEqualTo("skill/adapt/swords");
    assertThat(AdaptAdvancement.adaptationKey(ADDON)).isEqualTo("adaptation/alchemist/alchemy/transmute");
    assertThat(AdaptAdvancement.adaptationOfKey(AdaptAdvancement.adaptationKey(ADDON))).isEqualTo(ADDON);
    assertThat(AdaptAdvancement.adaptationOfKey("skill/adapt/swords")).isNull();
  }

  @Test
  void requirementsUseTheLevelSeparatorAndBareIdsAreAdapt() {
    List<AdaptationRequirement> parsed = AdaptationHost.parseRequirements(LUCK,
        List.of("adapt:herbalism/myconid@2", "herbalism/terralid@1", "alchemist:alchemy/transmute@3"));
    assertThat(parsed).containsExactly(
        new AdaptationRequirement(Id.adapt("herbalism/myconid"), 2),
        new AdaptationRequirement(Id.adapt("herbalism/terralid"), 1),
        new AdaptationRequirement(ADDON, 3));
    assertThat(parsed.get(0).entry()).isEqualTo("adapt:herbalism/myconid@2");
  }

  @Test
  void configEntriesNameContentWithOrWithoutTheAdaptNamespace() {
    assertThat(AdaptConfig.contentKey(" Swords ")).isEqualTo("adapt:swords");
    assertThat(AdaptConfig.contentKey("alchemist:alchemy")).isEqualTo("alchemist:alchemy");
    assertThat(AdaptConfig.contentEntry(Map.of("herbalism/luck", false), LUCK.toString())).isFalse();
  }

  @Test
  void legacyNamesMatchTheFlatNamesArtAndFieldKeysStillUse() {
    assertThat(LegacyContentNames.of(Id.adapt("herbalism/green_thumb"))).isEqualTo("herbalism-green-thumb");
    assertThat(LegacyContentNames.of(Id.adapt("swords/upgrade/tempest_flow"))).isEqualTo("upgraded-swords-tempest-flow");
    assertThat(LegacyContentNames.of(Id.adapt("swords/block/strike_fight"))).isEqualTo("block-swords-strike-fight");
    assertThat(LegacyContentNames.of(Id.adapt("swords/tool/whetstone"))).isEqualTo("tool-swords-whetstone");
    assertThat(LegacyContentNames.of("adapt:swords+adapt:stealth")).isEqualTo("mutated-swords-stealth");
    assertThat(LegacyContentNames.of(Id.adapt("mutated/chronos/adapt/swords/signature/convergence")))
        .isEqualTo("signature-chronos-swords-convergence");
    assertThat(LegacyContentNames.of(ADDON)).isEqualTo("alchemist:alchemy/transmute");
  }
}
