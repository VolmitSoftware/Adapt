package art.arcane.adapt.pack.screen;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.pack.PackResources;
import art.arcane.adapt.pack.menu.PackMenuHost;
import art.arcane.adapt.pack.surface.PackPrompts;
import art.arcane.adapt.pack.surface.PackSession;
import art.arcane.adapt.pack.surface.PackSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.MenuSession;
import art.arcane.adapt.util.common.misc.MenuSounds;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The skill screens for pack viewers: Skills, a skill's page, an adaptation's page and the Mutate spread, drawn from
 * the view models the native client gets and acting through {@link PackActions}. One page per viewer; moving between
 * screens updates the open window in place. State lives until the window closes. A platform installs the running
 * instance through {@link AdaptPorts}.
 */
public final class PackScreens {
  static final long SEAL_WINDOW_MS = 3_000L;
  static final int SEAL_WINDOW_TICKS = 60;
  /** The seal fills one pair of segments from its outer ends this often after it arms, and drains the same way. */
  static final int SEAL_STEP_TICKS = 8;
  static final long SEAL_STEP_MS = SEAL_STEP_TICKS * 50L;
  static final int SEARCH_LENGTH = 32;
  static final long SAME_ACTION_MS = 50L;

  private final PackMenuHost host;
  private final PackViews views;
  private final PackActions actions;
  private final PackPrompts prompts;
  private final PackClock clock;
  private final Speaker speaker;
  private final Map<UUID, Long> heard = new ConcurrentHashMap<>();
  private final Map<UUID, PackViewer> viewers = new ConcurrentHashMap<>();

  public PackScreens(PackMenuHost host, PackViews views, PackActions actions, PackPrompts prompts, PackClock clock,
                     Speaker speaker) {
    this.host = Objects.requireNonNull(host, "host");
    this.views = Objects.requireNonNull(views, "views");
    this.actions = Objects.requireNonNull(actions, "actions");
    this.prompts = Objects.requireNonNull(prompts, "prompts");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.speaker = Objects.requireNonNull(speaker, "speaker");
  }

  /** Plays a menu sound to one viewer. */
  @FunctionalInterface
  public interface Speaker {
    /** Through {@link MenuSounds}, while {@code pack.uiSounds} is on. */
    Speaker SERVER = switched(MenuSounds::play);

    /** Plays through {@code inner} while {@code pack.uiSounds} is on, read on every sound so a reload applies. */
    static Speaker switched(Speaker inner) {
      return (PlayerRef viewer, MenuSounds.Action action) -> {
        if (AdaptConfig.get().getPack().isUiSounds()) {
          inner.play(viewer, action);
        }
      };
    }

    void play(PlayerRef viewer, MenuSounds.Action action);
  }

  /** The installed screens, or null when the platform runs none. */
  public static PackScreens current() {
    return AdaptPorts.get(PackScreens.class, null);
  }

  /** True when {@code viewer} gets pack screens. */
  public boolean book(PlayerRef viewer) {
    return host.book(viewer);
  }

  public boolean openSkills(PlayerRef viewer) {
    return book(viewer) && go(viewer, PackViewer.Screen.SKILLS, "", "");
  }

  /** Opens the page of {@code skillId} when the player may open it. */
  public boolean openSkill(PlayerRef viewer, String skillId) {
    PlayerView player = book(viewer) ? views.player(viewer) : null;
    return player != null && player.access().canOpen(skillId) && go(viewer, PackViewer.Screen.SKILL, skillId, "");
  }

  /** Opens the page of {@code adaptationId} when its skill's page shows it to the player. */
  public boolean openAdaptation(PlayerRef viewer, String adaptationId) {
    if (!book(viewer)) {
      return false;
    }
    CatalogIndex catalog = views.catalog(viewer);
    PlayerView player = views.player(viewer);
    AdaptationView adaptation = catalog == null ? null : catalog.adaptation(adaptationId);
    if (adaptation == null || player == null || !player.access().shows(adaptation.skillId(), adaptationId)) {
      return false;
    }
    return go(viewer, PackViewer.Screen.ADAPTATION, adaptation.skillId(), adaptationId);
  }

