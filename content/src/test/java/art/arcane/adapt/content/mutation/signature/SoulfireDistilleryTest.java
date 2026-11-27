package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.engine.data.WorldDataPort;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.item.Potions;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Brewing leading Nether replaces Convergence with Soulfire Distillery: heated stands brew one level higher. */
class SoulfireDistilleryTest {
  private static final Id STRENGTH = Id.minecraft("strength");
  private static final Id STRONG_STRENGTH = Id.minecraft("strong_strength");
  private static final Id LUCK = Id.minecraft("luck");

  @TempDir
  Path data;

  private final Map<BlockRef, UUID> owners = new HashMap<>();
  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private BlockRef stand;

  @BeforeEach
  void start() {
    AdaptPorts.install(WorldDataPort.class, new OwnerStore());
    platform = TestPlatform.builder(data).build();
    platform.host().potion(STRENGTH, EffectInstance.of(STRENGTH, 3600, 0));
    platform.host().potion(STRONG_STRENGTH, EffectInstance.of(STRENGTH, 1800, 1));
    player = platform.player("Alchemist", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillBrewing.class, SkillNether.class));
    AdaptPlayer profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    platform.world().set(0, 60, 0, "soul_campfire");
    stand = platform.world().set(0, 61, 0, "brewing_stand").blockAt(0, 61, 0);
    owners.put(stand, player.uuid());
  }

  @AfterEach
  void stop() {
    adapt.close();
    AdaptPorts.uninstall(WorldDataPort.class);
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aHeatedStandBrewsEveryPotionOneLevelHigherForTwoThirdsOfTheTime() {
    kit.mutate("brewing", "nether");
    kit.learn("brewing", "nether", Recipe.CONVERGENCE, 3);
    assertThat(kit.adaptation("brewing", "nether", Recipe.CONVERGENCE).getSignature()).isInstanceOf(SoulfireDistillery.class);

    List<ItemRef> results = brew(STRENGTH, STRENGTH, STRENGTH);

    for (ItemRef potion : results) {
      assertThat(effect(potion)).isEqualTo(EffectInstance.of(STRENGTH, 2400, 1));
    }
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void levelOneRaisesOnePotionOfTheBrew() {
    kit.mutate("brewing", "nether");
    kit.learn("brewing", "nether", Recipe.CONVERGENCE, 1);

    List<ItemRef> results = brew(STRENGTH, STRENGTH, STRENGTH);

    assertThat(effect(results.get(0)).amplifier()).isEqualTo(1);
    assertThat(results.get(1).potionContents().orElseThrow().customEffects()).isEmpty();
    assertThat(results.get(2).potionContents().orElseThrow().customEffects()).isEmpty();
  }

  @Test
  void rebrewingARaisedPotionNeverPassesThreeAndSpendsNothing() {
    kit.mutate("brewing", "nether");
    kit.learn("brewing", "nether", Recipe.CONVERGENCE, 3);
    ItemRef raised = Potions.vanilla(Potions.Type.REGULAR, STRONG_STRENGTH);
    assertThat(SoulfireDistillery.boost(raised, 0.6667D)).isTrue();
    assertThat(effect(raised).amplifier()).isEqualTo(2);

    List<ItemRef> again = new ArrayList<>(List.of(raised));
    adapt.post(new BrewEvent(stand, FakeItem.of("glowstone_dust"), List.of(raised), again, 10, 0));

    assertThat(effect(again.get(0))).isEqualTo(EffectInstance.of(STRENGTH, 1200, 2));
    assertThat(effect(brew(STRENGTH).get(0)).amplifier()).isEqualTo(1);
  }

  @Test
  void aRaisedPotionIsNeverRaisedAgainByALaterBrewStep() {
    ItemRef potion = Potions.vanilla(Potions.Type.REGULAR, STRENGTH);
    assertThat(SoulfireDistillery.boost(potion, 0.6667D)).isTrue();

    assertThat(SoulfireDistillery.boost(potion, 0.6667D)).isFalse();
    assertThat(effect(potion)).isEqualTo(EffectInstance.of(STRENGTH, 2400, 1));
  }

  @Test
  void aPotionWithABaseAndExtraEffectsKeepsBoth() {
    FakeItem potion = FakeItem.of("potion");
    EffectInstance luck = EffectInstance.of(LUCK, 600, 0);
    potion.setPotionContents(PotionContents.of(STRENGTH).withCustomEffects(List.of(luck)));

    assertThat(SoulfireDistillery.boost(potion, 0.6667D)).isTrue();

    assertThat(potion.potionContents().orElseThrow().customEffects())
        .containsExactly(EffectInstance.of(STRENGTH, 2400, 1), luck);
  }

  @Test
  void theNextBrewWaitsForTheCooldown() {
    kit.mutate("brewing", "nether");
    kit.learn("brewing", "nether", Recipe.CONVERGENCE, 3);
    brew(STRENGTH);

    assertThat(brew(STRENGTH).get(0).potionContents().orElseThrow().customEffects()).isEmpty();
  }

  @Test
  void aColdStandBrewsAsUsual() {
    kit.mutate("brewing", "nether");
    kit.learn("brewing", "nether", Recipe.CONVERGENCE, 3);
    platform.world().set(0, 60, 0, "stone");

    assertThat(brew(STRENGTH).get(0).potionContents().orElseThrow().customEffects()).isEmpty();
  }

  @Test
  void netherLeadingBrewingOrNoMutationBrewsAsUsual() {
    assertThat(brew(STRENGTH).get(0).potionContents().orElseThrow().customEffects()).isEmpty();
    kit.mutate("nether", "brewing");
    kit.learn("nether", "brewing", Recipe.CONVERGENCE, 3);

    assertThat(brew(STRENGTH).get(0).potionContents().orElseThrow().customEffects()).isEmpty();
  }

  private List<ItemRef> brew(Id... potions) {
    List<ItemRef> results = new ArrayList<>();
    for (Id potion : potions) {
      results.add(Potions.vanilla(Potions.Type.REGULAR, potion));
    }
    BrewEvent event = adapt.post(new BrewEvent(stand, FakeItem.of("blaze_powder"), List.of(FakeItem.empty()), results, 10, 0));
    return event.results();
  }

  private static EffectInstance effect(ItemRef potion) {
    PotionContents contents = potion.potionContents().orElseThrow();
    assertThat(contents.customEffects()).hasSize(1);
    return contents.customEffects().get(0);
  }

  private final class OwnerStore implements WorldDataPort {
    @Override
    public double reportEarnings(BlockRef block) {
      return 1D;
    }

    @Override
    public double getEarningsMultiplier(BlockRef block) {
      return 1D;
    }

    @Override
    public Placement placement(BlockRef block) {
      return null;
    }

    @Override
    public void setPlacement(BlockRef block, Placement placement) {
    }

    @Override
    public void clearPlacement(BlockRef block) {
    }

    @Override
    public UUID blockOwner(BlockRef block) {
      return owners.get(block);
    }

    @Override
    public void setBlockOwner(BlockRef block, UUID owner) {
      if (owner == null) {
        owners.remove(block);
      } else {
        owners.put(block, owner);
      }
    }
  }
}
