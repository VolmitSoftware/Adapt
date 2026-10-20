package art.arcane.adapt.api.golem;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.IronGolem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GolemSnapshotTest {
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-00000000a5a1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000a5b2");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000a5c3");
  private static final long TAKEN_AT = 1_700_000_000_000L;

  @Test
  void captureCopiesTheLoadoutRecordCargoAndEntityTraits() {
    GolemState state = populated();
    String name = GolemSnapshot.serializeName(Component.text("Rusty", NamedTextColor.GOLD));

    GolemSnapshot snapshot = GolemSnapshot.capture(state, new GolemSnapshot.Traits(name, 17.5D, 2, true, false), TAKEN_AT);

    assertThat(snapshot.id()).isEqualTo(GOLEM);
    assertThat(snapshot.owner()).isEqualTo(OWNER);
    assertThat(snapshot.body()).isEqualTo(GolemBody.COPPER);
    assertThat(snapshot.cores()).containsExactly(GolemCoreType.SORTER, GolemCoreType.GATHERER);
    assertThat(snapshot.primaryCore()).isEqualTo(GolemCoreType.SORTER);
    assertThat(snapshot.runes()).containsExactly(new GolemRune(GolemRuneType.SWIFTNESS, 2), new GolemRune(GolemRuneType.ORDER, 3));
    assertThat(snapshot.channels()).isEqualTo(0x0805);
    assertThat(snapshot.mode()).isEqualTo(GolemMode.HOME);
    assertThat(snapshot.home()).isEqualTo(GolemState.Anchor.of(WORLD, 10, 65, 20));
    assertThat(snapshot.homeChest()).isEqualTo(GolemState.Anchor.of(WORLD, 11, 65, 20));
    assertThat(snapshot.customName()).isEqualTo(name);
    assertThat(snapshot.stats()).isEqualTo(new GolemSnapshot.Stats(3L, 55.5D, 9L, 128L));
    assertThat(snapshot.masterwork()).isTrue();
    assertThat(snapshot.snowTrail()).isTrue();
    assertThat(snapshot.combatOff()).isTrue();
    assertThat(snapshot.essence()).isEqualTo(42.5D);
    assertThat(snapshot.carried()).containsExactly("stack-one", "stack-two");
    assertThat(snapshot.health()).isEqualTo(17.5D);
    assertThat(snapshot.maxHealthMark()).isEqualTo(36D);
    assertThat(snapshot.copperStage()).isEqualTo(2);
    assertThat(snapshot.waxed()).isTrue();
    assertThat(snapshot.playerCreated()).isFalse();
    assertThat(snapshot.takenAt()).isEqualTo(TAKEN_AT);
  }

  @Test
  void whetherAPlayerBuiltTheGolemSurvivesTheJsonRoundTrip() {
    GolemSnapshot built = GolemSnapshot.capture(populated(), new GolemSnapshot.Traits(null, 1D, 0, false, true), TAKEN_AT);
    GolemSnapshot village = GolemSnapshot.capture(populated(), new GolemSnapshot.Traits(null, 1D, 0, false, false), TAKEN_AT);

    assertThat(GolemSnapshot.fromJson(built.toJson()).orElseThrow().playerCreated()).isTrue();
    assertThat(GolemSnapshot.fromJson(village.toJson()).orElseThrow().playerCreated()).isFalse();
    assertThat(built.withoutCargo().playerCreated()).isTrue();
  }

  @Test
  void everyFieldSurvivesTheJsonRoundTrip() {
    GolemSnapshot snapshot = GolemSnapshot.capture(populated(),
        new GolemSnapshot.Traits(GolemSnapshot.serializeName(Component.text("Rusty")), 17.5D, 3, true, true), TAKEN_AT);

    GolemSnapshot back = GolemSnapshot.fromJson(snapshot.toJson()).orElseThrow();

    assertThat(back).isEqualTo(snapshot);
  }

  @Test
  void aSnapshotWithoutAHomeChestOrNameRoundTrips() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.FROST, GolemCoreType.BLANK, GolemState.Anchor.of(WORLD, 0, 70, 0)));
    GolemSnapshot snapshot = GolemSnapshot.capture(state, new GolemSnapshot.Traits(null, 4D, 0, false, true), TAKEN_AT);

    GolemSnapshot back = GolemSnapshot.fromJson(snapshot.toJson()).orElseThrow();

    assertThat(back).isEqualTo(snapshot);
    assertThat(back.homeChest()).isNull();
    assertThat(back.customName()).isNull();
    assertThat(back.name()).isEmpty();
    assertThat(back.mode()).isEqualTo(GolemMode.FOLLOW);
    assertThat(back.carried()).isEmpty();
  }

  @Test
  void theCustomNameRoundTripsAsASerializedComponent() {
    Component name = Component.text("Sir Clank", NamedTextColor.AQUA);
    GolemSnapshot snapshot = GolemSnapshot.capture(populated(), new GolemSnapshot.Traits(GolemSnapshot.serializeName(name), 1D, 0, false, true), TAKEN_AT);

    assertThat(GolemSnapshot.fromJson(snapshot.toJson()).orElseThrow().name()).contains(name);
    assertThat(GolemSnapshot.serializeName(null)).isNull();
    assertThat(copy(snapshot, "{not a component", snapshot.home(), snapshot.essence()).name()).isEmpty();
  }

  @Test
  void enumsAreStoredByStableIds() {
    GolemSnapshot snapshot = GolemSnapshot.capture(populated(), new GolemSnapshot.Traits(null, 1D, 0, false, true), TAKEN_AT);

    JsonObject stored = new Gson().fromJson(snapshot.toJson(), JsonObject.class);

    assertThat(stored.get("body").getAsString()).isEqualTo("copper");
    assertThat(stored.get("mode").getAsString()).isEqualTo("home");
    assertThat(stored.getAsJsonArray("cores").get(0).getAsString()).isEqualTo("sorter");
    assertThat(stored.getAsJsonArray("runes").get(0).getAsJsonObject().get("aspect").getAsString()).isEqualTo("swiftness");
    assertThat(stored.has("version")).isFalse();
  }

  @Test
  void combatSurvivesTheJsonRoundTripAndAnOlderSnapshotRestoresWithCombatOn() {
    GolemSnapshot off = GolemSnapshot.capture(populated(), new GolemSnapshot.Traits(null, 1D, 0, false, true), TAKEN_AT);
    GolemState on = populated();
    on.setCombatOff(false);
    GolemSnapshot fighting = GolemSnapshot.capture(on, new GolemSnapshot.Traits(null, 1D, 0, false, true), TAKEN_AT);
    JsonObject stored = new Gson().fromJson(off.toJson(), JsonObject.class);

    assertThat(GolemSnapshot.fromJson(off.toJson()).orElseThrow().combatOff()).isTrue();
    assertThat(GolemSnapshot.fromJson(fighting.toJson()).orElseThrow().combatOff()).isFalse();
    assertThat(GolemSnapshot.fromJson(without(stored, "combatOff")).orElseThrow().combatOff()).isFalse();
    assertThat(off.withoutCargo().combatOff()).isTrue();
  }

  @Test
  void withoutCargoDropsOnlyTheCarriedItemsAndEssence() {
    GolemSnapshot snapshot = GolemSnapshot.capture(populated(), new GolemSnapshot.Traits(null, 9D, 1, false, true), TAKEN_AT);

    GolemSnapshot emptied = snapshot.withoutCargo();

    assertThat(emptied.carried()).isEmpty();
    assertThat(emptied.essence()).isZero();
    assertThat(emptied.cores()).isEqualTo(snapshot.cores());
    assertThat(emptied.runes()).isEqualTo(snapshot.runes());
    assertThat(emptied.health()).isEqualTo(snapshot.health());
    assertThat(emptied.stats()).isEqualTo(snapshot.stats());
    assertThat(emptied.id()).isEqualTo(snapshot.id());
  }

  @Test
  void restoringAStateKeepsTheGolemIdLoadoutAndRecord() {
    GolemSnapshot snapshot = GolemSnapshot.capture(populated(), new GolemSnapshot.Traits(null, 9D, 1, false, true), TAKEN_AT);
    GolemState.Anchor here = GolemState.Anchor.of(WORLD, 90, 64, 90);

    GolemState restored = GolemState.restored(snapshot, here, 0L);

    assertThat(restored.id()).isEqualTo(GOLEM);
    assertThat(restored.owner()).isEqualTo(OWNER);
    assertThat(restored.body()).isEqualTo(GolemBody.COPPER);
    assertThat(restored.cores()).containsExactly(GolemCoreType.SORTER, GolemCoreType.GATHERER);
    assertThat(restored.runes()).containsExactly(new GolemRune(GolemRuneType.SWIFTNESS, 2), new GolemRune(GolemRuneType.ORDER, 3));
    assertThat(restored.channels()).isEqualTo(0x0805);
    assertThat(restored.mode()).isEqualTo(GolemMode.HOME);
    assertThat(restored.home()).isEqualTo(GolemState.Anchor.of(WORLD, 10, 65, 20));
    assertThat(restored.homeChest()).isEqualTo(GolemState.Anchor.of(WORLD, 11, 65, 20));
    assertThat(restored.carried()).containsExactly("stack-one", "stack-two");
    assertThat(restored.essence()).isEqualTo(42.5D);
    assertThat(restored.stats().kills()).isEqualTo(3L);
    assertThat(restored.stats().damage()).isEqualTo(55.5D);
    assertThat(restored.stats().tasks()).isEqualTo(9L);
    assertThat(restored.stats().itemsMoved()).isEqualTo(128L);
    assertThat(restored.masterwork()).isTrue();
    assertThat(restored.snowTrail()).isTrue();
    assertThat(restored.combatOff()).isTrue();
    assertThat(restored.maxHealthMark()).isEqualTo(36D);
    assertThat(restored.previouslyBound()).isTrue();
    assertThat(restored.dormant()).isFalse();
    assertThat(restored.colossus()).isFalse();
    assertThat(restored.hostileDamagePool()).isZero();
    IronGolem body = GolemTestEntities.entity(IronGolem.class);
    restored.write(body);
    assertThat(GolemState.read(body).orElseThrow().id()).isEqualTo(GOLEM);
  }

  @Test
  void aRestoredStateWithoutAHomeUsesTheGivenSpot() {
    GolemSnapshot captured = GolemSnapshot.capture(populated(), new GolemSnapshot.Traits(null, 9D, 1, false, true), TAKEN_AT);
    GolemSnapshot homeless = copy(captured, null, null, captured.essence());
    GolemState.Anchor here = GolemState.Anchor.of(WORLD, 90, 64, 90);

    assertThat(GolemState.restored(homeless, here, 0L).home()).isEqualTo(here);
  }

  @Test
  void valuesAreNormalizedWhenTheSnapshotIsBuilt() {
    GolemSnapshot captured = GolemSnapshot.capture(populated(), new GolemSnapshot.Traits(null, Double.NaN, 9, false, true), TAKEN_AT);
    GolemSnapshot snapshot = copy(captured, null, captured.home(), -4D);

    assertThat(snapshot.health()).isZero();
    assertThat(snapshot.copperStage()).isEqualTo(GolemSnapshot.MAX_COPPER_STAGE);
    assertThat(snapshot.essence()).isZero();
    assertThat(GolemSnapshot.capture(populated(), new GolemSnapshot.Traits(null, 1D, -2, false, true), TAKEN_AT).copperStage()).isZero();
    assertThatThrownBy(() -> snapshotWithCores(List.of())).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> snapshotWithCores(List.of(GolemCoreType.BLANK, GolemCoreType.GATHERER, GolemCoreType.SORTER)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void malformedOrIncompleteJsonReadsAsEmpty() {
    assertThat(GolemSnapshot.fromJson(null)).isEmpty();
    assertThat(GolemSnapshot.fromJson("")).isEmpty();
    assertThat(GolemSnapshot.fromJson("{\"id\":")).isEmpty();
    assertThat(GolemSnapshot.fromJson("[]")).isEmpty();
    JsonObject stored = new Gson().fromJson(GolemSnapshot.capture(populated(), new GolemSnapshot.Traits(null, 1D, 0, false, true), TAKEN_AT).toJson(),
        JsonObject.class);
    assertThat(GolemSnapshot.fromJson(without(stored, "id"))).isEmpty();
    assertThat(GolemSnapshot.fromJson(without(stored, "owner"))).isEmpty();
    assertThat(GolemSnapshot.fromJson(without(stored, "body"))).isEmpty();
    assertThat(GolemSnapshot.fromJson(with(stored, "body", "clay"))).isEmpty();
    assertThat(GolemSnapshot.fromJson(without(stored, "cores"))).isEmpty();
    JsonObject nullCargo = new Gson().fromJson(stored.toString(), JsonObject.class);
    JsonArray carried = new JsonArray();
    carried.add(JsonNull.INSTANCE);
    carried.add("stack-one");
    nullCargo.add("carried", carried);
    assertThat(GolemSnapshot.fromJson(nullCargo.toString())).hasValueSatisfying(read -> assertThat(read.carried()).containsExactly("stack-one"));
    JsonObject worldless = new Gson().fromJson(stored.toString(), JsonObject.class);
    worldless.getAsJsonObject("home").remove("world");
    worldless.getAsJsonObject("homeChest").add("world", JsonNull.INSTANCE);
    assertThat(GolemSnapshot.fromJson(worldless.toString())).hasValueSatisfying(read -> {
      assertThat(read.home()).isNull();
      assertThat(read.homeChest()).isNull();
    });
    JsonObject nullRune = new Gson().fromJson(stored.toString(), JsonObject.class);
    nullRune.getAsJsonArray("runes").add(JsonNull.INSTANCE);
    nullRune.getAsJsonArray("cores").add(JsonNull.INSTANCE);
    assertThat(GolemSnapshot.fromJson(nullRune.toString())).hasValueSatisfying(read -> {
      assertThat(read.runes()).hasSize(2);
      assertThat(read.cores()).hasSize(2);
    });
    assertThat(GolemSnapshot.fromJson(with(stored, "carried", "not a list"))).isEmpty();
    assertThat(GolemSnapshot.fromJson(with(stored, "id", "not a uuid"))).isEmpty();
  }

  @Test
  void runesAreCappedAtTheMostRuneSlots() {
    GolemState state = populated();
    List<GolemRune> crowded = new ArrayList<>();
    for (int index = 0; index < GolemRuneLayout.MAX_SLOTS + 3; index++) {
      crowded.add(new GolemRune(GolemRuneType.values()[index % GolemRuneType.values().length], 1));
    }
    state.setRunes(crowded);

    GolemSnapshot snapshot = GolemSnapshot.capture(state, new GolemSnapshot.Traits(null, 1D, 0, false, true), TAKEN_AT);

    assertThat(snapshot.runes()).hasSize(GolemRuneLayout.MAX_SLOTS).containsExactlyElementsOf(crowded.subList(0, GolemRuneLayout.MAX_SLOTS));
    assertThat(GolemSnapshot.fromJson(snapshot.toJson()).orElseThrow().runes()).hasSize(GolemRuneLayout.MAX_SLOTS);
  }

  @Test
  void unknownCoresRunesAndModesAreSkippedOrDefaulted() {
    JsonObject stored = new Gson().fromJson(GolemSnapshot.capture(populated(), new GolemSnapshot.Traits(null, 1D, 0, false, true), TAKEN_AT).toJson(),
        JsonObject.class);
    stored.getAsJsonArray("cores").add("wizard");
    JsonObject badRune = new JsonObject();
    badRune.addProperty("aspect", "chaos");
    badRune.addProperty("tier", 2);
    stored.getAsJsonArray("runes").add(badRune);
    stored.addProperty("mode", "dance");

    GolemSnapshot back = GolemSnapshot.fromJson(stored.toString()).orElseThrow();

    assertThat(back.cores()).containsExactly(GolemCoreType.SORTER, GolemCoreType.GATHERER);
    assertThat(back.runes()).hasSize(2);
    assertThat(back.mode()).isEqualTo(GolemMode.WORK);
  }

  private static GolemSnapshot snapshotWithCores(List<GolemCoreType> cores) {
    GolemSnapshot base = GolemSnapshot.capture(populated(), new GolemSnapshot.Traits(null, 1D, 0, false, true), TAKEN_AT);
    return new GolemSnapshot(base.id(), base.owner(), base.body(), cores, base.runes(), base.channels(), base.mode(), base.home(),
        base.homeChest(), base.customName(), base.stats(), base.masterwork(), base.snowTrail(), base.combatOff(), base.essence(), base.carried(), base.health(),
        base.maxHealthMark(), base.copperStage(), base.waxed(), base.playerCreated(), base.takenAt());
  }

  private static GolemSnapshot copy(GolemSnapshot base, String customName, GolemState.Anchor home, double essence) {
    return new GolemSnapshot(base.id(), base.owner(), base.body(), base.cores(), base.runes(), base.channels(), base.mode(), home,
        base.homeChest(), customName, base.stats(), base.masterwork(), base.snowTrail(), base.combatOff(), essence, base.carried(), base.health(),
        base.maxHealthMark(), base.copperStage(), base.waxed(), base.playerCreated(), base.takenAt());
  }

  private static String without(JsonObject stored, String field) {
    JsonObject copy = new Gson().fromJson(stored.toString(), JsonObject.class);
    copy.remove(field);
    return copy.toString();
  }

  private static String with(JsonObject stored, String field, String value) {
    JsonObject copy = new Gson().fromJson(stored.toString(), JsonObject.class);
    copy.addProperty(field, value);
    return copy.toString();
  }

  private static GolemState populated() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.COPPER, GolemCoreType.SORTER, GolemState.Anchor.of(WORLD, 10, 65, 20)));
    state.setCores(List.of(GolemCoreType.SORTER, GolemCoreType.GATHERER));
    state.setRunes(List.of(new GolemRune(GolemRuneType.SWIFTNESS, 2), new GolemRune(GolemRuneType.ORDER, 3)));
    state.setChannels(0x0805);
    state.setMode(GolemMode.HOME);
    state.setHomeChest(GolemState.Anchor.of(WORLD, 11, 65, 20));
    state.setCarried(List.of("stack-one", "stack-two"));
    state.setEssence(42.5D);
    state.setHostileDamagePool(7.25D);
    state.stats().addKills(3L);
    state.stats().addDamage(55.5D);
    state.stats().addTasks(9L);
    state.stats().addItemsMoved(128L);
    state.setMasterwork(true);
    state.setColossus(true);
    state.setSnowTrail(true);
    state.setCombatOff(true);
    state.setMaxHealthMark(36D);
    return state;
  }
}
