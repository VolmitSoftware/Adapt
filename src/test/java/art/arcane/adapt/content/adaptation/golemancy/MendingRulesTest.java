package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemMend;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class MendingRulesTest {
  private static final List<Double> BONUS = List.of(0.2D, 0.4D, 0.6D, 0.8D, 1.0D);
  private static final double NEAR = 3D;
  private static final double FAR = 40D;

  @Test
  void itemHealsAddTheLevelBonusToTheirBaseAmount() {
    assertThat(MendingRules.healAmount(4D, BONUS, 1)).isCloseTo(4.8D, offset(1e-9));
    assertThat(MendingRules.healAmount(2D, BONUS, 3)).isCloseTo(3.2D, offset(1e-9));
    assertThat(MendingRules.healAmount(4D, BONUS, 5)).isCloseTo(8D, offset(1e-9));
    assertThat(MendingRules.healAmount(4D, BONUS, 9)).isCloseTo(8D, offset(1e-9));
    assertThat(MendingRules.healAmount(4D, BONUS, 0)).isZero();
    assertThat(MendingRules.healAmount(-1D, BONUS, 3)).isZero();
    assertThat(MendingRules.healAmount(2D, List.of(-0.5D), 1)).isEqualTo(2D);
  }

  @Test
  void theIronIngotBonusIsTheLevelBonusOfTheVanillaTwentyFive() {
    assertThat(MendingRules.ingotBonus(BONUS, 1)).isCloseTo(5D, offset(1e-9));
    assertThat(MendingRules.ingotBonus(BONUS, 3)).isCloseTo(15D, offset(1e-9));
    assertThat(MendingRules.ingotBonus(BONUS, 5)).isCloseTo(25D, offset(1e-9));
    assertThat(MendingRules.ingotBonus(BONUS, 0)).isZero();
  }

  @Test
  void eachRemedyHealsItsOwnBody() {
    GolemMending.Config config = new GolemMending.Config();

    assertThat(MendingRules.Remedy.of(Material.COPPER_INGOT)).contains(MendingRules.Remedy.COPPER_INGOT);
    assertThat(MendingRules.Remedy.of(Material.SNOW_BLOCK)).contains(MendingRules.Remedy.SNOW_BLOCK);
    assertThat(MendingRules.Remedy.of(Material.BONE_MEAL)).contains(MendingRules.Remedy.BONE_MEAL);
    assertThat(MendingRules.Remedy.of(Material.IRON_INGOT)).isEmpty();
    assertThat(MendingRules.Remedy.of(Material.SNOWBALL)).isEmpty();
    assertThat(MendingRules.Remedy.COPPER_INGOT.body()).isEqualTo(GolemBody.COPPER);
    assertThat(MendingRules.Remedy.SNOW_BLOCK.body()).isEqualTo(GolemBody.FROST);
    assertThat(MendingRules.Remedy.BONE_MEAL.body()).isNull();
    assertThat(MendingRules.Remedy.COPPER_INGOT.base(config)).isEqualTo(4D);
    assertThat(MendingRules.Remedy.SNOW_BLOCK.base(config)).isEqualTo(2D);
    assertThat(MendingRules.Remedy.BONE_MEAL.base(config)).isEqualTo(2D);
  }

  @Test
  void regenNeedsItsLevelAndTheOwnerNearby() {
    GolemMending.Config config = new GolemMending.Config();

    assertThat(MendingRules.regen(config, 1, senses(NEAR, NEAR, true))).isZero();
    assertThat(MendingRules.regen(config, 2, senses(NEAR, FAR, false))).isEqualTo(0.5D);
    assertThat(MendingRules.regen(config, 2, senses(16D, FAR, false))).isEqualTo(0.5D);
    assertThat(MendingRules.regen(config, 2, senses(16.01D, FAR, false))).isZero();
    assertThat(MendingRules.regen(config, 2, senses(Double.NaN, NEAR, true))).isZero();
    assertThat(MendingRules.regen(config, 0, senses(NEAR, NEAR, true))).isZero();
  }

  @Test
  void homeRegenNeedsLevelThreeAndAnIdleGolemNearHome() {
    GolemMending.Config config = new GolemMending.Config();

    assertThat(MendingRules.regen(config, 2, senses(FAR, NEAR, true))).isZero();
    assertThat(MendingRules.regen(config, 3, senses(FAR, NEAR, true))).isEqualTo(1D);
    assertThat(MendingRules.regen(config, 3, senses(FAR, 4D, true))).isEqualTo(1D);
    assertThat(MendingRules.regen(config, 3, senses(FAR, 4.5D, true))).isZero();
    assertThat(MendingRules.regen(config, 3, senses(FAR, NEAR, false))).isZero();
    assertThat(MendingRules.regen(config, 3, senses(FAR, Double.NaN, true))).isZero();
  }

  @Test
  void ownerAndHomeRegenTakeTheLargerAmountNeverTheSum() {
    GolemMending.Config config = new GolemMending.Config();

    assertThat(MendingRules.regen(config, 5, senses(NEAR, NEAR, true))).isEqualTo(1D);
    assertThat(MendingRules.regen(config, 5, senses(NEAR, NEAR, false))).isEqualTo(0.5D);
    config.ownerRegenAmount = 3D;
    assertThat(MendingRules.regen(config, 5, senses(NEAR, NEAR, true))).isEqualTo(3D);
  }

  @Test
  void aGolemShattersOnlyWhenValidatedWithItsOwnerOnlineAtTheShatterLevel() {
    assertThat(MendingRules.shatters(true, true, 4, 4)).isTrue();
    assertThat(MendingRules.shatters(true, true, 5, 4)).isTrue();
    assertThat(MendingRules.shatters(true, true, 3, 4)).isFalse();
    assertThat(MendingRules.shatters(true, false, 5, 4)).isFalse();
    assertThat(MendingRules.shatters(false, true, 5, 4)).isFalse();
    assertThat(MendingRules.shatters(true, true, 0, 0)).isFalse();
  }

  @Test
  void unlocksNeedAtLeastLevelOne() {
    assertThat(GolemancyRules.unlocked(5, 5)).isTrue();
    assertThat(GolemancyRules.unlocked(4, 5)).isFalse();
    assertThat(GolemancyRules.unlocked(0, 0)).isFalse();
  }

  @Test
  void rebuildingReachesThreeBlocksFromTheEyesToTheBody() {
    BoundingBox body = new BoundingBox(0D, 64D, 0D, 1.4D, 66.7D, 1.4D);

    assertThat(MendingRules.withinReach(new Vector(0.7D, 65.6D, 4.3D), body)).isTrue();
    assertThat(MendingRules.withinReach(new Vector(0.7D, 65.6D, 4.5D), body)).isFalse();
    assertThat(MendingRules.withinReach(new Vector(0.7D, 65.6D, 0.7D), body)).isTrue();
    assertThat(MendingRules.withinReach(new Vector(3.4D, 69.7D, 0.7D), body)).isFalse();
  }

  @Test
  void aReadyRebuildPassesEveryCheck() {
    assertThat(MendingRules.rebuildVerdict(ready().build())).isEqualTo(RebuildVerdict.READY);
  }

  @Test
  void theRebuildDecisionTableChecksInOrder() {
    assertThat(MendingRules.rebuildVerdict(ready().unreadable().build())).isEqualTo(RebuildVerdict.UNREADABLE);
    assertThat(MendingRules.rebuildVerdict(ready().notOwner().build())).isEqualTo(RebuildVerdict.NOT_OWNER);
    assertThat(MendingRules.rebuildVerdict(ready().notOwner().withStatus(null).build())).isEqualTo(RebuildVerdict.NOT_OWNER);
    assertThat(MendingRules.rebuildVerdict(ready().rosterUnknown().build())).isEqualTo(RebuildVerdict.UNAVAILABLE);
    assertThat(MendingRules.rebuildVerdict(ready().atLevel(3).build())).isEqualTo(RebuildVerdict.LOCKED);
    assertThat(MendingRules.rebuildVerdict(ready().blocked(GolemRegistry.BindBlock.ALREADY_BOUND).build())).isEqualTo(RebuildVerdict.ALREADY_BOUND);
    assertThat(MendingRules.rebuildVerdict(ready().blocked(GolemRegistry.BindBlock.BODY_MISMATCH).build())).isEqualTo(RebuildVerdict.BODY_MISMATCH);
    assertThat(MendingRules.rebuildVerdict(ready().villageGolem(false).build())).isEqualTo(RebuildVerdict.VILLAGE_GOLEM);
    assertThat(MendingRules.rebuildVerdict(ready().villageGolem(true).build())).isEqualTo(RebuildVerdict.READY);
    assertThat(MendingRules.rebuildVerdict(ready().notBuilder().build())).isEqualTo(RebuildVerdict.NOT_BUILDER);
    assertThat(MendingRules.rebuildVerdict(ready().bodyLocked().build())).isEqualTo(RebuildVerdict.BODY_LOCKED);
    assertThat(MendingRules.rebuildVerdict(ready().blocked(GolemRegistry.BindBlock.CORE_NOT_ALLOWED).build())).isEqualTo(RebuildVerdict.CORE_NOT_ALLOWED);
    assertThat(MendingRules.rebuildVerdict(ready().protectedArea().build())).isEqualTo(RebuildVerdict.PROTECTED);
    assertThat(MendingRules.rebuildVerdict(ready().blocked(GolemRegistry.BindBlock.OWNER_DATA_UNAVAILABLE).build())).isEqualTo(RebuildVerdict.UNAVAILABLE);
    assertThat(MendingRules.rebuildVerdict(ready().blocked(GolemRegistry.BindBlock.CHUNK_CAP).build())).isEqualTo(RebuildVerdict.CHUNK_CAP);
    assertThat(MendingRules.rebuildVerdict(ready().blocked(GolemRegistry.BindBlock.PLAYER_CAP).build())).isEqualTo(RebuildVerdict.READY);
    assertThat(MendingRules.rebuildVerdict(ready().notBuilder().protectedArea().build())).isEqualTo(RebuildVerdict.NOT_BUILDER);
  }

  @Test
  void aCoreWhoseEntryIsNotShatteredVoidsBeforeAnyOtherCheckButNeverForAnotherPlayer() {
    List<GolemRoster.Status> notShattered = new ArrayList<>();
    notShattered.add(GolemRoster.Status.ACTIVE);
    notShattered.add(GolemRoster.Status.PACKED);
    notShattered.add(GolemRoster.Status.MISSING);
    notShattered.add(null);
    for (GolemRoster.Status status : notShattered) {
      assertThat(MendingRules.rebuildVerdict(ready().withStatus(status).build())).as("status %s", status).isEqualTo(RebuildVerdict.VOID);
      assertThat(MendingRules.rebuildVerdict(ready().withStatus(status).atLevel(1).protectedArea().blocked(GolemRegistry.BindBlock.ALREADY_BOUND).build()))
          .as("status %s with other failures", status).isEqualTo(RebuildVerdict.VOID);
      assertThat(MendingRules.rebuildVerdict(ready().withStatus(status).notOwner().build())).as("status %s for another player", status)
          .isEqualTo(RebuildVerdict.NOT_OWNER);
    }
  }

  @Test
  void aCoreWhoseStateDoesNotMatchTheEntryDigestVoids() {
    assertThat(MendingRules.rebuildVerdict(ready().staleCopy().build())).isEqualTo(RebuildVerdict.VOID);
    assertThat(MendingRules.rebuildVerdict(ready().staleCopy().atLevel(1).protectedArea().build())).isEqualTo(RebuildVerdict.VOID);
    assertThat(MendingRules.rebuildVerdict(ready().staleCopy().notOwner().build())).isEqualTo(RebuildVerdict.NOT_OWNER);
  }

  private static GolemMend.RegenSenses senses(double ownerDistance, double homeDistance, boolean idle) {
    return new GolemMend.RegenSenses(ownerDistance, homeDistance, idle);
  }

  private static Check ready() {
    return new Check();
  }

  private static final class Check {
    private boolean readable = true;
    private boolean ownsItem = true;
    private boolean rosterKnown = true;
    private GolemRoster.Status status = GolemRoster.Status.SHATTERED;
    private boolean sealed = true;
    private int level = 4;
    private GolemRegistry.BindBlock block;
    private boolean playerCreated = true;
    private boolean allowVillageGolems;
    private boolean builderExclusive;
    private boolean bodyUnlocked = true;
    private boolean canInteract = true;

    private Check unreadable() {
      readable = false;
      return this;
    }

    private Check notOwner() {
      ownsItem = false;
      return this;
    }

    private Check rosterUnknown() {
      rosterKnown = false;
      return this;
    }

    private Check withStatus(GolemRoster.Status next) {
      status = next;
      return this;
    }

    private Check staleCopy() {
      sealed = false;
      return this;
    }

    private Check atLevel(int next) {
      level = next;
      return this;
    }

    private Check blocked(GolemRegistry.BindBlock next) {
      block = next;
      return this;
    }

    private Check villageGolem(boolean allowed) {
      playerCreated = false;
      allowVillageGolems = allowed;
      return this;
    }

    private Check notBuilder() {
      builderExclusive = true;
      return this;
    }

    private Check bodyLocked() {
      bodyUnlocked = false;
      return this;
    }

    private Check protectedArea() {
      canInteract = false;
      return this;
    }

    private RebuildCheck build() {
      return new RebuildCheck(readable, ownsItem, rosterKnown, status, sealed, level, 4, block, playerCreated, allowVillageGolems, builderExclusive,
          bodyUnlocked, canInteract);
    }
  }
}
