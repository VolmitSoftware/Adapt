package art.arcane.adapt.api.content;

import art.arcane.adapt.api.TestStubs;
import art.arcane.adapt.api.TestHost;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.EngineBridge;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeathSaveSlotTest {
  private static final DeathSaveSlot SHARED = DeathSaveSlot.of(Id.of("alchemist", "philosophers_stone"));

  private final List<String> errors = new ArrayList<>();
  private EngineBridge bridge;

  @BeforeEach
  void start() {
    PlatformLog log = TestStubs.stub(PlatformLog.class, Map.of("error", (Object[] args) -> {
      errors.add((String) args[0]);
      return null;
    }));
    bridge = TestStubs.stub(EngineBridge.class, Map.of("log", (Object[] args) -> log));
    Engine.start(TestHost.key(), bridge);
  }

  @AfterEach
  void stop() {
    Engine.stop(TestHost.key(), bridge);
  }

  @Test
  void slotsWithEqualIdsShareOneCooldownAcrossDeclarations() {
    MapData data = new MapData();
    PlayerRef player = player(data);
    DeathSaveSlot otherAddonsView = DeathSaveSlot.of(Id.of("alchemist", "philosophers_stone"));

    assertThat(SHARED.activate(player, 1_000L, 500L)).isTrue();

    assertThat(otherAddonsView).isEqualTo(SHARED);
    assertThat(otherAddonsView.activate(player, 1_200L, 500L)).isFalse();
    assertThat(otherAddonsView.remaining(player, 1_200L)).isEqualTo(300L);
    assertThat(otherAddonsView.isReady(player, 1_500L)).isTrue();
  }

  @Test
  void theDeadlineIsSavedUnderTheConventionalKey() {
    MapData data = new MapData();
    DeathSaveSlot nested = DeathSaveSlot.of(Id.adapt("chronos/hourglass_guard"));

    nested.activate(player(data), 10L, 5L);

    assertThat(nested.deadlineKey().id()).isEqualTo(Id.adapt("death_save_chronos_hourglass_guard"));
    assertThat(data.values).containsEntry(Id.adapt("death_save_chronos_hourglass_guard"), 15L);
  }

  @Test
  void aDeadlineOfTheWrongTypeKeepsTheSlotClosedAndIsLogged() {
    MapData data = new MapData();
    data.foreign.add(SHARED.deadlineKey().id());

    assertThat(SHARED.remaining(player(data), 0L)).isEqualTo(Long.MAX_VALUE);
    assertThat(SHARED.activate(player(data), 0L, 1L)).isFalse();
    assertThat(errors).hasSize(2).allSatisfy((String error) -> assertThat(error).contains("alchemist:philosophers_stone"));
  }

  @Test
  void aSaveThatIsNotAnAdaptationRunsAtNormalPriority() {
    DeathSave save = () -> 0;

    assertThat(save.deathSavePriority(player(new MapData()))).isEqualTo(CommonPreferences.DeathSavePriority.NORMAL);
    assertThat(save.protectsFromDamage(null)).isFalse();
  }

  private static PlayerRef player(MapData data) {
    UUID id = UUID.fromString("00000000-0000-0000-0000-000000000007");
    return TestStubs.stub(PlayerRef.class, Map.of("data", (Object[] args) -> data, "uuid", (Object[] args) -> id));
  }

  private static final class MapData implements DataHolder {
    private final Map<Id, Object> values = new HashMap<>();
    private final Set<Id> foreign = new HashSet<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(DataKey<T> key) {
      Object value = values.get(key.id());
      return value == null ? null : (T) value;
    }

    @Override
    public boolean has(DataKey<?> key) {
      return values.containsKey(key.id());
    }

    @Override
    public boolean hasAny(Id id) {
      return values.containsKey(id) || foreign.contains(id);
    }

    @Override
    public <T> void set(DataKey<T> key, T value) {
      values.put(key.id(), value);
    }

    @Override
    public void remove(Id id) {
      values.remove(id);
    }

    @Override
    public Set<Id> keys() {
      return values.keySet();
    }

    @Override
    public boolean isEmpty() {
      return values.isEmpty();
    }
  }
}
