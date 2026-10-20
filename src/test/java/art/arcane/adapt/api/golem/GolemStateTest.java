package art.arcane.adapt.api.golem;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.IronGolem;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GolemStateTest {
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

  @Test
  void createdStateCarriesTheBindingAndDefaults() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.IRON, GolemCoreType.GUARDIAN, GolemState.Anchor.of(WORLD, 4, 64, -9)));

    assertThat(state.id()).isEqualTo(GOLEM);
    assertThat(state.owner()).isEqualTo(OWNER);
    assertThat(state.body()).isEqualTo(GolemBody.IRON);
    assertThat(state.cores()).containsExactly(GolemCoreType.GUARDIAN);
    assertThat(state.primaryCore()).isEqualTo(GolemCoreType.GUARDIAN);
    assertThat(state.runes()).isEmpty();
    assertThat(state.channels()).isEqualTo(ChannelMask.NONE);
    assertThat(state.mode()).isEqualTo(GolemMode.WORK);
    assertThat(state.home()).isEqualTo(GolemState.Anchor.of(WORLD, 4, 64, -9));
    assertThat(state.homeChest()).isNull();
    assertThat(state.carried()).isEmpty();
    assertThat(state.essence()).isZero();
    assertThat(state.hostileDamagePool()).isZero();
    assertThat(state.stats().kills()).isZero();
    assertThat(state.masterwork()).isFalse();
    assertThat(state.colossus()).isFalse();
    assertThat(state.dormant()).isFalse();
    assertThat(state.previouslyBound()).isFalse();
  }

  @Test
  void parsedBodyCoresAndModeAreReusedUntilTheyChange() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.IRON, GolemCoreType.GUARDIAN, GolemState.Anchor.of(WORLD, 0, 64, 0)));

    assertThat(state.body()).isSameAs(GolemBody.IRON);
    assertThat(state.cores()).isSameAs(state.cores()).containsExactly(GolemCoreType.GUARDIAN);
    assertThat(state.mode()).isSameAs(state.mode());

    state.setCores(List.of(GolemCoreType.GUARDIAN, GolemCoreType.GATHERER));
    state.setMode(GolemMode.STAY);

    assertThat(state.cores()).containsExactly(GolemCoreType.GUARDIAN, GolemCoreType.GATHERER);
    assertThat(state.mode()).isSameAs(GolemMode.STAY);
    GolemState back = reloaded(state);
    assertThat(back.body()).isSameAs(GolemBody.IRON);
    assertThat(back.cores()).containsExactly(GolemCoreType.GUARDIAN, GolemCoreType.GATHERER);
    assertThat(back.mode()).isSameAs(GolemMode.STAY);
  }

  @Test
  void essenceCountsInWholeExperiencePoints() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.IRON, GolemCoreType.GATHERER, GolemState.Anchor.of(WORLD, 0, 64, 0)));

    assertThat(state.essencePoints()).isZero();
    state.setEssence(14.7D);
    assertThat(state.essencePoints()).isEqualTo(14);
    state.setEssence(1500D);
    assertThat(state.essencePoints()).isEqualTo(1500);
    state.setEssence(-3D);
    assertThat(state.essencePoints()).isZero();
    state.setEssence(Double.NaN);
    assertThat(state.essencePoints()).isZero();
    state.setEssence(1.0E12D);
    assertThat(state.essencePoints()).isEqualTo(Integer.MAX_VALUE);
    JsonObject stored = new Gson().fromJson(state.toJson(), JsonObject.class);
    stored.addProperty("essence", -5.5D);
    assertThat(readPayload(stored.toString()).orElseThrow().essencePoints()).isZero();
  }

  @Test
  void companionCoresDefaultToFollow() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.FROST, GolemCoreType.BLANK, GolemState.Anchor.of(WORLD, 0, 70, 0)));

    assertThat(state.mode()).isEqualTo(GolemMode.FOLLOW);
  }

  @Test
  void everyFieldSurvivesTheJsonRoundTrip() {
    GolemState state = populated();

    GolemState back = reloaded(state);

    assertThat(back.id()).isEqualTo(GOLEM);
    assertThat(back.owner()).isEqualTo(OWNER);
    assertThat(back.body()).isEqualTo(GolemBody.COPPER);
    assertThat(back.cores()).containsExactly(GolemCoreType.SORTER, GolemCoreType.GATHERER);
    assertThat(back.runes()).containsExactly(new GolemRune(GolemRuneType.SWIFTNESS, 2), new GolemRune(GolemRuneType.ORDER, 3));
    assertThat(back.channels()).isEqualTo(0x0805);
    assertThat(back.mode()).isEqualTo(GolemMode.HOME);
    assertThat(back.home()).isEqualTo(GolemState.Anchor.of(WORLD, 10, 65, 20));
    assertThat(back.homeChest()).isEqualTo(GolemState.Anchor.of(WORLD, 11, 65, 20));
    assertThat(back.carried()).containsExactly("rO0AB-one", "rO0AB-two");
    assertThat(back.essence()).isEqualTo(42.5D);
    assertThat(back.hostileDamagePool()).isEqualTo(7.25D);
    assertThat(back.stats().kills()).isEqualTo(3L);
    assertThat(back.stats().damage()).isEqualTo(55.5D);
    assertThat(back.stats().tasks()).isEqualTo(9L);
    assertThat(back.stats().itemsMoved()).isEqualTo(128L);
    assertThat(back.masterwork()).isTrue();
    assertThat(back.colossus()).isTrue();
    assertThat(back.dormant()).isTrue();
    assertThat(back.previouslyBound()).isTrue();
  }

  @Test
  void snowTrailDefaultsOffAndSurvivesTheJsonRoundTrip() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.FROST, GolemCoreType.BLANK, GolemState.Anchor.of(WORLD, 0, 70, 0)));

    assertThat(state.snowTrail()).isFalse();
    assertThat(readPayload("{\"id\":\"" + GOLEM + "\",\"owner\":\"" + OWNER + "\",\"body\":\"frost\"}").orElseThrow().snowTrail()).isFalse();

    state.setSnowTrail(true);

    assertThat(reloaded(state).snowTrail()).isTrue();
  }

  @Test
  void theSightingStartsAtZeroAndSurvivesTheJsonRoundTrip() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.FROST, GolemCoreType.BLANK, GolemState.Anchor.of(WORLD, 0, 70, 0)));

    assertThat(state.sighting()).isZero();
    assertThat(readPayload("{\"id\":\"" + GOLEM + "\",\"owner\":\"" + OWNER + "\",\"body\":\"frost\"}").orElseThrow().sighting()).isZero();

    state.setSighting(17L);

    assertThat(reloaded(state).sighting()).isEqualTo(17L);
  }

  @Test
  void ownerLevelsDefaultToZeroAndSurviveTheJsonRoundTrip() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.FROST, GolemCoreType.BLANK, GolemState.Anchor.of(WORLD, 0, 70, 0)));

    assertThat(state.ownerLevel("frost-frame")).isZero();
    assertThat(readPayload("{\"id\":\"" + GOLEM + "\",\"owner\":\"" + OWNER + "\",\"body\":\"frost\"}").orElseThrow().ownerLevel("frost-frame")).isZero();

    assertThat(state.setOwnerLevel("frost-frame", 3)).isTrue();
    assertThat(state.setOwnerLevel("frost-frame", 3)).isFalse();
    assertThat(state.setOwnerLevel("frost-frame", 4)).isTrue();
    GolemState back = reloaded(state);

    assertThat(back.ownerLevel("frost-frame")).isEqualTo(4);
    assertThat(back.ownerLevel("runic-augments")).isZero();
  }

  @Test
  void aRestoredStateStartsFromTheSightingItIsGiven() {
    GolemState original = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.IRON, GolemCoreType.GUARDIAN, GolemState.Anchor.of(WORLD, 0, 70, 0)));
    original.setSighting(3L);
    GolemSnapshot snapshot = GolemSnapshot.capture(original, new GolemSnapshot.Traits(null, 20D, 0, false, true), 0L);

    GolemState restored = GolemState.restored(snapshot, GolemState.Anchor.of(WORLD, 1, 70, 1), 9L);

    assertThat(restored.sighting()).isEqualTo(9L);
  }

  @Test
  void theMaximumHealthMarkStartsAtZeroAndSurvivesTheJsonRoundTrip() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.FROST, GolemCoreType.BLANK, GolemState.Anchor.of(WORLD, 0, 70, 0)));

    assertThat(state.maxHealthMark()).isZero();

    state.setMaxHealthMark(24D);

    assertThat(reloaded(state).maxHealthMark()).isEqualTo(24D);
  }

  @Test
  void equippedItemsStartEmptyAndSurviveTheJsonRoundTripBySlot() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.BONE, GolemCoreType.GUARDIAN, GolemState.Anchor.of(WORLD, 0, 70, 0)));

    assertThat(state.equipment()).isEmpty();

    state.setEquipment(GolemEquipmentSlot.HELMET, "rO0AB-helm");
    state.setEquipment(GolemEquipmentSlot.MAIN_HAND, "rO0AB-bow");
    state.setEquipment(GolemEquipmentSlot.BOOTS, "rO0AB-boots");
    state.setEquipment(GolemEquipmentSlot.BOOTS, null);
    GolemState back = reloaded(state);

    assertThat(back.equipment()).containsOnlyKeys(GolemEquipmentSlot.HELMET, GolemEquipmentSlot.MAIN_HAND)
        .containsEntry(GolemEquipmentSlot.HELMET, "rO0AB-helm")
        .containsEntry(GolemEquipmentSlot.MAIN_HAND, "rO0AB-bow");
    JsonObject json = new Gson().fromJson(state.toJson(), JsonObject.class);
    assertThat(json.getAsJsonObject("equipment").get("main_hand").getAsString()).isEqualTo("rO0AB-bow");
  }

  @Test
  void unknownOrBlankEquipmentEntriesAreDroppedAndMissingEquipmentReadsEmpty() {
    String base = "{\"id\":\"" + GOLEM + "\",\"owner\":\"" + OWNER + "\",\"body\":\"bone\"";

    GolemState noisy = readPayload(base + ",\"equipment\":{\"helmet\":\"rO0AB-helm\",\"cape\":\"rO0AB-cape\",\"boots\":\" \"}}").orElseThrow();

    assertThat(noisy.equipment()).containsOnlyKeys(GolemEquipmentSlot.HELMET);
    assertThat(readPayload(base + "}").orElseThrow().equipment()).isEmpty();
    assertThat(readPayload(base + ",\"equipment\":null}").orElseThrow().equipment()).isEmpty();
  }

  @Test
  void enumsAreStoredByStableIds() {
    JsonObject json = new Gson().fromJson(populated().toJson(), JsonObject.class);

    assertThat(json.get("body").getAsString()).isEqualTo("copper");
    assertThat(json.get("mode").getAsString()).isEqualTo("home");
    assertThat(json.getAsJsonArray("cores").get(0).getAsString()).isEqualTo("sorter");
    assertThat(json.getAsJsonArray("runes").get(0).getAsJsonObject().get("aspect").getAsString()).isEqualTo("swiftness");
    assertThat(json.get("previouslyBound").getAsBoolean()).isTrue();
    assertThat(json.has("boundOnce")).isFalse();
  }

  @Test
  void malformedOrIncompleteJsonReadsAsEmpty() {
    assertThat(GolemState.read(null)).isEmpty();
    assertThat(readPayload("")).isEmpty();
    assertThat(readPayload("{not json")).isEmpty();
    assertThat(readPayload("{\"owner\":\"" + OWNER + "\",\"body\":\"iron\"}")).isEmpty();
    assertThat(readPayload("{\"id\":\"" + GOLEM + "\",\"body\":\"iron\"}")).isEmpty();
    assertThat(readPayload("{\"id\":\"" + GOLEM + "\",\"owner\":\"" + OWNER + "\",\"body\":\"gold\"}")).isEmpty();
  }

  @Test
  void unknownCoreAndRuneIdsAreSkippedAndMissingListsReadEmpty() {
    String json = "{\"id\":\"" + GOLEM + "\",\"owner\":\"" + OWNER + "\",\"body\":\"iron\",\"cores\":[\"guardian\",\"wizard\"],"
        + "\"runes\":[{\"aspect\":\"might\",\"tier\":1},{\"aspect\":\"chaos\",\"tier\":1},{\"aspect\":\"tide\",\"tier\":9}]}";

    GolemState state = readPayload(json).orElseThrow();

    assertThat(state.cores()).containsExactly(GolemCoreType.GUARDIAN);
    assertThat(state.runes()).containsExactly(new GolemRune(GolemRuneType.MIGHT, 1));
    assertThat(state.carried()).isEmpty();
    assertThat(state.mode()).isEqualTo(GolemMode.WORK);
    assertThat(state.stats().kills()).isZero();
  }

  @Test
  void coresAreCappedAtTwo() {
    GolemState state = populated();

    assertThatThrownBy(() -> state.setCores(List.of(GolemCoreType.BLANK, GolemCoreType.SORTER, GolemCoreType.ANGLER)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> state.setCores(List.of())).isInstanceOf(IllegalArgumentException.class);
    assertThat(GolemState.MAX_CORES).isEqualTo(2);
  }

  @Test
  void channelsAreNormalizedToSixteenBits() {
    GolemState state = populated();

    state.setChannels(0x3_0001);

    assertThat(state.channels()).isEqualTo(0x0001);
  }

  @Test
  void entityCodecUsesTheAdaptGolemStringKey() {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    GolemState state = populated();

    assertThat(GolemState.has(golem)).isFalse();
    assertThat(GolemState.read(golem)).isEmpty();

    state.write(golem);

    assertThat(GolemState.KEY.toString()).isEqualTo("adapt:golem");
    assertThat(GolemState.has(golem)).isTrue();
    assertThat(golem.getPersistentDataContainer().get(GolemState.KEY, PersistentDataType.STRING)).isEqualTo(state.toJson());
    assertThat(GolemState.read(golem).orElseThrow().stats().itemsMoved()).isEqualTo(128L);

    GolemState.clear(golem);

    assertThat(GolemState.has(golem)).isFalse();
  }

  @Test
  void corruptEntityPayloadReadsAsEmpty() {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    golem.getPersistentDataContainer().set(GolemState.KEY, PersistentDataType.STRING, "{\"id\":42");

    assertThat(GolemState.has(golem)).isTrue();
    assertThat(GolemState.read(golem)).isEmpty();
  }

  @Test
  void theGolemNameIsStoredWithTheStateAndSurvivesAReload() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.IRON, GolemCoreType.GUARDIAN, GolemState.Anchor.of(WORLD, 0, 64, 0)));
    assertThat(state.name()).isEmpty();
    assertThat(state.customName()).isNull();

    state.setName(Component.text("Clank", NamedTextColor.GOLD));

    assertThat(state.name()).contains(Component.text("Clank", NamedTextColor.GOLD));
    assertThat(state.name().orElseThrow()).isSameAs(state.name().orElseThrow());
    assertThat(reloaded(state).name()).contains(Component.text("Clank", NamedTextColor.GOLD));
    assertThat(GolemSnapshot.fromJson(GolemSnapshot.capture(state, new GolemSnapshot.Traits(state.customName(), 20D, 0, false, true), 0L).toJson())
        .orElseThrow().name()).contains(Component.text("Clank", NamedTextColor.GOLD));
  }

  private static GolemState reloaded(GolemState state) {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    state.write(golem);
    return GolemState.read(golem).orElseThrow();
  }

  private static Optional<GolemState> readPayload(String json) {
    IronGolem golem = GolemTestEntities.entity(IronGolem.class);
    golem.getPersistentDataContainer().set(GolemState.KEY, PersistentDataType.STRING, json);
    return GolemState.read(golem);
  }

  private static GolemState populated() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.COPPER, GolemCoreType.SORTER, GolemState.Anchor.of(WORLD, 10, 65, 20)));
    state.setCores(List.of(GolemCoreType.SORTER, GolemCoreType.GATHERER));
    state.setRunes(List.of(new GolemRune(GolemRuneType.SWIFTNESS, 2), new GolemRune(GolemRuneType.ORDER, 3)));
    state.setChannels(0x0805);
    state.setMode(GolemMode.HOME);
    state.setHomeChest(GolemState.Anchor.of(WORLD, 11, 65, 20));
    state.setCarried(List.of("rO0AB-one", "rO0AB-two"));
    state.setEssence(42.5D);
    state.setHostileDamagePool(7.25D);
    state.stats().addKills(3L);
    state.stats().addDamage(55.5D);
    state.stats().addTasks(9L);
    state.stats().addItemsMoved(128L);
    state.setMasterwork(true);
    state.setColossus(true);
    state.setDormant(true);
    state.setPreviouslyBound(true);
    return state;
  }
}
