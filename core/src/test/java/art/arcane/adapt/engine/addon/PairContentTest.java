package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.AddonState;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.PairText;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.text.MessageBundle;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.mutation.signature.UnseenBlade;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.mutation.pair.PairRegistry;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pair content declared by addons through the public registrar: a pair of a built-in and an addon skill, a pair of
 * two addon skills, a claim on a built-in pair, and the refusals of a second holder and of a duplicate claim.
 */
class PairContentTest {
  static final Id SWORDS = Id.adapt("swords");
  static final Id STEALTH = Id.adapt("stealth");
  static final Id SMITHING = Id.of("forge", "smithing");
  static final Id WEAVING = Id.of("loom", "weaving");
  static final Id CARVING = Id.of("rival", "carving");
  static final MutationSlot SWORDS_SMITHING = slot(SWORDS, SMITHING, Recipe.CONVERGENCE);
  static final MutationSlot SWORDS_SMITHING_GRAFT = slot(SWORDS, SMITHING, Recipe.GRAFT);
  static final MutationSlot SMITHING_WEAVING = slot(SMITHING, WEAVING, Recipe.CONVERGENCE);
  static final MutationSlot SMITHING_WEAVING_ECHO = slot(SMITHING, WEAVING, Recipe.ECHO);
  static final MutationSlot SWORDS_STEALTH_ECHO = slot(SWORDS, STEALTH, Recipe.ECHO);

  @TempDir
  Path data;

  private TestAdapt adapt;

  @AfterEach
  void stop() {
    if (adapt != null) {
      adapt.close();
      assertThat(adapt.handlerFailures()).isEmpty();
    }
  }