  /** Opens the Mutate spread led by {@code lead} when that skill can lead a mutation. */
  public boolean openMutate(PlayerRef viewer, String lead) {
    MutationSpreadView spread = book(viewer) ? views.spread(viewer, lead, "") : null;
    if (spread == null) {
      return false;
    }
    PackViewer state = state(viewer);
    state.go(PackViewer.Screen.MUTATE);
    state.skillId = lead;
    state.adaptationId = "";
    state.spread = spread;
    return show(viewer, state);
  }

  /** Opens the Mutate lead picker: the skills the viewer may lead a mutation with, each opening its spread. */
  public boolean openMutateLeads(PlayerRef viewer) {
    List<String> leads = book(viewer) ? views.mutationLeads(viewer) : List.of();
    if (leads.isEmpty()) {
      return false;
    }
    PackViewer state = state(viewer);
    state.go(PackViewer.Screen.MUTATE);
    state.skillId = "";
    state.adaptationId = "";
    state.spread = null;
    state.leads = List.copyOf(leads);
    return show(viewer, state);
  }

  /** Redraws the viewer's open page on the next tick, once however often it is called. */
  public void changed(PlayerRef viewer) {
    PackViewer state = viewer == null ? null : viewers.get(viewer.uuid());
    if (state == null || state.refreshDue) {
      return;
    }
    state.refreshDue = true;
    clock.later(viewer, 1, () -> {
      state.refreshDue = false;
      if (viewers.get(viewer.uuid()) == state && state.session != null && state.session.isOpen()) {
        show(viewer, state);
      }
    });
  }

  /** The periodic check: redraws when the player's stored numbers or the catalog moved since the page was drawn. */
  public void refresh(PlayerRef viewer) {
    PackViewer state = viewer == null ? null : viewers.get(viewer.uuid());
    if (state == null) {
      return;
    }
    CatalogIndex catalog = views.catalog(viewer);
    if (views.fingerprint(viewer) != state.fingerprint
        || (catalog != null && catalog.catalog().revision() != state.catalogRevision)) {
      changed(viewer);
    }
  }

  PackViews views() {
    return views;
  }

  long now() {
    return clock.now();
  }

  void route(PlayerRef viewer, Route route) {
    sound(viewer, MenuSounds.Action.OPEN);
    actions.open(viewer, route);
  }

  /** Back to the page above, from a back control. */
  void back(PlayerRef viewer, Route route) {
    sound(viewer, MenuSounds.Action.BACK);
    actions.open(viewer, route);
  }

  void page(PlayerRef viewer, int page) {
    PackViewer state = viewers.get(viewer.uuid());
    if (state != null) {
      sound(viewer, MenuSounds.Action.TURN);
      state.page = Math.max(0, page);
      state.notice = "";
      show(viewer, state);
    }
  }

  void tab(PlayerRef viewer, int tab) {
    PackViewer state = viewers.get(viewer.uuid());
    if (state != null) {
      sound(viewer, MenuSounds.Action.TOGGLE);
      state.tab = Math.max(0, tab);
      state.page = 0;
      state.pickedLevel = -1;
      state.notice = "";
      show(viewer, state);
    }
  }

  void close(PlayerRef viewer) {
    PackViewer state = viewers.remove(viewer.uuid());
    if (state != null && state.session != null && state.session.isOpen()) {
      state.session.close();
    }
  }

  void pick(PlayerRef viewer, int level) {
    PackViewer state = viewers.get(viewer.uuid());
    if (state != null) {
      sound(viewer, MenuSounds.Action.TOGGLE);
      state.pickedLevel = level;
      state.notice = "";
      show(viewer, state);
    }
  }

