package art.arcane.adapt.engine.mutation.pair;

import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.PairText;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Claims per slot and pair, the namespace that holds each, and refusals of a second holder. */
class PairRegistryTest {
  private static final SkillPair BUILT_IN_WITH_ADDON = new SkillPair(Id.adapt("swords"), Id.of("forge", "smithing"));
  private static final SkillPair TWO_ADDONS = new SkillPair(Id.of("forge", "smithing"), Id.of("loom", "weaving"));
  private static final MutationSlot SIGNATURE_SLOT = new MutationSlot(BUILT_IN_WITH_ADDON, Recipe.CONVERGENCE);
  private static final MutationSlot TEXT_SLOT = new MutationSlot(TWO_ADDONS, Recipe.GRAFT);
  private static final Function<MutationSlot, ? extends Signature<?>> FACTORY = (MutationSlot slot) -> null;

  @Test
  void claimsCarryTheirNamespaceAndUninstallWithdrawsThem() {
    PairRegistry registry = new PairRegistry();
    PairClaims.Builder forge = new PairClaims.Builder("forge");
    forge.signature(SIGNATURE_SLOT, FACTORY);
    forge.text(TEXT_SLOT, PairText.of(Text.literal("Hammers the weave.")));
    forge.name(TWO_ADDONS, Text.literal("Loomsmith"));
    long before = registry.revision();

    registry.install(forge.build());

    assertThat(registry.revision()).isGreaterThan(before);
    assertThat(registry.signature(SIGNATURE_SLOT)).get().extracting(PairRegistry.Claim::owner).isEqualTo("forge");
    assertThat(registry.signature(SIGNATURE_SLOT).orElseThrow().value()).isSameAs(FACTORY);
    assertThat(registry.text(TEXT_SLOT)).get().extracting(PairRegistry.Claim::owner).isEqualTo("forge");
    assertThat(registry.name(TWO_ADDONS)).get().extracting(PairRegistry.Claim::owner).isEqualTo("forge");
    assertThat(registry.name(BUILT_IN_WITH_ADDON)).isEmpty();
    assertThat(registry.signatureSlots()).containsExactly(SIGNATURE_SLOT);

    long installed = registry.revision();
    assertThat(registry.withdraw("forge")).isTrue();

    assertThat(registry.revision()).isGreaterThan(installed);
    assertThat(registry.signature(SIGNATURE_SLOT)).isEmpty();
    assertThat(registry.text(TEXT_SLOT)).isEmpty();
    assertThat(registry.name(TWO_ADDONS)).isEmpty();
    assertThat(registry.withdraw("forge")).isFalse();
  }

  @Test
  void anAddonMayClaimASlotOfAPairWhoseSkillsAreNotItsOwn() {
    PairRegistry registry = new PairRegistry();
    MutationSlot builtIn = new MutationSlot(new SkillPair(Id.adapt("swords"), Id.adapt("stealth")), Recipe.ECHO);
    PairClaims.Builder loom = new PairClaims.Builder("loom");
    loom.text(builtIn, PairText.of(Text.literal("A quiet cut.")));

    registry.install(loom.build());

    assertThat(registry.text(builtIn)).get().extracting(PairRegistry.Claim::owner).isEqualTo("loom");
  }

  @Test
  void aSecondProviderOfASlotOrNameIsRefusedNamingBoth() {
    PairRegistry registry = new PairRegistry();
    PairClaims.Builder forge = new PairClaims.Builder("forge");
    forge.signature(SIGNATURE_SLOT, FACTORY);
    forge.name(TWO_ADDONS, Text.literal("Loomsmith"));
    registry.install(forge.build());

    PairClaims.Builder signatureRival = new PairClaims.Builder("rival");
    signatureRival.signature(SIGNATURE_SLOT, FACTORY);
    PairClaims.Builder nameRival = new PairClaims.Builder("rival");
    nameRival.name(TWO_ADDONS, Text.literal("Weaver's Forge"));

    assertThatThrownBy(() -> registry.install(signatureRival.build()))
        .hasMessageContaining("forge").hasMessageContaining("rival").hasMessageContaining(SIGNATURE_SLOT.toString());
    assertThatThrownBy(() -> registry.requireFree(nameRival.build()))
        .hasMessageContaining("forge").hasMessageContaining("rival").hasMessageContaining(TWO_ADDONS.toString());
    assertThat(registry.signature(SIGNATURE_SLOT).orElseThrow().owner()).isEqualTo("forge");
  }

  @Test
  void aRefreshMayClaimItsOwnSlotsAgain() {
    PairRegistry registry = new PairRegistry();
    PairClaims.Builder first = new PairClaims.Builder("forge");
    first.signature(SIGNATURE_SLOT, FACTORY);
    registry.install(first.build());
    PairClaims.Builder again = new PairClaims.Builder("forge");
    again.text(TEXT_SLOT, PairText.of(Text.literal("Hammers the weave.")));

    registry.install(again.build());

    assertThat(registry.signature(SIGNATURE_SLOT)).isEmpty();
    assertThat(registry.text(TEXT_SLOT)).isPresent();
  }

  @Test
  void aDuplicateClaimInsideOneAddonIsRefused() {
    PairClaims.Builder forge = new PairClaims.Builder("forge");
    forge.signature(SIGNATURE_SLOT, FACTORY);
    forge.text(TEXT_SLOT, PairText.of(Text.literal("One.")));
    forge.name(TWO_ADDONS, Text.literal("Loomsmith"));

    assertThatThrownBy(() -> forge.signature(SIGNATURE_SLOT, FACTORY)).hasMessageContaining("forge")
        .hasMessageContaining("twice");
    assertThatThrownBy(() -> forge.text(TEXT_SLOT, PairText.of(Text.literal("Two.")))).hasMessageContaining("twice");
    assertThatThrownBy(() -> forge.name(TWO_ADDONS, Text.literal("Again"))).hasMessageContaining("twice");
  }

  @Test
  void aSlotWithASignatureHidesItsText() {
    PairRegistry registry = new PairRegistry();
    PairClaims.Builder forge = new PairClaims.Builder("forge");
    forge.text(SIGNATURE_SLOT, PairText.of(Text.literal("Generated text, replaced.")));
    registry.install(forge.build());
    PairClaims.Builder loom = new PairClaims.Builder("loom");
    loom.signature(SIGNATURE_SLOT, FACTORY);

    registry.install(loom.build());

    assertThat(registry.text(SIGNATURE_SLOT)).isEmpty();
    registry.withdraw("loom");
    assertThat(registry.text(SIGNATURE_SLOT)).get().extracting(PairRegistry.Claim::owner).isEqualTo("forge");
  }
}
