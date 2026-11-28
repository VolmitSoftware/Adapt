package art.arcane.adapt.pack.link;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PackMessages;
import art.arcane.adapt.pack.screen.PackScreens;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.util.common.misc.MenuSounds;
import art.arcane.adapt.util.config.TomlCodec;
import art.arcane.volmlib.util.localization.MessageArgs;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PackOffersTest {
  private static final long WINDOW = 3_000L;
  private static final String URL = "http://pack.test/abc.zip";

  private final UUID player = UUID.randomUUID();
  private final RecordingLink link = new RecordingLink();
  private final PackViewers viewers = new PackViewers();
  private final FakePlayers players = new FakePlayers();
  private final List<Delayed> delayed = new ArrayList<>();
  private final PackArtifact first = TestPacks.pack("first");
  private final PackArtifact second = TestPacks.pack("second");
  private long now = 1_000_000L;

  @Test
  void joinOffersOnlyAfterTheHandshakeWindow() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);

    offers.joined(player);
    assertThat(link.sent).isEmpty();
    assertThat(delayed).singleElement().extracting(Delayed::millis).isEqualTo(WINDOW);

    runDelayed();

    assertThat(link.sent).containsExactly("offer " + first.id());
    assertThat(link.offers.getFirst()).isEqualTo(new PackOffer(first.id(), URL, first.sha1Hex(), "prompt " + player, false));
    assertThat(viewers.state(player)).contains(PackState.OFFERED);
    assertThat(players.told).isEmpty();
  }

  @Test
  void aPlayerWithTheAdaptClientIsNeverOfferedNorNudged() {
    PackOffers offers = offers(PackOffers.Policy.OFF);
    offers.published(first, URL);

    offers.joined(player);
    players.mod.add(player);
    runDelayed();
    offers.opened(player);
    offers.published(second, URL);

    assertThat(link.sent).isEmpty();
    assertThat(players.told).isEmpty();
    assertThat(offers.standing(player)).isEqualTo(PackOffers.Standing.NONE);
    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.CLIENT_MOD);
    assertThat(link.sent).isEmpty();
  }

  @Test
  void aClientThatNegotiatesLateLosesThePack() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    players.mod.add(player);
    offers.status(player, first.id(), PackState.ACCEPTED);

    assertThat(link.sent).containsExactly("offer " + first.id(), "remove " + first.id());
    assertThat(viewers.state(player)).isEmpty();
  }

  @Test
  void bedrockPlayersAreSkipped() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    players.bedrock.add(player);

    offers.joined(player);
    runDelayed();
    offers.opened(player);

    assertThat(link.sent).isEmpty();
    assertThat(players.told).isEmpty();
    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.BEDROCK);
  }

  @Test
  void aDeclineLastsOnlyForTheConnection() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    offers.status(player, first.id(), PackState.DECLINED);
    offers.published(second, URL);
    assertThat(link.sent).containsExactly("offer " + first.id());

    offers.quit(player);
    offers.joined(player);
    runDelayed();

    assertThat(link.sent).containsExactly("offer " + first.id(), "offer " + second.id());
  }

  @Test
  void aDeclineIsAnsweredWithTheStepsOncePerConnection() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    offers.status(player, first.id(), PackState.DECLINED);
    offers.requested(player);
    offers.status(player, first.id(), PackState.DECLINED);

    assertThat(players.told).containsExactly("steps");
    assertThat(offers.standing(player)).isEqualTo(PackOffers.Standing.REFUSING);
  }

  @Test
  void anOfferAfterTheClientDeclinedOnThisConnectionIsReportedAsDeclinedUntilARejoin() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.status(player, first.id(), PackState.DECLINED);

    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.CLIENT_DECLINED);
    offers.status(player, first.id(), PackState.DECLINED);
    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.CLIENT_DECLINED);

    offers.quit(player);
    offers.joined(player);
    runDelayed();
    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.OFFERED);
  }

  @Test
  void theWindowWaitsUntilThePlayerDataIsLoaded() {
    players.ready = false;
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);

    runDelayed();
    runDelayed();
    assertThat(link.sent).isEmpty();
    assertThat(delayed).singleElement().extracting(Delayed::millis).isEqualTo(PackOffers.READ_RETRY_MILLIS);

    players.ready = true;
    runDelayed();
    assertThat(link.sent).containsExactly("offer " + first.id());
    assertThat(delayed).isEmpty();
  }

  @Test
  void aRequiredPackIsOfferedAtJoinUnderEveryPolicyAndMarkedRequired() {
    players.required = true;
    PackOffers offers = offers(PackOffers.Policy.OFF);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    assertThat(link.offers).singleElement().extracting(PackOffer::required).isEqualTo(true);
  }

  @Test
  void decliningARequiredPackDisconnects() {
    players.required = true;
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    offers.status(player, first.id(), PackState.DECLINED);

    assertThat(players.told).containsExactly("kick");
  }

  @Test
  void aPlayerWithTheBypassGetsAnOptionalPackWhileItIsRequired() {
    players.required = true;
    players.optional.add(player);
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.status(player, first.id(), PackState.DECLINED);

    assertThat(link.offers).singleElement().extracting(PackOffer::required).isEqualTo(false);
    assertThat(players.told).containsExactly("steps");
  }

  @Test
  void aFailedDownloadOfARequiredPackDisconnectsNobody() {
    players.required = true;
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    offers.status(player, first.id(), PackState.ACCEPTED);
    offers.status(player, first.id(), PackState.DOWNLOAD_FAILED);

    assertThat(players.told).isEmpty();
    assertThat(link.sent).noneMatch((String sent) -> sent.equals("disconnect"));
  }

  @Test
  void aPlayerWithoutTheRequirementGetsAnOptionalPack() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.status(player, first.id(), PackState.DECLINED);

    assertThat(link.offers).singleElement().extracting(PackOffer::required).isEqualTo(false);
    assertThat(players.told).containsExactly("steps");
  }

  @Test
  void aPlayerNotOfferedAtJoinIsInvitedOnce() {
    PackOffers offers = offers(PackOffers.Policy.OFF);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    assertThat(link.sent).isEmpty();
    assertThat(players.told).containsExactly("invite");
    assertThat(offers.standing(player)).isEqualTo(PackOffers.Standing.INVITED);
  }

  @Test
  void openingAMenuNudgesAgainAtMostEveryInterval() {
    PackOffers offers = offers(PackOffers.Policy.OFF);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    offers.opened(player);
    now += PackOffers.NUDGE_MILLIS - 1L;
    offers.opened(player);
    assertThat(players.told).containsExactly("invite");

    now += 1L;
    offers.opened(player);
    assertThat(players.told).containsExactly("invite", "invite");
  }

  @Test
  void aMenuNudgeAfterADeclineGivesTheSteps() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.status(player, first.id(), PackState.DECLINED);

    now += PackOffers.NUDGE_MILLIS;
    offers.opened(player);

    assertThat(players.told).containsExactly("steps", "steps");
  }

  @Test
  void noNudgeWhileThePackLoadsOrOnceItLoaded() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    offers.status(player, first.id(), PackState.ACCEPTED);
    offers.opened(player);
    offers.status(player, first.id(), PackState.LOADED);
    now += PackOffers.NUDGE_MILLIS;
    offers.opened(player);

    assertThat(players.told).isEmpty();
    assertThat(offers.standing(player)).isEqualTo(PackOffers.Standing.NONE);
  }

  @Test
  void anUnansweredOfferIsNudgedOnMenuOpen() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();

    offers.opened(player);

    assertThat(players.told).containsExactly("invite");
  }

  @Test
  void withReofferOffADeclinedPackWaitsForANewHashOrARequest() {
    players.reoffer = false;
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.status(player, first.id(), PackState.DECLINED);
    offers.quit(player);

    offers.joined(player);
    runDelayed();
    assertThat(link.sent).containsExactly("offer " + first.id());
    assertThat(players.told).containsExactly("steps", "invite");

    offers.published(second, URL);
    assertThat(link.sent).containsExactly("offer " + first.id(), "offer " + second.id());
  }

  @Test
  void askingForgetsTheStoredDecline() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.status(player, first.id(), PackState.DECLINED);
    assertThat(players.declined.get(player)).isEqualTo(first.sha1Hex());

    offers.requested(player);

    assertThat(players.declined).doesNotContainKey(player);
  }

  @Test
  void everySwitchOffInTheConfigMeansNoOfferNoNudgeNoItemNoModMentionAndNoSound() throws ReflectiveOperationException, IOException {
    AdaptConfig previous = install(switchedOff());
    try {
      AdaptConfig.Pack pack = AdaptConfig.get().getPack();
      PackOffers offers = new PackOffers(link, viewers, players, PackOffers.Switches.CONFIG, this::delay, () -> now,
          pack.offerPolicy(), WINDOW);
      offers.published(first, URL);
      offers.joined(player);
      runDelayed();
      offers.opened(player);
      now += PackOffers.NUDGE_MILLIS;
      offers.opened(player);
      FakePlayer viewer = new FakePlayer("Viewer", new Loc(new FakeWorld("world"), 0, 64, 0));
      List<MenuSounds.Action> heard = new ArrayList<>();
      PackScreens.Speaker speaker = PackScreens.Speaker.switched((PlayerRef ignored, MenuSounds.Action action) -> heard.add(action));
      for (MenuSounds.Action action : MenuSounds.Action.values()) {
        speaker.play(viewer, action);
      }

      assertThat(link.sent).isEmpty();
      assertThat(players.told).isEmpty();
      assertThat(heard).isEmpty();
      assertThat(PackInvite.item(viewer, PackOffers.Standing.INVITED, pack.isMenuItem())).isNull();
      assertThat(PackPitch.prompt(player)).isEqualTo(AdaptLanguage.text(player, PackMessages.PROMPT, MessageArgs.empty()));
      assertThat(PackPitch.steps(player)).doesNotContain("\n");
    } finally {
      install(previous);
    }
  }

  @Test
  void withTheDefaultConfigThePackIsOfferedAndTheMenusSound() throws ReflectiveOperationException {
    AdaptConfig previous = install(new AdaptConfig());
    try {
      PackOffers offers = new PackOffers(link, viewers, players, PackOffers.Switches.CONFIG, this::delay, () -> now,
          AdaptConfig.get().getPack().offerPolicy(), WINDOW);
      offers.published(first, URL);
      offers.joined(player);
      runDelayed();
      List<MenuSounds.Action> heard = new ArrayList<>();
      PackScreens.Speaker.switched((PlayerRef ignored, MenuSounds.Action action) -> heard.add(action))
          .play(new FakePlayer("Viewer", new Loc(new FakeWorld("world"), 0, 64, 0)), MenuSounds.Action.TURN);

      assertThat(link.sent).containsExactly("offer " + first.id());
      assertThat(link.offers.getFirst().required()).isFalse();
      assertThat(heard).containsExactly(MenuSounds.Action.TURN);
      assertThat(PackPitch.steps(player)).contains("\n");
    } finally {
      install(previous);
    }
  }

  @Test
  void theDeclineStepsFollowTheJoinNudgeSwitch() {
    players.joinNudge = false;
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, URL);
    offers.joined(player);
    runDelayed();
    offers.status(player, first.id(), PackState.DECLINED);

    assertThat(players.told).isEmpty();
  }

  @Test
  void aBuiltPackNothingHostsIsReportedAsUnhosted() {
    PackOffers offers = offers(PackOffers.Policy.JOIN);
    offers.published(first, null);
    offers.joined(player);
    runDelayed();
    offers.opened(player);

    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.UNHOSTED);
    assertThat(link.sent).isEmpty();
    assertThat(players.told).isEmpty();

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
    players.joinNudge = false;
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
    assertThat(players.told).isEmpty();
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
    PackViewers merged = PackViewers.merged(players.bedrock::contains);
    PackOffers offers = new PackOffers(link, merged, players, players, this::delay, () -> now, PackOffers.Policy.JOIN, WINDOW);
    UUID bedrockPlayer = UUID.randomUUID();
    players.bedrock.add(bedrockPlayer);

    offers.joined(player);
    runDelayed();
    offers.status(player, UUID.randomUUID(), PackState.LOADED);
    offers.status(bedrockPlayer, UUID.randomUUID(), PackState.LOADED);

    assertThat(link.sent).isEmpty();
    assertThat(players.told).isEmpty();
    assertThat(offers.requested(player)).isEqualTo(PackOffers.Result.UNAVAILABLE);
    assertThat(link.sent).isEmpty();
    assertThat(merged.loaded(player)).isTrue();
    assertThat(merged.loaded(bedrockPlayer)).isFalse();
  }

  /** The default config with every switch in the pack section turned off and offering set to off. */
  private static AdaptConfig switchedOff() throws IOException {
    String canonical = TomlCodec.toToml(new AdaptConfig(), "core-config");
    int start = canonical.indexOf("\n[pack]\n");
    int end = canonical.indexOf("\n[", start + 1);
    String section = canonical.substring(start, end < 0 ? canonical.length() : end)
        .replaceAll("(?m)^offer = \"join\"$", "offer = \"off\"")
        .replaceAll("(?m)^(reofferAfterDecline|joinNudge|menuNudge|menuItem|mentionMod|strongPrompt|uiSounds) = true$", "$1 = false");
    String edited = canonical.substring(0, start) + section + (end < 0 ? "" : canonical.substring(end));
    AdaptConfig config = TomlCodec.fromToml(edited, AdaptConfig.class);
    AdaptConfig.Pack pack = config.getPack();
    assertThat(pack.offerPolicy()).isEqualTo(PackOffers.Policy.OFF);
    assertThat(List.of(pack.isReofferAfterDecline(), pack.isJoinNudge(), pack.isMenuNudge(), pack.isMenuItem(), pack.isMentionMod(),
        pack.isStrongPrompt(), pack.isUiSounds(), pack.isRequired())).containsOnly(false);
    return config;
  }

  private static AdaptConfig install(AdaptConfig config) throws ReflectiveOperationException {
    Field field = AdaptConfig.class.getDeclaredField("config");
    field.setAccessible(true);
    AdaptConfig previous = (AdaptConfig) field.get(null);
    field.set(null, config);
    return previous;
  }

  private PackOffers offers(PackOffers.Policy policy) {
    return new PackOffers(link, viewers, players, players, this::delay, () -> now, policy, WINDOW);
  }

  private void delay(UUID playerId, long millis, Runnable task) {
    delayed.add(new Delayed(millis, task));
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

  private static final class FakePlayers implements PackOffers.Players, PackOffers.Switches {
    private final Set<UUID> mod = new HashSet<>();
    private final Set<UUID> bedrock = new HashSet<>();
    private final Set<UUID> optional = new HashSet<>();
    private final Map<UUID, String> declined = new HashMap<>();
    private final List<String> told = new ArrayList<>();
    private boolean ready = true;
    private boolean required;
    private boolean reoffer = true;
    private boolean joinNudge = true;
    private boolean menuNudge = true;

    @Override
    public boolean ready(UUID playerId) {
      return ready;
    }

    @Override
    public boolean clientMod(UUID playerId) {
      return mod.contains(playerId);
    }

    @Override
    public boolean bedrock(UUID playerId) {
      return bedrock.contains(playerId);
    }

    @Override
    public boolean optional(UUID playerId) {
      return optional.contains(playerId);
    }

    @Override
    public boolean required() {
      return required;
    }

    @Override
    public String prompt(UUID playerId) {
      return "prompt " + playerId;
    }

    @Override
    public boolean reoffer() {
      return reoffer;
    }

    @Override
    public boolean joinNudge() {
      return joinNudge;
    }

    @Override
    public boolean menuNudge() {
      return menuNudge;
    }

    @Override
    public String declined(UUID playerId) {
      return declined.get(playerId);
    }

    @Override
    public void declined(UUID playerId, String sha1Hex) {
      if (sha1Hex == null) {
        declined.remove(playerId);
      } else {
        declined.put(playerId, sha1Hex);
      }
    }

    @Override
    public void invite(UUID playerId) {
      told.add("invite");
    }

    @Override
    public void steps(UUID playerId) {
      told.add("steps");
    }

    @Override
    public void kick(UUID playerId) {
      told.add("kick");
    }
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
    public void disconnect(UUID playerId, String message) {
      sent.add("disconnect");
    }

    @Override
    public void listen(StatusListener listener) {
    }
  }
}