  @Test
  void aBuiltInAndAnAddonSkillPairTakesTheAddonsSignatureTextAndName() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), List.of(SkillSwords.class, SkillStealth.class));

    AddonHandle forge = AdaptApi.addons().install(new Forge());

    assertThat(forge.state()).isEqualTo(AddonState.ACTIVE);
    PairRegistry pairs = pairs();
    assertThat(pairs.signature(SWORDS_SMITHING).orElseThrow().owner()).isEqualTo("forge");
    PairRegistry.Claim<PairText> text = pairs.text(SWORDS_SMITHING_GRAFT).orElseThrow();
    assertThat(text.owner()).isEqualTo("forge");
    assertThat(text.value().description().resolve()).isEqualTo("Every swing tempers the blade.");
    assertThat(pairs.name(SWORDS_SMITHING.pair()).orElseThrow().value().resolve()).isEqualTo("Bladesmith");
    assertThat(pairs.text(SWORDS_STEALTH_ECHO).orElseThrow().owner()).isEqualTo("forge");
    assertThat(pairs.signature(slot(SWORDS, STEALTH, Recipe.CONVERGENCE)).orElseThrow().owner()).isEqualTo("adapt");

    Adaptation<?> signature = AdaptApi.content().mutated(SWORDS_SMITHING).orElseThrow();
    assertThat(signature).isInstanceOf(Anvil.class);
    assertThat(signature.getId()).isEqualTo(Id.adapt("mutated/swords/forge/smithing/signature/convergence"));
    assertThat(((Signature<?>) signature).getSlot()).isEqualTo(SWORDS_SMITHING);
    assertThat(AdaptApi.content().owner(SMITHING)).contains("forge");
    Adaptation<?> generated = AdaptApi.content().mutated(SWORDS_SMITHING_GRAFT).orElseThrow();
    assertThat(generated).isNotInstanceOf(Signature.class);
    assertThat(generated.getId()).isEqualTo(SWORDS_SMITHING.pair().child("graft"));
    assertThat(AdaptApi.content().mutated(slot(SWORDS, STEALTH, Recipe.CONVERGENCE))).get()
        .isInstanceOf(UnseenBlade.class);
  }

  @Test
  void twoAddonSkillsPairOnceBothAreRegisteredAndUninstallWithdrawsTheClaims() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), List.of(SkillSwords.class));

    AddonHandle loom = AdaptApi.addons().install(new Loom());

    assertThat(loom.state()).isEqualTo(AddonState.ACTIVE);
    assertThat(pairs().signature(SMITHING_WEAVING).orElseThrow().owner()).isEqualTo("loom");
    assertThat(AdaptApi.content().mutated(SMITHING_WEAVING)).isEmpty();

    AddonHandle forge = AdaptApi.addons().install(new Forge());

    Adaptation<?> shuttle = AdaptApi.content().mutated(SMITHING_WEAVING).orElseThrow();
    assertThat(shuttle).isInstanceOf(Shuttle.class);
    assertThat(shuttle.getId()).isEqualTo(Id.of("forge", "mutated/smithing/loom/weaving/signature/convergence"));
    assertThat(pairs().text(SMITHING_WEAVING_ECHO).orElseThrow().value().description().resolve())
        .isEqualTo("Each thread you pull rings the anvil.");
    assertThat(pairs().name(SMITHING_WEAVING.pair()).orElseThrow().owner()).isEqualTo("loom");

    forge.uninstall();

    assertThat(AdaptApi.content().mutated(SMITHING_WEAVING)).isEmpty();
    assertThat(pairs().signature(SMITHING_WEAVING)).isPresent();
    assertThat(pairs().signature(SWORDS_SMITHING)).isEmpty();
    long withLoom = pairs().revision();

    loom.uninstall();

    assertThat(pairs().revision()).isGreaterThan(withLoom);
    assertThat(pairs().signature(SMITHING_WEAVING)).isEmpty();
    assertThat(pairs().name(SMITHING_WEAVING.pair())).isEmpty();
  }

  @Test
  void aSecondProviderOfAClaimIsRefusedWholeAndTheRefusalNamesBoth() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), List.of(SkillSwords.class));
    AdaptApi.addons().install(new Forge());

    AddonHandle rival = AdaptApi.addons().install(new Rival());

    assertThat(rival.state()).isEqualTo(AddonState.REJECTED);
    assertThat(rival.rejection()).get().asString().contains("forge").contains("rival")
        .contains(SWORDS_SMITHING.toString());
    assertThat(AdaptApi.content().skill(CARVING)).isEmpty();
    assertThat(pairs().signature(SWORDS_SMITHING).orElseThrow().owner()).isEqualTo("forge");
  }

  @Test
  void aDuplicateClaimInOneAddonRefusesTheAddon() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build(), List.of(SkillSwords.class));

    AddonHandle twice = AdaptApi.addons().install(new Twice());

    assertThat(twice.state()).isEqualTo(AddonState.REJECTED);
    assertThat(twice.rejection()).get().asString().contains("twice").contains(SWORDS_STEALTH_ECHO.toString());
    assertThat(pairs().text(SWORDS_STEALTH_ECHO)).isEmpty();
  }

  private PairRegistry pairs() {
    return adapt.core().getAdaptServer().getSkillRegistry().getPairRegistry();
  }

  static MutationSlot slot(Id lead, Id partner, Recipe recipe) {
    return new MutationSlot(new SkillPair(lead, partner), recipe);
  }

  /** Smithing, with a signature and text on its pair with Swords, a name for it, and text on a built-in pair. */
  static final class Forge implements AdaptAddon {
    @Override
    public String namespace() {
      return "forge";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.messages(MessageBundle.builder()
          .message("skill.smithing.name", "Smithing")
          .message("skill.smithing.description", "Shape metal.")
          .message("adaptation.smithing.temper.name", "Temper")
          .message("adaptation.smithing.temper.description", "Harden a blade.")
          .message("pair.adapt.swords.forge.smithing.convergence.name", "Anvil Echo")
          .message("pair.adapt.swords.forge.smithing.convergence.description", "A hit rings the anvil.")
          .message("pair.adapt.swords.forge.smithing.graft", "Every swing tempers the blade.")
          .message("pair.adapt.swords.adapt.stealth.echo", "A quiet cut, forged.")
          .message("mutation.bladesmith", "Bladesmith")
          .build());
      registrar.skill(SMITHING, () -> new PlainSkill(SMITHING, "temper"));
      registrar.signature(SWORDS, SMITHING, Recipe.CONVERGENCE, Anvil::new);
      registrar.pairText(SWORDS, SMITHING, Recipe.GRAFT,
          PairText.of(Text.key("forge.pair.adapt.swords.forge.smithing.graft")));
      registrar.pairName(SWORDS, SMITHING, Text.key("forge.mutation.bladesmith"));
      registrar.pairText(SWORDS, STEALTH, Recipe.ECHO,
          PairText.of(Text.key("forge.pair.adapt.swords.adapt.stealth.echo")));
    }
  }

  /** Weaving, with the signature, text and name of its pair with Smithing, another addon's skill. */
  static final class Loom implements AdaptAddon {
    @Override
    public String namespace() {
      return "loom";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.messages(MessageBundle.builder()
          .message("skill.weaving.name", "Weaving")
          .message("skill.weaving.description", "Make cloth.")
          .message("adaptation.weaving.weft.name", "Weft")
          .message("adaptation.weaving.weft.description", "Pull a thread.")
          .message("pair.forge.smithing.loom.weaving.convergence.name", "Shuttle")
          .message("pair.forge.smithing.loom.weaving.convergence.description", "The shuttle strikes.")
          .message("pair.forge.smithing.loom.weaving.echo", "Each thread you pull rings the anvil.")
          .message("mutation.loomsmith", "Loomsmith")
          .build());
      registrar.skill(WEAVING, () -> new PlainSkill(WEAVING, "weft"));
      registrar.signature(SMITHING, WEAVING, Recipe.CONVERGENCE, Shuttle::new);
      registrar.pairText(SMITHING, WEAVING, Recipe.ECHO,
          PairText.recentState(Text.key("loom.pair.forge.smithing.loom.weaving.echo"), 4));
      registrar.pairName(SMITHING, WEAVING, Text.key("loom.mutation.loomsmith"));
    }
  }

  /** A skill of its own and a signature on the slot Forge holds. */
  static final class Rival implements AdaptAddon {
    @Override
    public String namespace() {
      return "rival";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.skill(CARVING, () -> new PlainSkill(CARVING, "notch"));
      registrar.signature(SWORDS, SMITHING, Recipe.CONVERGENCE, Anvil::new);
    }
  }

  /** The same slot's text twice. */
  static final class Twice implements AdaptAddon {
    @Override
    public String namespace() {
      return "twice";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.pairText(SWORDS, STEALTH, Recipe.ECHO, PairText.of(Text.literal("One.")));
      registrar.pairText(SWORDS, STEALTH, Recipe.ECHO, PairText.of(Text.literal("Two.")));
    }
  }

  public static final class PlainSkill extends SimpleSkill<PlainSkill.Config> {
    public PlainSkill(Id id, String adaptation) {
      super(id);
      registerConfiguration(Config.class);
      setColor(Rgb.of(0x8A6D3B));
      setIcon(Id.minecraft("anvil"));
      Id child = Id.of(id.namespace(), id.path() + "/" + adaptation);
      registerAdaptation(child, () -> new Plain(child));
    }

    public static final class Config extends SkillConfig {
    }
  }

  public static final class Plain extends SimpleAdaptation<AdaptationConfig> {
    public Plain(Id id) {
      super(id);
      registerConfiguration(AdaptationConfig.class);
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }

  public static final class Anvil extends Signature<Signature.Config> {
    public Anvil(MutationSlot slot) {
      super(slot);
      registerConfiguration(Signature.Config.class);
    }

    @Override
    public Outcome run(BlockUse use) {
      return Outcome.INSTANT;
    }
  }

  public static final class Shuttle extends Signature<Signature.Config> {
    public Shuttle(MutationSlot slot) {
      super(slot);
      registerConfiguration(Signature.Config.class);
    }

    @Override
    public Outcome run(BlockUse use) {
      return Outcome.INSTANT;
    }
  }
}
