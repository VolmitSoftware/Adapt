package art.arcane.adapt.engine.mutation.kit;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.BlockKey;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** A skill of another addon with its own kit mutates with a built-in skill in either order, as a built-in pair does. */
class AddonKitMutationTest {
  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, ForgeSkill.class));
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theAddonKitBuildsItsBlocksAndUpgradeOnItsOwnSkill() {
    SimpleSkill<?> forge = (SimpleSkill<?>) adapt.core().getAdaptServer().getSkillRegistry().getSkill(ForgeSkill.ID);

    assertThat(Kits.of(forge)).isSameAs(forge.getMutationKit().orElseThrow());
    assertThat(SkillHost.of(forge).getBlocks()).extracting(MutationBlock::getKey)
        .extracting(BlockKey::id).extracting(Id::toString)
        .containsExactlyInAnyOrder("kittest:forge/block/trigger", "kittest:forge/block/strike_fight",
            "kittest:forge/block/strike_work", "kittest:forge/block/boon", "kittest:forge/block/fuel",
            "kittest:forge/block/state");
    assertThat(SkillHost.of(forge).getUpgrades()).hasExactlyElementsOfTypes(Anvil.class, Tongs.class);
    assertThat(((Anvil) SkillHost.of(forge).getUpgrades().getFirst()).getSources()).containsExactly(Hammer.ID);
  }

  @Test
  void anUpgradeLendsALevelOnlyToTheOriginalsItFuses() {
    Bench bench = bench("Farrier");
    bench.kit().mutate("kittest:forge", "swords");
    Skill<?> skill = adapt.core().getAdaptServer().getSkillRegistry().getSkill(ForgeSkill.ID);
    SkillHost forge = SkillHost.of(skill);
    Adaptation<?> hammer = skill.getAdaptations().stream()
        .filter((Adaptation<?> adaptation) -> adaptation.getId().equals(Hammer.ID)).findFirst().orElseThrow();
    Adaptation<?> anvil = upgrade(forge, Anvil.ID);
    Adaptation<?> tongs = upgrade(forge, Tongs.ID);

    bench.profile().getData().getSkillLine("kittest:forge").setAdaptation(tongs, 1);
    assertThat(AdaptationHost.of(hammer).level(bench.profile())).as("an upgrade that only replaces lends nothing")
        .isZero();

    bench.profile().getData().getSkillLine("kittest:forge").setAdaptation(anvil, 1);
    assertThat(AdaptationHost.of(hammer).level(bench.profile())).as("the fusing upgrade lends a level").isPositive();
  }

  private static Adaptation<?> upgrade(SkillHost skill, Id id) {
    return skill.getUpgrades().stream().filter((Adaptation<?> upgrade) -> upgrade.getId().equals(id)).findFirst()
        .orElseThrow();
  }

  @Test
  void theAddonSkillLeadsAMutationWithABuiltInAndItsBlocksFire() {
    Bench bench = bench("Smith");
    MutatedSkill mutated = bench.kit().mutated("kittest:forge", "swords");
    bench.kit().mutate("kittest:forge", "swords");

    assertFiveAndUpgrades(mutated);
    bench.kit().learn("kittest:forge", "swords", Recipe.ECHO, 1);
    critWithASword(bench);

    assertThat(strike(StrikeForm.FIGHT).uses).as("the forge Strike set off by the swords Trigger").isNotEmpty();
  }

  @Test
  void theAddonSkillJoinsABuiltInLeadAndItsBlocksFire() {
    Bench bench = bench("Squire");
    bench.kit().mutate("swords", "kittest:forge");
    MutatedSkill mutated = bench.kit().mutated("swords", "kittest:forge");

    assertFiveAndUpgrades(mutated);
    bench.kit().learn("swords", "kittest:forge", Recipe.GRAFT, 1);
    critWithASword(bench);

    assertThat(strike(StrikeForm.FIGHT).uses).as("the forge Strike set off by the swords Trigger").isNotEmpty();
  }

  private void assertFiveAndUpgrades(MutatedSkill mutated) {
    assertThat(mutated.getMutationAdaptations()).extracting(MutationAdaptation::getRecipe)
        .containsExactly(Recipe.values());
    Adaptation<?> anvil = SkillHost.of(adapt.core().getAdaptServer().getSkillRegistry().getSkill(ForgeSkill.ID))
        .getUpgrades().getFirst();
    assertThat(mutated.getAdaptations()).contains(anvil);
    assertThat(mutated.getAdaptations()).containsAll(SkillHost.of(adapt.core().getAdaptServer().getSkillRegistry()
        .getSkill(SkillSwords.ID)).getUpgrades());
  }

  private record Bench(FakePlayer player, AdaptPlayer profile, art.arcane.adapt.testkit.MutationKit kit) {
  }

  private Bench bench(String name) {
    FakePlayer player = platform.player(name, 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    AdaptPlayer profile = adapt.join(player);
    return new Bench(player, profile, new art.arcane.adapt.testkit.MutationKit(adapt, profile));
  }

  private void critWithASword(Bench bench) {
    FakeLiving zombie = art.arcane.adapt.testkit.MutationKit.zombie(platform.world(), 1.5D, 61D, 0.5D);
    bench.kit().hit(bench.player(), zombie, 4D, true);
  }

  private ForgeStrike strike(StrikeForm form) {
    for (MutationBlock<?> block : SkillHost.of(adapt.core().getAdaptServer().getSkillRegistry().getSkill(ForgeSkill.ID))
        .getBlocks()) {
      if (block instanceof ForgeStrike strike && strike.form == form) {
        return strike;
      }
    }
    throw new AssertionError("No forge " + form + " Strike");
  }

  public static final class ForgeSkill extends SimpleSkill<ForgeSkill.Config> {
    public static final Id ID = Id.of("kittest", "forge");

    public ForgeSkill() {
      super(ID);
      registerConfiguration(Config.class);
      setIcon(Id.minecraft("anvil"));
      registerAdaptation(Hammer.ID, Hammer::new);
      registerMutationKit(MutationKit.builder()
          .trigger(TriggerKind.FIGHT, ForgeTrigger::new)
          .strikes(() -> new ForgeStrike(StrikeForm.FIGHT), () -> new ForgeStrike(StrikeForm.WORK))
          .boon(ForgeBoon::new)
          .fuel(ForgeFuel::new)
          .state(ForgeState::new)
          .upgrade(Anvil.ID, Anvil::new, List.of(Hammer.ID), Set.of(Hammer.ID))
          .upgrade(Tongs.ID, Tongs::new, List.of(Hammer.ID), Set.of())
          .sounds(DefaultKit.SOUNDS)
          .tint(Rgb.of(0x884422))
          .build());
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    public static final class Config extends SkillConfig {
    }
  }

  static final class Hammer extends SimpleAdaptation<AdaptationConfig> {
    static final Id ID = Id.of("kittest", "forge/hammer");

    Hammer() {
      super(ID);
      registerConfiguration(AdaptationConfig.class);
      setLocalizationKey("pickaxe.chisel");
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }

  static final class Anvil extends SkillUpgrade<SkillUpgrade.Config> {
    static final Id ID = Id.of("kittest", "forge/upgrade/anvil");

    Anvil() {
      super(ID);
      registerConfiguration(SkillUpgrade.Config.class);
      setLocalizationKey("pickaxe.chisel");
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }

  static final class Tongs extends SkillUpgrade<SkillUpgrade.Config> {
    static final Id ID = Id.of("kittest", "forge/upgrade/tongs");

    Tongs() {
      super(ID);
      registerConfiguration(SkillUpgrade.Config.class);
      setLocalizationKey("pickaxe.chisel");
    }

    @Override
    public void addStats(int level, StatLines lines) {
    }
  }

  static final class ForgeTrigger extends TriggerBlock<MutationBlock.Config> {
    ForgeTrigger() {
      super(ForgeSkill.ID);
      registerConfiguration(MutationBlock.Config.class);
    }
  }

  static final class ForgeStrike extends StrikeBlock<MutationBlock.Config> {
    private final List<BlockUse> uses = new ArrayList<>();
    private final StrikeForm form;

    ForgeStrike(StrikeForm form) {
      super(ForgeSkill.ID, form);
      this.form = form;
      registerConfiguration(MutationBlock.Config.class);
    }

    @Override
    public Outcome strike(BlockUse use) {
      uses.add(use);
      return Outcome.INSTANT;
    }
  }

  static final class ForgeBoon extends BoonBlock<MutationBlock.Config> {
    ForgeBoon() {
      super(ForgeSkill.ID);
      registerConfiguration(MutationBlock.Config.class);
    }

    @Override
    public Outcome grant(BlockUse use) {
      return Outcome.INSTANT;
    }
  }

  static final class ForgeFuel extends FuelBlock<MutationBlock.Config> {
    ForgeFuel() {
      super(ForgeSkill.ID);
      registerConfiguration(MutationBlock.Config.class);
    }

    @Override
    public boolean canPay(BlockUse use) {
      return true;
    }

    @Override
    public boolean pay(BlockUse use) {
      return true;
    }
  }

  static final class ForgeState extends StateBlock<MutationBlock.Config> {
    ForgeState() {
      super(ForgeSkill.ID);
      registerConfiguration(MutationBlock.Config.class);
    }

    @Override
    public boolean holds(PlayerRef player) {
      return true;
    }
  }
}
