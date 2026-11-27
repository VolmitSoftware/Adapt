package art.arcane.adapt.pack.link;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PackViewersTest {
  private final UUID player = UUID.randomUUID();
  private final UUID pack = UUID.randomUUID();
  private final PackViewers viewers = new PackViewers(PackViewers.Styles.memory());
  private final List<String> events = new ArrayList<>();

  PackViewersTest() {
    viewers.onLoaded((UUID id) -> events.add("loaded " + (id.equals(player) ? "player" : id)));
    viewers.onLost((UUID id) -> events.add("lost " + (id.equals(player) ? "player" : id)));
  }

  @Test
  void statusesWalkTheOfferToLoaded() {
    viewers.offered(player, pack);
    assertThat(viewers.state(player)).contains(PackState.OFFERED);

    viewers.status(player, pack, PackState.ACCEPTED);
    viewers.status(player, pack, PackState.DOWNLOADED);
    assertThat(viewers.loaded(player)).isFalse();

    viewers.status(player, pack, PackState.LOADED);

    assertThat(viewers.state(player)).contains(PackState.LOADED);
    assertThat(viewers.loaded(player)).isTrue();
    assertThat(events).containsExactly("loaded player");
  }

  @Test
  void declinedAndFailedPlayersAreNotLoaded() {
    viewers.offered(player, pack);
    viewers.status(player, pack, PackState.DECLINED);
    assertThat(viewers.loaded(player)).isFalse();
    assertThat(viewers.state(player)).contains(PackState.DECLINED);

    viewers.offered(player, pack);
    viewers.status(player, pack, PackState.FAILED);
    assertThat(viewers.loaded(player)).isFalse();
    assertThat(events).isEmpty();
  }

  @Test
  void reportsForAnOlderPackAreIgnored() {
    UUID older = UUID.randomUUID();
    viewers.offered(player, older);
    viewers.offered(player, pack);

    boolean applied = viewers.status(player, older, PackState.LOADED);

    assertThat(applied).isFalse();
    assertThat(viewers.state(player)).contains(PackState.OFFERED);
    assertThat(viewers.packId(player)).contains(pack);
  }

  @Test
  void reportsWithoutAnOfferAreIgnored() {
    assertThat(viewers.status(player, pack, PackState.LOADED)).isFalse();
    assertThat(viewers.loaded(player)).isFalse();
  }

  @Test
  void discardingOrReplacingALoadedPackLosesIt() {
    viewers.offered(player, pack);
    viewers.status(player, pack, PackState.LOADED);

    viewers.status(player, pack, PackState.DISCARDED);
    assertThat(viewers.loaded(player)).isFalse();

    viewers.offered(player, pack);
    viewers.status(player, pack, PackState.LOADED);
    viewers.offered(player, UUID.randomUUID());

    assertThat(events).containsExactly("loaded player", "lost player", "loaded player", "lost player");
  }

  @Test
  void removedForgetsTheOfferAndTellsListeners() {
    viewers.offered(player, pack);
    viewers.status(player, pack, PackState.LOADED);

    viewers.removed(player);

    assertThat(viewers.state(player)).isEmpty();
    assertThat(events).containsExactly("loaded player", "lost player");
  }

  @Test
  void quitForgetsSilently() {
    viewers.offered(player, pack);
    viewers.status(player, pack, PackState.LOADED);

    viewers.quit(player);

    assertThat(viewers.loaded(player)).isFalse();
    assertThat(events).containsExactly("loaded player");
  }

  @Test
  void bookStyleWaitsForTheLoadedReport() {
    viewers.style(player, MenuStyle.BOOK);

    assertThat(viewers.loaded(player)).isFalse();
    assertThat(viewers.style(player)).contains(MenuStyle.BOOK);
    assertThat(events).isEmpty();

    viewers.offered(player, pack);
    viewers.status(player, pack, PackState.DECLINED);
    assertThat(viewers.loaded(player)).isFalse();

    viewers.offered(player, pack);
    viewers.status(player, pack, PackState.LOADED);
    assertThat(viewers.loaded(player)).isTrue();
    assertThat(events).containsExactly("loaded player");
  }

  @Test
  void chestOverrideWinsOverALoadedPack() {
    viewers.offered(player, pack);
    viewers.status(player, pack, PackState.LOADED);

    viewers.style(player, MenuStyle.CHEST);
    assertThat(viewers.loaded(player)).isFalse();

    viewers.style(player, null);
    assertThat(viewers.loaded(player)).isTrue();
    assertThat(viewers.style(player)).isEmpty();
    assertThat(events).containsExactly("loaded player", "lost player", "loaded player");
  }

  @Test
  void overridesAreReadFromTheStore() {
    PackViewers.Styles store = PackViewers.Styles.memory();
    store.set(player, MenuStyle.CHEST);
    PackViewers stored = new PackViewers(store);
    stored.offered(player, pack);
    stored.status(player, pack, PackState.LOADED);

    assertThat(stored.loaded(player)).isFalse();
    assertThat(stored.style(player)).contains(MenuStyle.CHEST);
  }

  @Test
  void aLoadedPackWaitsForTheStoredChoiceToBeReadable() {
    PackViewers.Styles memory = PackViewers.Styles.memory();
    boolean[] readable = {false};
    PackViewers.Styles store = new PackViewers.Styles() {
      @Override
      public boolean ready(UUID playerId) {
        return readable[0];
      }

      @Override
      public MenuStyle get(UUID playerId) {
        return readable[0] ? memory.get(playerId) : null;
      }

      @Override
      public void set(UUID playerId, MenuStyle style) {
        memory.set(playerId, style);
      }
    };
    memory.set(player, MenuStyle.CHEST);
    PackViewers stored = new PackViewers(store);
    stored.offered(player, pack);
    stored.status(player, pack, PackState.LOADED);

    assertThat(stored.loaded(player)).isFalse();
    readable[0] = true;
    assertThat(stored.loaded(player)).isFalse();
    memory.set(player, null);
    assertThat(stored.loaded(player)).isTrue();
  }

  @Test
  void removedListenersStopHearing() {
    PackViewers fresh = new PackViewers(PackViewers.Styles.memory());
    List<UUID> heard = new ArrayList<>();
    Runnable cancel = fresh.onLoaded(heard::add);
    cancel.run();

    fresh.offered(player, pack);
    fresh.status(player, pack, PackState.LOADED);

    assertThat(heard).isEmpty();
  }
  @Test
  void mergedViewersHaveThePackOnceAnyServerPackLoaded() {
    PackViewers merged = merged(Set.of());
    UUID other = UUID.randomUUID();

    assertThat(merged.merged()).isTrue();
    assertThat(merged.status(player, other, PackState.ACCEPTED)).isFalse();
    assertThat(merged.status(player, other, PackState.DOWNLOADED)).isFalse();
    assertThat(merged.loaded(player)).isFalse();

    assertThat(merged.status(player, other, PackState.LOADED)).isTrue();

    assertThat(merged.loaded(player)).isTrue();
    assertThat(events).containsExactly("loaded player");
  }

  @Test
  void mergedViewersKeepThePackThroughALaterAcceptButLoseItOnDeclineFailureOrDiscard() {
    for (PackState lost : List.of(PackState.DECLINED, PackState.FAILED, PackState.DISCARDED)) {
      PackViewers merged = merged(Set.of());
      merged.status(player, UUID.randomUUID(), PackState.LOADED);
      merged.status(player, UUID.randomUUID(), PackState.ACCEPTED);
      assertThat(merged.loaded(player)).as(lost.name()).isTrue();

      merged.status(player, UUID.randomUUID(), lost);
      assertThat(merged.loaded(player)).as(lost.name()).isFalse();

      merged.status(player, UUID.randomUUID(), PackState.LOADED);
      assertThat(merged.loaded(player)).as(lost.name()).isTrue();
    }
  }

  @Test
  void mergedViewersIgnoreExcludedPlayers() {
    PackViewers merged = merged(Set.of(player));

    assertThat(merged.status(player, pack, PackState.LOADED)).isFalse();

    assertThat(merged.loaded(player)).isFalse();
    assertThat(events).isEmpty();
  }

  @Test
  void aChestChoiceWinsOverAMergedPack() {
    PackViewers merged = merged(Set.of());
    merged.status(player, pack, PackState.LOADED);

    merged.style(player, MenuStyle.CHEST);

    assertThat(merged.loaded(player)).isFalse();
    assertThat(events).containsExactly("loaded player", "lost player");
  }

  @Test
  void mergedViewersForgetAPlayerWhoQuits() {
    PackViewers merged = merged(Set.of());
    merged.status(player, pack, PackState.LOADED);

    merged.quit(player);

    assertThat(merged.loaded(player)).isFalse();
  }

  @Test
  void ownViewersAreNotMerged() {
    assertThat(viewers.merged()).isFalse();
  }

  private PackViewers merged(Set<UUID> excluded) {
    Set<UUID> bedrock = new HashSet<>(excluded);
    PackViewers merged = PackViewers.merged(PackViewers.Styles.memory(), bedrock::contains);
    events.clear();
    merged.onLoaded((UUID id) -> events.add("loaded " + (id.equals(player) ? "player" : id)));
    merged.onLost((UUID id) -> events.add("lost " + (id.equals(player) ? "player" : id)));
    return merged;
  }
}