  void recipe(PlayerRef viewer, int recipe) {
    PackViewer state = viewers.get(viewer.uuid());
    if (state != null) {
      sound(viewer, MenuSounds.Action.TOGGLE);
      state.recipe = Math.max(0, recipe);
      show(viewer, state);
    }
  }

  void learn(PlayerRef viewer, String adaptationId, int level) {
    quiet(viewer);
    answer(viewer, actions.learn(viewer, adaptationId, level));
  }

  void unlearn(PlayerRef viewer, String adaptationId, int level) {
    quiet(viewer);
    answer(viewer, actions.unlearn(viewer, adaptationId, level));
  }

  void preference(PlayerRef viewer, PreferenceOwner owner, String ownerId, String preferenceId, String value) {
    boolean saved = actions.setPreference(viewer, owner, ownerId, preferenceId, value);
    sound(viewer, saved ? MenuSounds.Action.TOGGLE : MenuSounds.Action.REFUSE);
    notice(viewer, AdaptLanguage.text(saved ? GuiMessages.CLIENT_PREFERENCE_SAVED : GuiMessages.CLIENT_PREFERENCE_DENIED));
  }

  void reset(PlayerRef viewer, PreferenceOwner owner, String ownerId) {
    boolean saved = actions.resetPreference(viewer, owner, ownerId);
    sound(viewer, saved ? MenuSounds.Action.TOGGLE : MenuSounds.Action.REFUSE);
    notice(viewer, AdaptLanguage.text(saved ? GuiMessages.CLIENT_PREFERENCE_SAVED : GuiMessages.CLIENT_PREFERENCE_DENIED));
  }

  void unmutate(PlayerRef viewer, String skillId, PackSlot slot) {
    armed(viewer, PackSkillScreen.SPLIT, slot, GuiMessages.PACK_CONFIRM_SPLIT, () -> unmutateNow(viewer, skillId));
  }

  private void unmutateNow(PlayerRef viewer, String skillId) {
    String text = actions.unmutate(viewer, skillId);
    if (text == null) {
      refuse(viewer, AdaptLanguage.text(GuiMessages.CLIENT_UNAVAILABLE));
      return;
    }
    sound(viewer, MenuSounds.Action.BACK);
    PackViewer state = viewers.get(viewer.uuid());
    if (state != null) {
      state.go(PackViewer.Screen.SKILLS);
      state.notice = text;
      show(viewer, state);
    }
  }

  /** Shows the partner candidates in place of the spread, where the window has no room for both. */
  void partners(PlayerRef viewer, boolean shown) {
    PackViewer state = viewers.get(viewer.uuid());
    if (state != null) {
      sound(viewer, MenuSounds.Action.TOGGLE);
      state.partners = shown;
      state.page = 0;
      show(viewer, state);
    }
  }

  /** The Mutate spread with {@code partner} chosen, empty for none. */
  void partner(PlayerRef viewer, String lead, String partner) {
    PackViewer state = viewers.get(viewer.uuid());
    MutationSpreadView spread = views.spread(viewer, lead, partner);
    if (state == null || spread == null) {
      refuse(viewer, AdaptLanguage.text(GuiMessages.CLIENT_UNAVAILABLE));
      return;
    }
    sound(viewer, MenuSounds.Action.TOGGLE);
    state.skillId = lead;
    state.spread = spread;
    state.partners = false;
    state.page = 0;
    state.armedUntil = 0L;
    state.armedControl = "";
    state.notice = "";
    show(viewer, state);
  }

