package art.arcane.adapt.api.golem;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.entity.IronGolem;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.List;
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
  void companionCoresDefaultToFollow() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.FROST, GolemCoreType.BLANK, GolemState.Anchor.of(WORLD, 0, 70, 0)));

    assertThat(state.mode()).isEqualTo(GolemMode.FOLLOW);
  }

  @Test
  void everyFieldSurvivesTheJsonRoundTrip() {
    GolemState state = populated();

    GolemState back = GolemState.fromJson(state.toJson()).orElseThrow();

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
    assertThat(GolemState.fromJson(null)).isEmpty();
    assertThat(GolemState.fromJson("")).isEmpty();
    assertThat(GolemState.fromJson("{not json")).isEmpty();
    assertThat(GolemState.fromJson("{\"owner\":\"" + OWNER + "\",\"body\":\"iron\"}")).isEmpty();
    assertThat(GolemState.fromJson("{\"id\":\"" + GOLEM + "\",\"body\":\"iron\"}")).isEmpty();
    assertThat(GolemState.fromJson("{\"id\":\"" + GOLEM + "\",\"owner\":\"" + OWNER + "\",\"body\":\"gold\"}")).isEmpty();
  }

  @Test
  void unknownCoreAndRuneIdsAreSkippedAndMissingListsReadEmpty() {
    String json = "{\"id\":\"" + GOLEM + "\",\"owner\":\"" + OWNER + "\",\"body\":\"iron\",\"cores\":[\"guardian\",\"wizard\"],"
        + "\"runes\":[{\"aspect\":\"might\",\"tier\":1},{\"aspect\":\"chaos\",\"tier\":1},{\"aspect\":\"tide\",\"tier\":9}]}";

    GolemState state = GolemState.fromJson(json).orElseThrow();

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
