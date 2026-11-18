package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RancherRulesTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000025a1");
  private static final RancherRules.Levels LEVELS = new RancherRules.Levels(2, 3, 4);
  private static final Set<RancherRules.Action> ALL = EnumSet.allOf(RancherRules.Action.class);

  private final NodeRecord pasture = NodeRecord.builder(OWNER, NodeType.PASTURE, BlockFace.UP).build();
  private int beasts;

  @BeforeEach
  void installAnimals() {
    PeacefulAnimals.install();
  }

  @Test
  void keepDefaultsToTheConfiguredCountAndStaysBetweenTwoAndSixtyFour() {
    assertThat(RancherRules.keep(pasture, 8)).isEqualTo(8);
    assertThat(RancherRules.keep(pasture, 200)).isEqualTo(64);
    assertThat(RancherRules.keep(pasture, 0)).isEqualTo(2);
    assertThat(RancherRules.keep(data("keep", "12"), 8)).isEqualTo(12);
    assertThat(RancherRules.keep(data("keep", "99"), 8)).isEqualTo(64);
    assertThat(RancherRules.keep(data("keep", "nope"), 8)).isEqualTo(8);

    NodeRecord raised = RancherRules.adjustKeep(pasture, 1, 8);
    assertThat(RancherRules.keep(raised, 8)).isEqualTo(9);
    assertThat(RancherRules.keep(RancherRules.adjustKeep(data("keep", "64"), 1, 8), 8)).isEqualTo(64);
    assertThat(RancherRules.keep(RancherRules.adjustKeep(data("keep", "2"), -1, 8), 8)).isEqualTo(2);
  }

  @Test
  void hostileAnimalsSuchAsHoglinsAreNeverTended() {
    assertThat(RancherRules.parseSpecies(List.of("HOGLIN", "COW", "cow", "ZOMBIE", "nothing"))).containsExactly(Id.minecraft("cow"));
  }

  @Test
  void speciesCyclesFromAllAnimalsThroughTheTendedListAndBack() {
    List<Id> tended = List.of(Id.minecraft("cow"), Id.minecraft("sheep"), Id.minecraft("pig"));

    assertThat(RancherRules.species(pasture)).isEmpty();
    NodeRecord cow = RancherRules.cycleSpecies(pasture, tended, true);
    assertThat(RancherRules.species(cow)).contains(Id.minecraft("cow"));
    NodeRecord sheep = RancherRules.cycleSpecies(cow, tended, true);
    assertThat(RancherRules.species(sheep)).contains(Id.minecraft("sheep"));
    NodeRecord pig = RancherRules.cycleSpecies(sheep, tended, true);
    assertThat(RancherRules.species(RancherRules.cycleSpecies(pig, tended, true))).isEmpty();
    assertThat(RancherRules.species(RancherRules.cycleSpecies(pasture, tended, false))).contains(Id.minecraft("pig"));
    assertThat(RancherRules.species(RancherRules.cycleSpecies(cow, tended, false))).isEmpty();
    assertThat(RancherRules.species(RancherRules.cycleSpecies(data("species", "GOAT"), tended, true))).contains(Id.minecraft("cow"));
    assertThat(RancherRules.species(data("species", "NOT_AN_ANIMAL"))).isEmpty();
  }

  @Test
  void aNodeTendsItsChosenSpeciesOrEveryTendedSpecies() {
    Set<Id> tended = Set.of(Id.minecraft("cow"), Id.minecraft("sheep"));

    assertThat(RancherRules.tends(pasture, tended, Id.minecraft("cow"))).isTrue();
    assertThat(RancherRules.tends(pasture, tended, Id.minecraft("pig"))).isFalse();
    assertThat(RancherRules.tends(data("species", "SHEEP"), tended, Id.minecraft("sheep"))).isTrue();
    assertThat(RancherRules.tends(data("species", "SHEEP"), tended, Id.minecraft("cow"))).isFalse();
    assertThat(RancherRules.tends(data("species", "PIG"), tended, Id.minecraft("pig"))).isFalse();
  }

  @Test
  void breedingShearingAndMilkingStartOnAndCullingStartsOff() {
    assertThat(RancherRules.enabled(pasture, RancherRules.Action.BREED)).isTrue();
    assertThat(RancherRules.enabled(pasture, RancherRules.Action.SHEAR)).isTrue();
    assertThat(RancherRules.enabled(pasture, RancherRules.Action.MILK)).isTrue();
    assertThat(RancherRules.enabled(pasture, RancherRules.Action.CULL)).isFalse();

    NodeRecord culling = RancherRules.toggle(pasture, RancherRules.Action.CULL);
    NodeRecord noShear = RancherRules.toggle(culling, RancherRules.Action.SHEAR);
    assertThat(RancherRules.enabled(noShear, RancherRules.Action.CULL)).isTrue();
    assertThat(RancherRules.enabled(noShear, RancherRules.Action.SHEAR)).isFalse();
    assertThat(RancherRules.enabled(RancherRules.toggle(noShear, RancherRules.Action.SHEAR), RancherRules.Action.SHEAR)).isTrue();
    assertThat(noShear.data()).containsEntry("cull", "true").containsEntry("shear", "false");
  }

  @Test
  void eachActionUnlocksAtItsLevelAndOnlyIronGolemsCull() {
    assertThat(RancherRules.actions(allOn(), 1, GolemBody.IRON, LEVELS)).containsExactly(RancherRules.Action.BREED);
    assertThat(RancherRules.actions(allOn(), 2, GolemBody.COPPER, LEVELS)).containsExactly(RancherRules.Action.BREED, RancherRules.Action.SHEAR);
    assertThat(RancherRules.actions(allOn(), 4, GolemBody.COPPER, LEVELS))
        .containsExactly(RancherRules.Action.BREED, RancherRules.Action.SHEAR, RancherRules.Action.MILK);
    assertThat(RancherRules.actions(allOn(), 4, GolemBody.IRON, LEVELS)).containsExactlyElementsOf(ALL);
    assertThat(RancherRules.actions(pasture, 5, GolemBody.IRON, LEVELS)).doesNotContain(RancherRules.Action.CULL);
    assertThat(RancherRules.actions(allOn(), 0, GolemBody.IRON, LEVELS)).isEmpty();
  }

  @Test
  void speciesNamesMustBeAnimals() {
    assertThat(RancherRules.parseSpecies(List.of("COW", "cow", "ZOMBIE", "NOPE", "SHEEP", "MOOSHROOM")))
        .containsExactly(Id.minecraft("cow"), Id.minecraft("sheep"), Id.minecraft("mooshroom"));
    assertThat(RancherRules.parseSpecies(RancherRules.DEFAULT_SPECIES)).hasSize(RancherRules.DEFAULT_SPECIES.size());
  }

  @Test
  void adultsAboveTheKeepCountAreCulledNearestFirstButNeverGuardedOnes() {
    List<RancherRules.Beast> herd = new ArrayList<>();
    RancherRules.Beast far = adult(Id.minecraft("cow"), 9);
    RancherRules.Beast near = adult(Id.minecraft("cow"), 1);
    RancherRules.Beast named = guarded(Id.minecraft("cow"), 0.5);
    herd.add(far);
    herd.add(near);
    herd.add(named);
    herd.add(baby(Id.minecraft("cow"), 0.1));

    assertThat(RancherRules.choose(herd, 2, ALL, supplies(0, 0, 0))).containsExactly(cull(near), cull(far));
    assertThat(RancherRules.choose(herd, 3, ALL, supplies(0, 0, 0))).isEmpty();
    assertThat(RancherRules.choose(herd, 2, EnumSet.of(RancherRules.Action.BREED), supplies(9, 0, 0))).isEmpty();
  }

  @Test
  void pairsAreFedOnlyBelowTheKeepCountWithTwoReadyAdultsAndFoodForBoth() {
    RancherRules.Beast near = adult(Id.minecraft("cow"), 1);
    RancherRules.Beast far = adult(Id.minecraft("cow"), 4);
    List<RancherRules.Beast> herd = List.of(far, near, baby(Id.minecraft("cow"), 0.2), baby(Id.minecraft("cow"), 0.3));

    assertThat(RancherRules.choose(herd, 5, ALL, supplies(2, 0, 0))).containsExactly(breed(near), breed(far));
    assertThat(RancherRules.choose(herd, 4, ALL, supplies(2, 0, 0))).isEmpty();
    assertThat(RancherRules.choose(herd, 5, ALL, supplies(1, 0, 0))).isEmpty();
    assertThat(RancherRules.choose(List.of(near, cooling(Id.minecraft("cow"), 2)), 8, ALL, supplies(5, 0, 0))).isEmpty();
  }

  @Test
  void babiesAndPairsAlreadyInLoveBlockNewFeedingAtTheKeepCount() {
    RancherRules.Beast near = adult(Id.minecraft("pig"), 1);
    RancherRules.Beast far = adult(Id.minecraft("pig"), 2);
    List<RancherRules.Beast> withBaby = List.of(near, far, baby(Id.minecraft("pig"), 3));
    List<RancherRules.Beast> withPair = List.of(near, far, playerInLove(Id.minecraft("pig"), 4), playerInLove(Id.minecraft("pig"), 5));

    assertThat(RancherRules.choose(withBaby, 4, ALL, supplies(2, 0, 0))).containsExactly(breed(near), breed(far));
    assertThat(RancherRules.choose(withBaby, 3, ALL, supplies(2, 0, 0))).isEmpty();
    assertThat(RancherRules.choose(withPair, 6, ALL, supplies(2, 0, 0))).containsExactly(breed(near), breed(far));
    assertThat(RancherRules.choose(withPair, 5, ALL, supplies(2, 0, 0))).isEmpty();
    assertThat(RancherRules.breedRoom(new RancherRules.Census(4, 0, 1, 1), 5)).isTrue();
    assertThat(RancherRules.breedRoom(new RancherRules.Census(4, 0, 1, 1), 4)).isFalse();
    assertThat(RancherRules.breedRoom(new RancherRules.Census(4, 0, 0, 0), 5)).isTrue();
    assertThat(RancherRules.breedRoom(new RancherRules.Census(4, 1, 0, 0), 5)).isFalse();
  }

  @Test
  void loveAPlayerStartedIsNeverFinishedByARancher() {
    RancherRules.Beast playerFed = playerInLove(Id.minecraft("sheep"), 2);
    RancherRules.Beast partner = adult(Id.minecraft("sheep"), 3);

    assertThat(RancherRules.choose(List.of(playerFed, partner), 8, EnumSet.of(RancherRules.Action.BREED), supplies(1, 0, 0))).isEmpty();
    assertThat(RancherRules.census(List.of(playerFed, partner)).waiting()).isZero();
  }

  @Test
  void aWaitingAnimalGetsTheNearestPartnerWithinEightBlocks() {
    RancherRules.Beast waiting = inLove(Id.minecraft("cow"), 20);
    RancherRules.Beast nearGolem = adult(Id.minecraft("cow"), 1);
    RancherRules.Beast besideIt = adult(Id.minecraft("cow"), 21);
    RancherRules.Beast inReach = adult(Id.minecraft("cow"), 13);
    RancherRules.Beast outOfReach = adult(Id.minecraft("cow"), 29);

    assertThat(RancherRules.choose(List.of(nearGolem, outOfReach, inReach, besideIt, waiting), 8, EnumSet.of(RancherRules.Action.BREED),
        supplies(1, 0, 0))).containsExactly(breed(besideIt), breed(inReach));
  }

  @Test
  void anAdultWaitingInLoveGetsAPartnerWithFoodForOne() {
    RancherRules.Beast waiting = inLove(Id.minecraft("sheep"), 2);
    RancherRules.Beast partner = adult(Id.minecraft("sheep"), 3);
    List<RancherRules.Beast> herd = List.of(waiting, partner);

    assertThat(RancherRules.choose(herd, 8, EnumSet.of(RancherRules.Action.BREED), supplies(1, 0, 0))).containsExactly(breed(partner));
    assertThat(RancherRules.choose(List.of(waiting, inLove(Id.minecraft("sheep"), 4), partner), 8, EnumSet.of(RancherRules.Action.BREED),
        supplies(1, 0, 0))).isEmpty();
  }

  @Test
  void eachSpeciesIsCountedOnItsOwn() {
    List<RancherRules.Beast> herd = List.of(adult(Id.minecraft("cow"), 1), adult(Id.minecraft("cow"), 2), adult(Id.minecraft("cow"), 3),
        adult(Id.minecraft("pig"), 5), adult(Id.minecraft("pig"), 6));

    List<RancherRules.Choice> choices = RancherRules.choose(herd, 2, ALL, supplies(4, 0, 0));

    assertThat(choices).extracting(choice -> choice.action().id() + " " + choice.beast().species().legacyName())
        .containsExactly("cull COW", "cull COW", "cull COW");
    assertThat(RancherRules.choose(herd, 3, EnumSet.of(RancherRules.Action.BREED), supplies(4, 0, 0)))
        .extracting(choice -> choice.beast().species()).containsExactly(Id.minecraft("pig"), Id.minecraft("pig"));
  }

  @Test
  void unshornAdultSheepAreShornAndMilkNeedsAnEmptyContainer() {
    RancherRules.Beast sheep = shearable(3);
    RancherRules.Beast cow = milkable(Id.minecraft("cow"), 1, RancherRules.Milk.BUCKET);
    RancherRules.Beast mooshroom = milkable(Id.minecraft("mooshroom"), 2, RancherRules.Milk.BUCKET_OR_BOWL);
    List<RancherRules.Beast> herd = List.of(sheep, cow, mooshroom);
    Set<RancherRules.Action> work = EnumSet.of(RancherRules.Action.SHEAR, RancherRules.Action.MILK);

    assertThat(RancherRules.choose(herd, 8, work, supplies(0, 1, 0))).containsExactly(shear(sheep), milk(cow), milk(mooshroom));
    assertThat(RancherRules.choose(herd, 8, work, supplies(0, 0, 1))).containsExactly(shear(sheep), milk(mooshroom));
    assertThat(RancherRules.choose(herd, 8, work, supplies(0, 0, 0))).containsExactly(shear(sheep));
  }

  @Test
  void protectedAnimalsCountTowardTheKeepCountButAreNeverWorked() {
    RancherRules.Beast npc = untouchable(Id.minecraft("cow"), 0.5);
    RancherRules.Beast cow = adult(Id.minecraft("cow"), 1);
    List<RancherRules.Beast> herd = List.of(npc, cow, adult(Id.minecraft("cow"), 2));

    assertThat(RancherRules.choose(herd, 2, ALL, supplies(9, 9, 9))).extracting(RancherRules.Choice::beast).doesNotContain(npc);
    assertThat(RancherRules.choose(herd, 2, ALL, supplies(9, 9, 9))).hasSize(2);
  }

  @Test
  void cullsComeBeforeBreedingShearingAndMilking() {
    RancherRules.Beast cow = milkable(Id.minecraft("cow"), 1, RancherRules.Milk.BUCKET);
    RancherRules.Beast sheep = shearable(2);
    RancherRules.Beast pig = adult(Id.minecraft("pig"), 3);
    RancherRules.Beast otherPig = adult(Id.minecraft("pig"), 4);
    RancherRules.Beast spare = adult(Id.minecraft("cow"), 5);
    List<RancherRules.Beast> herd = List.of(cow, sheep, pig, otherPig, spare);

    assertThat(RancherRules.choose(herd, 3, ALL, supplies(2, 1, 0)))
        .containsExactly(breed(pig), breed(otherPig), shear(sheep), milk(cow));
    assertThat(RancherRules.choose(herd, 1, ALL, supplies(2, 1, 0)))
        .containsExactly(cull(cow), cull(pig), cull(otherPig), cull(spare), shear(sheep), milk(cow));
  }

  @Test
  void aSpeciesToCullComesBeforeANearerSpeciesToBreed() {
    RancherRules.Beast pig = adult(Id.minecraft("pig"), 1);
    RancherRules.Beast otherPig = adult(Id.minecraft("pig"), 2);
    List<RancherRules.Beast> herd = new ArrayList<>(List.of(pig, otherPig));
    List<RancherRules.Beast> cows = new ArrayList<>();
    for (int cow = 0; cow < 4; cow++) {
      cows.add(adult(Id.minecraft("cow"), 5 + cow));
    }
    herd.addAll(cows);

    assertThat(RancherRules.choose(herd, 3, ALL, supplies(2, 0, 0)))
        .containsExactly(cull(cows.get(0)), cull(cows.get(1)), cull(cows.get(2)), cull(cows.get(3)), breed(pig), breed(otherPig));
  }

  @Test
  void anActStillFitsOnlyWhileItsAnimalAndTheCountAllowIt() {
    RancherRules.Beast cow = adult(Id.minecraft("cow"), 1);

    assertThat(RancherRules.fits(RancherRules.Action.BREED, cow, adults(1), 2)).isTrue();
    assertThat(RancherRules.fits(RancherRules.Action.BREED, cow, adults(2), 2)).isFalse();
    assertThat(RancherRules.fits(RancherRules.Action.BREED, cow, new RancherRules.Census(1, 1, 0, 0), 2)).isFalse();
    assertThat(RancherRules.fits(RancherRules.Action.BREED, inLove(Id.minecraft("cow"), 1), adults(1), 2)).isFalse();
    assertThat(RancherRules.fits(RancherRules.Action.CULL, cow, adults(3), 2)).isTrue();
    assertThat(RancherRules.fits(RancherRules.Action.CULL, cow, new RancherRules.Census(3, 4, 2, 0), 2)).isTrue();
    assertThat(RancherRules.fits(RancherRules.Action.CULL, cow, adults(2), 2)).isFalse();
    assertThat(RancherRules.fits(RancherRules.Action.CULL, guarded(Id.minecraft("cow"), 1), adults(3), 2)).isFalse();
    assertThat(RancherRules.fits(RancherRules.Action.CULL, baby(Id.minecraft("cow"), 1), adults(3), 2)).isFalse();
    assertThat(RancherRules.fits(RancherRules.Action.SHEAR, shearable(1), adults(9), 2)).isTrue();
    assertThat(RancherRules.fits(RancherRules.Action.SHEAR, cow, adults(9), 2)).isFalse();
    assertThat(RancherRules.fits(RancherRules.Action.MILK, milkable(Id.minecraft("cow"), 1, RancherRules.Milk.BUCKET), adults(9), 2)).isTrue();
    assertThat(RancherRules.fits(RancherRules.Action.MILK, cow, adults(9), 2)).isFalse();
    assertThat(RancherRules.fits(RancherRules.Action.MILK, untouchable(Id.minecraft("cow"), 1), adults(9), 2)).isFalse();
  }

  @Test
  void husbandryScalesTheBreedingCooldown() {
    assertThat(RancherRules.cooldown(5999, 0.5D)).isEqualTo(2999);
    assertThat(RancherRules.cooldown(0, 0.5D)).isZero();
    assertThat(RancherRules.cooldown(-24000, 0.5D)).isEqualTo(-24000);
    assertThat(RancherRules.fraction(2D)).isEqualTo(1D);
    assertThat(RancherRules.fraction(Double.NaN)).isEqualTo(1D);
    assertThat(RancherRules.fraction(-1D)).isZero();
  }

  private NodeRecord data(String key, String value) {
    return pasture.toBuilder().data(Map.of(key, value)).build();
  }

  private NodeRecord allOn() {
    return RancherRules.toggle(pasture, RancherRules.Action.CULL);
  }

  private RancherRules.Beast adult(Id species, double distance) {
    return beast(species, distance, true, true, false, false, RancherRules.Milk.NONE, false, false);
  }

  private RancherRules.Beast cooling(Id species, double distance) {
    return beast(species, distance, true, false, false, false, RancherRules.Milk.NONE, false, false);
  }

  private RancherRules.Beast inLove(Id species, double distance) {
    return beast(species, distance, true, false, true, false, RancherRules.Milk.NONE, false, false);
  }

  private RancherRules.Beast playerInLove(Id species, double distance) {
    beasts++;
    return new RancherRules.Beast(new UUID(0x25A0L, beasts), species, distance, 0D, 0D, distance * distance, true, false, true, false, false,
        RancherRules.Milk.NONE, false, false);
  }

  private RancherRules.Beast baby(Id species, double distance) {
    return beast(species, distance, false, false, false, false, RancherRules.Milk.NONE, true, false);
  }

  private RancherRules.Beast guarded(Id species, double distance) {
    return beast(species, distance, true, false, false, false, RancherRules.Milk.NONE, true, false);
  }

  private RancherRules.Beast untouchable(Id species, double distance) {
    return beast(species, distance, true, true, false, false, RancherRules.Milk.BUCKET, false, true);
  }

  private RancherRules.Beast shearable(double distance) {
    return beast(Id.minecraft("sheep"), distance, true, false, false, true, RancherRules.Milk.NONE, false, false);
  }

  private RancherRules.Beast milkable(Id species, double distance, RancherRules.Milk milk) {
    return beast(species, distance, true, false, false, false, milk, false, false);
  }

  private RancherRules.Beast beast(Id species, double distance, boolean adult, boolean breedable, boolean love, boolean shearable,
                                   RancherRules.Milk milk, boolean guarded, boolean untouchable) {
    beasts++;
    UUID id = new UUID(0x25A0L, beasts);
    return new RancherRules.Beast(id, species, distance, 0D, 0D, distance * distance, adult, breedable, love, love, shearable, milk, guarded,
        untouchable);
  }

  private static RancherRules.Census adults(int adults) {
    return new RancherRules.Census(adults, 0, 0, 0);
  }

  private static RancherRules.Supplies supplies(int food, int buckets, int bowls) {
    return new RancherRules.Supplies() {
      @Override
      public int food(Id species) {
        return food;
      }

      @Override
      public int buckets() {
        return buckets;
      }

      @Override
      public int bowls() {
        return bowls;
      }
    };
  }

  private static RancherRules.Choice cull(RancherRules.Beast beast) {
    return new RancherRules.Choice(RancherRules.Action.CULL, beast);
  }

  private static RancherRules.Choice breed(RancherRules.Beast beast) {
    return new RancherRules.Choice(RancherRules.Action.BREED, beast);
  }

  private static RancherRules.Choice shear(RancherRules.Beast beast) {
    return new RancherRules.Choice(RancherRules.Action.SHEAR, beast);
  }

  private static RancherRules.Choice milk(RancherRules.Beast beast) {
    return new RancherRules.Choice(RancherRules.Action.MILK, beast);
  }
}