  /**
   * Arm-then-confirm: the first click arms {@code control} for {@link #SEAL_WINDOW_MS} and shows {@code label}, a second
   * click on the same control inside that window runs {@code confirmed}. A click on another control arms that one
   * instead. {@code slot}, when not null, plays the armed wash.
   */
  void armed(PlayerRef viewer, String control, PackSlot slot, TextKey label, Runnable confirmed) {
    PackViewer state = viewers.get(viewer.uuid());
    if (state == null) {
      return;
    }
    if (state.armed(control, clock.now())) {
      state.armedUntil = 0L;
      state.armedControl = "";
      confirmed.run();
      return;
    }
    sound(viewer, MenuSounds.Action.ARM);
    state.armedUntil = clock.now() + SEAL_WINDOW_MS;
    state.armedControl = control;
    state.notice = AdaptLanguage.text(label);
    long armed = state.armedUntil;
    show(viewer, state);
    if (slot != null && state.session != null && state.session.isOpen()) {
      state.session.oneShot(slot, SEAL_WINDOW_TICKS);
    }
    clock.later(viewer, SEAL_WINDOW_TICKS, () -> {
      if (viewers.get(viewer.uuid()) == state && state.armedUntil == armed && state.session != null
          && state.session.isOpen()) {
        state.armedUntil = 0L;
        state.armedControl = "";
        state.notice = "";
        show(viewer, state);
      }
    });
  }

  /**
   * The seal of the spread: arms, then merges the chosen partner into the lead. While armed it fills a pair of segments
   * from its outer ends every {@link #SEAL_STEP_TICKS} ticks and drains the same way before its window runs out.
   */
  void seal(PlayerRef viewer, PackSlot mark) {
    PackViewer state = viewers.get(viewer.uuid());
    if (state == null || state.spread == null || state.spread.partner().isEmpty()) {
      return;
    }
    armed(viewer, PackMutateScreen.SEAL, mark, GuiMessages.PACK_CONFIRM_MUTATE, () -> mutate(viewer, state));
    long armed = state.armedUntil;
    if (armed <= clock.now()) {
      return;
    }
    for (int tick = SEAL_STEP_TICKS; tick < SEAL_WINDOW_TICKS; tick += SEAL_STEP_TICKS) {
      clock.later(viewer, tick, () -> {
        if (viewers.get(viewer.uuid()) == state && state.armedUntil == armed && state.session != null && state.session.isOpen()) {
          show(viewer, state);
        }
      });
    }
  }

  /**
   * How many segments from each outer end of the seal are filled now: one more every step after arming, one fewer
   * every step in the last steps of the window, none when disarmed.
   */
  int sealFill(PackViewer state) {
    long now = clock.now();
    if (!state.armed(PackMutateScreen.SEAL, now)) {
      return 0;
    }
    long elapsed = now - (state.armedUntil - SEAL_WINDOW_MS);
    long left = state.armedUntil - now;
    return (int) Math.max(0L, Math.min(1L + (elapsed / SEAL_STEP_MS), left / SEAL_STEP_MS));
  }

  private void mutate(PlayerRef viewer, PackViewer state) {
    PackActions.Mutation mutation = actions.mutate(viewer, state.spread.lead(), state.spread.partner());
    if (mutation == null) {
      refuse(viewer, AdaptLanguage.text(GuiMessages.CLIENT_UNAVAILABLE));
      return;
    }
    state.notice = mutation.text();
    state.spread = mutation.spread();
    if (mutation.mutated()) {
      quiet(viewer);
      actions.open(viewer, new Route.Skill(mutation.spread().mutatedSkillId()));
      return;
    }
    sound(viewer, MenuSounds.Action.REFUSE);
    show(viewer, state);
  }

  void search(PlayerRef viewer) {
    PackViewer state = viewers.get(viewer.uuid());
    if (state == null || !prompts.available(viewer)) {
      return;
    }
    sound(viewer, MenuSounds.Action.TOGGLE);
    prompts.text(viewer, AdaptLanguage.text(GuiMessages.PACK_SEARCH), state.search, SEARCH_LENGTH, (String query) -> {
      sound(viewer, MenuSounds.Action.TOGGLE);
      state.search = query.strip();
      state.page = 0;
      show(viewer, state);
    }, () -> show(viewer, state));
  }

