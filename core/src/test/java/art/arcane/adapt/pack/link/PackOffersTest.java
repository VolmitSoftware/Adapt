package art.arcane.adapt.pack.link;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PackOffersTest {
  private static final long WINDOW = 3_000L;
  private static final String URL = "http://pack.test/abc.zip";

  private final UUID player = UUID.randomUUID();
  private final RecordingLink link = new RecordingLink();
  private final PackViewers viewers = new PackViewers(PackViewers.Styles.memory());
  private final PackOffers.Declines declines = PackOffers.Declines.memory();
  private final Set<UUID> modClients = new HashSet<>();
  private final Set<UUID> bedrock = new HashSet<>();
  private final List<Delayed> delayed = new ArrayList<>();
  private final PackArtifact first = TestPacks.pack("first");
  private final PackArtifact second = TestPacks.pack("second");

  @Test
  void joinOffersOnlyAfterTheHandshakeWindow() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);

    offers.joined(player);
    assertThat(link.sent).isEmpty();
    assertThat(delayed).singleElement().extracting(Delayed::millis).isEqualTo(WINDOW);

    runDelayed();

    assertThat(link.sent).containsExactly("offer " + first.id());
    assertThat(link.offers.getFirst()).isEqualTo(new PackOffer(first.id(), URL, first.sha1Hex(), "prompt " + player));
    assertThat(viewers.state(player)).contains(PackState.OFFERED);
  }

  @Test
  void aPlayerWithTheAdaptClientIsNeverOffered() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);

    offers.joined(player);
    modClients.add(player);
    runDelayed();
    offers.published(second, URL);

    assertThat(link.sent).isEmpty();
    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.CLIENT_MOD);
    assertThat(link.sent).isEmpty();
  }

  @Test
  void aClientThatNegotiatesLateLosesThePack() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    modClients.add(player);
    offers.status(player, first.id(), PackState.ACCEPTED);

    assertThat(link.sent).containsExactly("offer " + first.id(), "remove " + first.id());
    assertThat(viewers.state(player)).isEmpty();
  }

  @Test
  void bedrockPlayersAreSkipped() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    bedrock.add(player);

    offers.joined(player);
    runDelayed();

    assertThat(link.sent).isEmpty();
    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.BEDROCK);
  }

  @Test
  void aDeclinedPackIsNotOfferedAgainUntilItsHashChanges() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    offers.status(player, first.id(), PackState.DECLINED);
    offers.quit(player);
    offers.joined(player);
    runDelayed();

    assertThat(link.sent).containsExactly("offer " + first.id());

    offers.published(second, URL);

    assertThat(link.sent).containsExactly("offer " + first.id(), "offer " + second.id());
  }

  @Test
  void anOfferAfterTheClientDeclinedOnThisConnectionIsReportedAsDeclinedUntilARejoin() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.status(player, first.id(), PackState.DECLINED);

    assertThat(offers.bookChosen(player)).isEqualTo(PackOffers.Result.CLIENT_DECLINED);
    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.CLIENT_DECLINED);
    offers.status(player, first.id(), PackState.DECLINED);
    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.CLIENT_DECLINED);

    offers.quit(player);
    offers.joined(player);
    runDelayed();
    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.OFFERED);
  }

  @Test
  void theWindowWaitsUntilTheStoredDeclineCanBeRead() {
    boolean[] readable = {false};
    PackOffers.Declines memory = PackOffers.Declines.memory();
    memory.set(player, first.sha1Hex());
    PackOffers.Declines stored = new PackOffers.Declines() {
      @Override
      public boolean ready(UUID playerId) {
        return readable[0];
      }

      @Override
      public String get(UUID playerId) {
        return readable[0] ? memory.get(playerId) : null;
      }

      @Override
      public void set(UUID playerId, String sha1Hex) {
        memory.set(playerId, sha1Hex);
      }
    };
    PackOffers offers = new PackOffers(link, viewers, stored, modClients::contains, bedrock::contains, (UUID id) -> "prompt " + id,
        (UUID id, long millis, Runnable task) -> delayed.add(new Delayed(millis, task)), PackOffers.Policy.JOIN, WINDOW);
    offers.published(first, URL);
    offers.joined(player);

    runDelayed();
    runDelayed();
    assertThat(link.sent).isEmpty();
    assertThat(delayed).singleElement().extracting(Delayed::millis).isEqualTo(PackOffers.READ_RETRY_MILLIS);

    readable[0] = true;
    runDelayed();
    assertThat(link.sent).isEmpty();
    assertThat(delayed).isEmpty();

    offers.published(second, URL);
    assertThat(link.sent).containsExactly("offer " + second.id());
  }

  @Test
  void aDeclineOutlivesARestartForTheSameHash() {
    PackOffers before = offers(PackOffers.Policy.JOIN);
    before.published(first, URL);
    before.joined(player);
    runDelayed();
    before.status(player, first.id(), PackState.DECLINED);

    PackOffers after = offers(PackOffers.Policy.JOIN);
    after.published(first, URL);
    after.joined(player);
    runDelayed();

    assertThat(link.sent).containsExactly("offer " + first.id());

    after.published(second, URL);

    assertThat(link.sent).containsExactly("offer " + first.id(), "offer " + second.id());
  }

  @Test
  void askingClearsTheStoredDecline() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.status(player, first.id(), PackState.DECLINED);
    assertThat(declines.get(player)).isEqualTo(first.sha1Hex());

    offers.requested(player);

    assertThat(declines.get(player)).isNull();
  }

  @Test
  void choosingBookOffersThePackEvenWithOffersOff() {
    PackOffers offers = offers(PackOffers.Policy.OFF);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    assertThat(offers.bookChosen(player)).isEqualTo(PackOffers.Result.OFFERED);
    assertThat(link.sent).containsExactly("offer " + first.id());
  }

  @Test
  void choosingBookDoesNotOfferADeclinedPack() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.status(player, first.id(), PackState.DECLINED);

    assertThat(offers.bookChosen(player)).isEqualTo(PackOffers.Result.CLIENT_DECLINED);
    assertThat(link.sent).containsExactly("offer " + first.id());
  }

  @Test
  void aBuiltPackNothingHostsIsReportedAsUnhosted() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, null);
    offers.joined(player);
    runDelayed();

    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.UNHOSTED);
    assertThat(link.sent).isEmpty();

    offers.published(first, URL);

    assertThat(link.sent).containsExactly("offer " + first.id());
  }

  @Test
  void aDeclinedPlayerWhoAsksGetsThePackAgain() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.status(player, first.id(), PackState.DECLINED);

    PackOffers.Result result = offers.requested(player);

    assertThat(result).isEqualTo(PackOffers.Result.CLIENT_DECLINED);
    assertThat(link.sent).containsExactly("offer " + first.id(), "remove " + first.id(), "offer " + first.id());
    assertThat(viewers.state(player)).contains(PackState.OFFERED);
  }

  @Test
  void aNewHashReplacesTheOldPackOnEveryClient() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.status(player, first.id(), PackState.LOADED);

    offers.published(second, "http://pack.test/second.zip");

    assertThat(link.sent).containsExactly("offer " + first.id(), "remove " + first.id(), "offer " + second.id());
    assertThat(viewers.packId(player)).contains(second.id());
    assertThat(viewers.loaded(player)).isFalse();
  }

  @Test
  void theSamePackAtANewAddressIsOfferedAgainOnce() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.status(player, first.id(), PackState.DOWNLOAD_FAILED);

    offers.published(first, "http://host.test/abc.zip");
    offers.published(first, "http://host.test/abc.zip");

    assertThat(link.sent).containsExactly("offer " + first.id(), "remove " + first.id(), "offer " + first.id());
    assertThat(link.offers.getLast().url()).isEqualTo("http://host.test/abc.zip");
    assertThat(viewers.state(player)).contains(PackState.OFFERED);
  }

  @Test
  void republishingTheSameArtifactSendsNothing() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    offers.published(first, URL);

    assertThat(link.sent).containsExactly("offer " + first.id());
  }

  @Test
  void withNoArtifactNothingIsOfferedUntilOneArrives() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.joined(player);
    runDelayed();

    assertThat(link.sent).isEmpty();
    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.NO_PACK);

    offers.published(first, URL);

    assertThat(link.sent).containsExactly("offer " + first.id());
  }

  @Test
  void withdrawingTheArtifactRemovesItFromClients() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.status(player, first.id(), PackState.LOADED);

    offers.published(null, null);

    assertThat(link.sent).containsExactly("offer " + first.id(), "remove " + first.id());
    assertThat(viewers.loaded(player)).isFalse();
  }

  @Test
  void firstOpenWaitsForAMenu() {
    PackOffers offers = offers(PackOffers.Policy.FIRST_OPEN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    assertThat(link.sent).isEmpty();

    offers.opened(player);
    offers.opened(player);

    assertThat(link.sent).containsExactly("offer " + first.id());
  }

  @Test
  void firstOpenBeforeTheWindowOffersWhenItPasses() {
    PackOffers offers = offers(PackOffers.Policy.FIRST_OPEN);
    offers.published(first, URL);
    offers.joined(player);

    offers.opened(player);
    assertThat(link.sent).isEmpty();
    runDelayed();

    assertThat(link.sent).containsExactly("offer " + first.id());
  }

  @Test
  void offPolicyOffersOnlyOnRequest() {
    PackOffers offers = offers(PackOffers.Policy.OFF);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.opened(player);
    assertThat(link.sent).isEmpty();

    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.OFFERED);
    assertThat(link.sent).containsExactly("offer " + first.id());
  }

  @Test
  void anUnavailableLinkOffersNothing() {
    link.available = false;
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    assertThat(link.sent).isEmpty();
    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.UNAVAILABLE);
  }

  @Test
  void aWindowFromAnEarlierSessionDoesNotOffer() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    offers.quit(player);

    runDelayed();

    assertThat(link.sent).isEmpty();
  }

  @Test
  void playersPresentAtStartAreOfferedAtOnce() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);

    offers.present(player);

    assertThat(delayed).isEmpty();
    assertThat(link.sent).containsExactly("offer " + first.id());
  }

  @Test
  void mergedViewersAreNeverOfferedAndCountAnyLoadedPack() {
    PackViewers merged = PackViewers.merged(PackViewers.Styles.memory(), bedrock::contains);
    PackOffers offers = new PackOffers(link, merged, declines, modClients::contains, bedrock::contains, (UUID id) -> "prompt " + id,
        (UUID id, long millis, Runnable task) -> delayed.add(new Delayed(millis, task)), PackOffers.Policy.JOIN, WINDOW);
    UUID bedrockPlayer = UUID.randomUUID();
    bedrock.add(bedrockPlayer);

    offers.joined(player);
    runDelayed();
    offers.status(player, UUID.randomUUID(), PackState.LOADED);
    offers.status(bedrockPlayer, UUID.randomUUID(), PackState.LOADED);

    assertThat(link.sent).isEmpty();
    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.UNAVAILABLE);
    assertThat(link.sent).isEmpty();
    assertThat(merged.loaded(player)).isTrue();
    assertThat(merged.loaded(bedrockPlayer)).isFalse();
  }

  private PackOffers offers(PackOffers.Policy policy) {
    return new PackOffers(link, viewers, declines, modClients::contains, bedrock::contains, (UUID id) -> "prompt " + id,
        (UUID id, long millis, Runnable task) -> delayed.add(new Delayed(millis, task)), policy, WINDOW);
  }

  private void runDelayed() {
    List<Delayed> due = List.copyOf(delayed);
    delayed.clear();
    for (Delayed task : due) {
      task.task.run();
    }
  }

  private record Delayed(long millis, Runnable task) {
  }

  private static final class RecordingLink implements PackLink {
    private final List<String> sent = new ArrayList<>();
    private final List<PackOffer> offers = new ArrayList<>();
    private boolean available = true;

    @Override
    public boolean available() {
      return available;
    }

    @Override
    public void offer(UUID playerId, PackOffer offer) {
      sent.add("offer " + offer.packId());
      offers.add(offer);
    }

    @Override
    public void remove(UUID playerId, UUID packId) {
      sent.add("remove " + packId);
    }

    @Override
    public void listen(StatusListener listener) {
    }
  }
}
