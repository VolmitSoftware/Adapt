package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.EntityRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GolemJsonFormTest {
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-00000000a5a1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000a5b2");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000a5c3");
  private static final UUID TRANSFER = UUID.fromString("00000000-0000-0000-0000-00000000a5d4");
  private static final String NAME = "{\"text\":\"R<u>s&t=y\\u00e9\\n'\"}";
  private static final String STATE = "{\"id\":\"00000000-0000-0000-0000-00000000a5a1\",\"owner\":\"00000000-0000-0000-0000-00000000a5b2\",\"body\":\"copper\",\"cores\":[\"sorter\",\"gatherer\"],\"runes\":[{\"aspect\":\"swiftness\",\"tier\":2},{\"aspect\":\"order\",\"tier\":3}],\"channels\":2053,\"mode\":\"home\",\"home\":{\"world\":\"00000000-0000-0000-0000-00000000a5c3\",\"x\":10,\"y\":65,\"z\":20},\"homeChest\":{\"world\":\"00000000-0000-0000-0000-00000000a5c3\",\"x\":11,\"y\":65,\"z\":20},\"carried\":[\"stack-one\",\"st\\\"ack=two\"],\"held\":\"held-tool\",\"equipment\":{\"helmet\":\"helm<>==\"},\"essence\":42.5,\"hostileDamagePool\":7.25,\"stats\":{\"kills\":3,\"damage\":1.0E-4,\"tasks\":9,\"itemsMoved\":128},\"masterwork\":true,\"colossus\":true,\"dormant\":false,\"previouslyBound\":false,\"snowTrail\":true,\"combatOff\":true,\"maxHealthMark\":36.0,\"sighting\":12345,\"ownerLevels\":{\"adapt:golem/iron_frame\":4},\"customName\":\"{\\\"text\\\":\\\"R<u>s&t=y\\\\u00e9\\\\n'\\\"}\"}";
  private static final String SNAPSHOT = "{\"id\":\"00000000-0000-0000-0000-00000000a5a1\",\"owner\":\"00000000-0000-0000-0000-00000000a5b2\",\"body\":\"copper\",\"cores\":[\"sorter\",\"gatherer\"],\"runes\":[{\"aspect\":\"swiftness\",\"tier\":2},{\"aspect\":\"order\",\"tier\":3}],\"channels\":2053,\"mode\":\"home\",\"home\":{\"world\":\"00000000-0000-0000-0000-00000000a5c3\",\"x\":10,\"y\":65,\"z\":20},\"homeChest\":{\"world\":\"00000000-0000-0000-0000-00000000a5c3\",\"x\":11,\"y\":65,\"z\":20},\"customName\":\"{\\\"text\\\":\\\"R<u>s&t=y\\\\u00e9\\\\n'\\\"}\",\"kills\":3,\"damage\":1.0E-4,\"tasks\":9,\"itemsMoved\":128,\"masterwork\":true,\"snowTrail\":true,\"combatOff\":true,\"essence\":42.5,\"carried\":[\"stack-one\",\"st\\\"ack=two\"],\"held\":\"held-tool\",\"equipment\":{\"helmet\":\"helm<>==\"},\"health\":17.5,\"maxHealthMark\":36.0,\"copperStage\":2,\"waxed\":true,\"playerCreated\":false,\"takenAt\":1700000000000}";
  private static final String PACKED = "{\"id\":\"00000000-0000-0000-0000-00000000a5a1\",\"owner\":\"00000000-0000-0000-0000-00000000a5b2\",\"body\":\"copper\",\"cores\":[\"sorter\",\"gatherer\"],\"runes\":[{\"aspect\":\"swiftness\",\"tier\":2},{\"aspect\":\"order\",\"tier\":3}],\"channels\":2053,\"mode\":\"home\",\"home\":{\"world\":\"00000000-0000-0000-0000-00000000a5c3\",\"x\":10,\"y\":65,\"z\":20},\"homeChest\":{\"world\":\"00000000-0000-0000-0000-00000000a5c3\",\"x\":11,\"y\":65,\"z\":20},\"customName\":\"{\\\"text\\\":\\\"R<u>s&t=y\\\\u00e9\\\\n'\\\"}\",\"kills\":3,\"damage\":1.0E-4,\"tasks\":9,\"itemsMoved\":128,\"masterwork\":true,\"snowTrail\":true,\"combatOff\":true,\"essence\":42.5,\"carried\":[\"stack-one\",\"st\\\"ack=two\"],\"held\":\"held-tool\",\"equipment\":{\"helmet\":\"helm<>==\"},\"health\":17.5,\"maxHealthMark\":36.0,\"copperStage\":2,\"waxed\":true,\"playerCreated\":false,\"takenAt\":1700000000000,\"transfer\":\"00000000-0000-0000-0000-00000000a5d4\"}";

  @Test
  void stateKeepsItsStoredForm() {
    GolemState state = populated();

    assertThat(state.toJson()).isEqualTo(STATE);
    assertThat(read(STATE).toJson()).isEqualTo(STATE);
  }

  @Test
  void snapshotKeepsItsStoredForm() {
    GolemSnapshot snapshot = GolemSnapshot.capture(populated(), new GolemSnapshot.Traits(NAME, 17.5D, 2, true, false), 1_700_000_000_000L);

    assertThat(snapshot.toJson()).isEqualTo(SNAPSHOT);
    assertThat(snapshot.toPackedJson(TRANSFER)).isEqualTo(PACKED);
    assertThat(GolemSnapshot.fromJson(SNAPSHOT).orElseThrow().toJson()).isEqualTo(SNAPSHOT);
  }

  @Test
  void namesReadAndWriteAsTextComponents() {
    assertThat(GolemSnapshot.textName("Cl<a>nk \"x\" \\ =")).isEqualTo("\"Cl<a>nk \\\"x\\\" \\\\ =\"");
    assertThat(GolemSnapshot.plainName("{\"text\":\"a\",\"extra\":[{\"text\":\"b\"},\"c\",{\"extra\":[\"d\"]}]}")).isEqualTo("abcd");
    assertThat(GolemSnapshot.plainName("Steve")).isEqualTo("Steve");
    assertThat(GolemSnapshot.plainName("{\"text\":")).isEqualTo("{\"text\":");
    assertThat(GolemSnapshot.fromJson("{\"id\":")).isEmpty();
    assertThat(GolemSnapshot.fromJson(" ")).isEmpty();
  }

  private static GolemState read(String json) {
    EntityRef entity = mock(EntityRef.class);
    DataHolder data = mock(DataHolder.class);
    when(entity.data()).thenReturn(data);
    when(entity.uuid()).thenReturn(GOLEM);
    when(data.get(GolemState.KEY)).thenReturn(json);
    return GolemState.read(entity).orElseThrow();
  }

  private static GolemState populated() {
    GolemState state = GolemState.create(new GolemState.Seed(GOLEM, OWNER, GolemBody.COPPER, GolemCoreType.SORTER, GolemAnchor.of(WORLD, 10, 65, 20)));
    state.setCores(List.of(GolemCoreType.SORTER, GolemCoreType.GATHERER));
    state.setRunes(List.of(new GolemRune(GolemRuneType.SWIFTNESS, 2), new GolemRune(GolemRuneType.ORDER, 3)));
    state.setChannels(0x0805);
    state.setMode(GolemMode.HOME);
    state.setHomeChest(GolemAnchor.of(WORLD, 11, 65, 20));
    state.setCarried(List.of("stack-one", "st\"ack=two"));
    state.setHeld("held-tool");
    state.setEquipment(GolemEquipmentSlot.HELMET, "helm<>==");
    state.setEssence(42.5D);
    state.setHostileDamagePool(7.25D);
    state.stats().addKills(3L);
    state.stats().addDamage(0.0001D);
    state.stats().addTasks(9L);
    state.stats().addItemsMoved(128L);
    state.setMasterwork(true);
    state.setColossus(true);
    state.setSnowTrail(true);
    state.setCombatOff(true);
    state.setMaxHealthMark(36D);
    state.setSighting(12345L);
    state.setOwnerLevel("adapt:golem/iron_frame", 4);
    state.setName(NAME);
    return state;
  }
}