  /**
   * One sound per action: a second cue within {@link #SAME_ACTION_MS} belongs to the same click and stays silent, so a
   * click that turns a page and redraws plays once. Redraws, retitles and animation frames never call this.
   */
  private void sound(PlayerRef viewer, MenuSounds.Action action) {
    if (fresh(viewer)) {
      speaker.play(viewer, action);
    }
  }

  /** The action plays its own sound through the shared operation, as learning and a finished mutation do. */
  private void quiet(PlayerRef viewer) {
    fresh(viewer);
  }

  private boolean fresh(PlayerRef viewer) {
    long now = clock.now();
    Long previous = heard.put(viewer.uuid(), now);
    return previous == null || now - previous >= SAME_ACTION_MS || now < previous;
  }

  /** Shows why an action was turned down, with the refused sound. */
  void refuse(PlayerRef viewer, String text) {
    sound(viewer, MenuSounds.Action.REFUSE);
    notice(viewer, text);
  }

  /** Opens the Mutate spread led by {@code lead} from a click on the lead picker. */
  void lead(PlayerRef viewer, String lead) {
    sound(viewer, MenuSounds.Action.OPEN);
    openMutate(viewer, lead);
  }

  void notice(PlayerRef viewer, String text) {
    PackViewer state = viewers.get(viewer.uuid());
    if (state != null) {
      state.notice = text;
      show(viewer, state);
    }
  }

  private void answer(PlayerRef viewer, PackActions.Outcome outcome) {
    PackViewer state = viewers.get(viewer.uuid());
    if (state != null) {
      state.pickedLevel = -1;
    }
    notice(viewer, outcome.message().isEmpty() ? AdaptLanguage.text(GuiMessages.CLIENT_NO_CHANGE) : outcome.message());
  }

  private boolean go(PlayerRef viewer, PackViewer.Screen screen, String skillId, String adaptationId) {
    PackViewer state = state(viewer);
    state.go(screen);
    state.skillId = skillId;
    state.adaptationId = adaptationId;
    state.spread = null;
    return show(viewer, state);
  }

  private PackViewer state(PlayerRef viewer) {
    PackViewer state = viewers.get(viewer.uuid());
    if (state == null || state.session == null || !state.session.isOpen()) {
      state = new PackViewer();
    }
    return state;
  }

  private boolean show(PlayerRef viewer, PackViewer state) {
    PackResources resources = host.resources();
    if (resources == null) {
      return false;
    }
    try (LanguageAudience.Scope audience = LanguageAudience.open(viewer.uuid())) {
      CatalogIndex catalog = views.catalog(viewer);
      PlayerView player = views.player(viewer);
      if (catalog == null || player == null) {
        return false;
      }
      boolean lower = host.lowerSlots(viewer);
      PackFrame frame = new PackFrame(this, viewer, state, resources, lower, catalog, player);
      PackPage page = switch (state.screen) {
        case SKILLS -> PackSkillsScreen.render(frame);
        case SKILL -> PackSkillScreen.render(frame);
        case ADAPTATION -> PackAdaptationScreen.render(frame);
        case MUTATE -> PackMutateScreen.render(frame);
      };
      if (page == null) {
        if (state.session != null && state.session == host.window(viewer)) {
          close(viewer);
        }
        return false;
      }
      state.fingerprint = views.fingerprint(viewer);
      state.catalogRevision = catalog.catalog().revision();
      if (!state.notice.isEmpty()) {
        viewer.sendActionBar(state.notice);
      }
      ChestMenu menu = page.menu().toBuilder().onClose((MenuSession closing) -> closed(viewer, state, closing)).build();
      PackSession open = host.window(viewer);
      if (open != null && open == state.session) {
        host.updatePage(viewer, menu, page.surface());
      } else {
        state.session = host.openPage(viewer, menu, page.surface());
        viewers.put(viewer.uuid(), state);
      }
      return true;
    }
  }

  private void closed(PlayerRef viewer, PackViewer state, MenuSession closing) {
    if (state.session == closing) {
      viewers.remove(viewer.uuid(), state);
      heard.remove(viewer.uuid());
    }
  }
}
