package art.arcane.adapt.content.golem.command;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.command.AdaptCommand;
import art.arcane.adapt.api.command.ArgumentType;
import art.arcane.adapt.api.command.ArgumentTypes;
import art.arcane.adapt.api.command.CommandArgument;
import art.arcane.adapt.api.command.CommandCaller;
import art.arcane.adapt.api.command.CommandInvocation;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.gui.GolemGuiActions;
import art.arcane.adapt.content.golem.gui.RosterGui;
import art.arcane.adapt.content.golem.item.GolemItemKind;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.node.NodeService;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


/**
 * {@code /adapt golem}: the player's roster and the admin tools over other players' golems. Every admin subcommand
 * checks {@link #ADMIN_PERMISSION} itself, so the refusal names the node.
 */
public final class GolemCommand {
  public static final String ADMIN_PERMISSION = "adapt.golemancy.admin";
  public static final String SKILL = "golemancy";
  private static final int MIN_GIVE_AMOUNT = 1;
  private static final int MAX_GIVE_AMOUNT = 64;
  private static final int SHORT_ID_LENGTH = 8;
  private static final String MASTERWORK_HEART = GolemItemKind.MASTERWORK_HEART.id();
  private static final String RETURNS_ALL = "---";
  private static final String RETURNS_LIST = "list";
  private static final Text TARGET_HELP = Text.key("adapt.command.help.target_player_defaults_to_you");

  static final CommandArgument<KnownTarget> LIST_PLAYER = CommandArgument.optional("player", new KnownTargets(), TARGET_HELP);
  static final CommandArgument<String> RELEASE_ID = CommandArgument.required("id", ArgumentTypes.word(),
      Text.key("adapt.command.help.golem_id_or_enough_of_its_first_characters_to_match_one_golem"));
  static final CommandArgument<PlayerRef> PLAYER = CommandArgument.optional("player", ArgumentTypes.onlinePlayer(), TARGET_HELP);
  static final CommandArgument<String> ITEM = CommandArgument.required("item", new GiveItems(),
      Text.key("adapt.command.help.item_id_such_as_crook_core_guardian_node_deposit_rune_might_2_or_masterwork_heart_or_all_for_one_of_each"));
  static final CommandArgument<Integer> AMOUNT = CommandArgument.optional("amount", ArgumentTypes.integer(Integer.MIN_VALUE, Integer.MAX_VALUE),
      Text.key("adapt.command.help.amount_from_1_to_64_defaults_to_1"));
  static final CommandArgument<String> RETURN_ID = CommandArgument.optional("id", ArgumentTypes.word(),
      Text.literal("Return identifier from the pending list"));
  static final CommandArgument<String> OUTCOME = CommandArgument.optional("outcome", ArgumentTypes.word(),
      Text.literal("list, delivered, or missing; missing confirms no item was delivered"));

  /** The command tree players see. */
  public AdaptCommand tree() {
    return AdaptCommand.named("golem")
        .help(Text.key("adapt.command.help.golem_roster_and_admin_tools"))
        .then(AdaptCommand.named("roster").help(Text.key("adapt.command.help.open_your_golem_roster")).playerOnly()
            .executes((CommandInvocation run) -> roster(run.caller())).build())
        .then(AdaptCommand.named("list").permission(ADMIN_PERMISSION).help(Text.key("adapt.command.help.list_a_player_s_golems")).argument(LIST_PLAYER)
            .executes((CommandInvocation run) -> list(run.caller(), run.find(LIST_PLAYER).orElse(null))).build())
        .then(AdaptCommand.named("release").permission(ADMIN_PERMISSION).help(Text.key("adapt.command.help.release_one_of_a_player_s_golems"))
            .argument(RELEASE_ID).argument(PLAYER)
            .executes((CommandInvocation run) -> release(run.caller(), run.get(RELEASE_ID), run.find(PLAYER).orElse(null))).build())
        .then(AdaptCommand.named("wake").permission(ADMIN_PERMISSION).help(Text.key("adapt.command.help.wake_a_player_s_loaded_golems")).argument(PLAYER)
            .executes((CommandInvocation run) -> wake(run.caller(), run.find(PLAYER).orElse(null))).build())
        .then(AdaptCommand.named("give").permission(ADMIN_PERMISSION)
            .help(Text.key("adapt.command.help.give_golemancy_items_for_testing_in_debug_mode_dormant_golems_and_shattered_cores_are_owner_bound_and_excluded"))
            .argument(ITEM).argument(AMOUNT).argument(PLAYER)
            .executes((CommandInvocation run) -> give(run.caller(), run.get(ITEM), run.find(AMOUNT).orElse(MIN_GIVE_AMOUNT),
                run.find(PLAYER).orElse(null))).build())
        .then(AdaptCommand.named("returns").permission(ADMIN_PERMISSION).help(Text.literal("Inspect unresolved node returns or confirm their delivery status"))
            .argument(RETURN_ID).argument(OUTCOME)
            .executes((CommandInvocation run) -> returns(run.caller(), run.find(RETURN_ID).orElse(RETURNS_ALL),
                run.find(OUTCOME).orElse(RETURNS_LIST))).build())
        .build();
  }

  public void roster(CommandCaller caller) {
    Optional<PlayerRef> viewer = caller.player();
    if (viewer.isEmpty() || runningRegistry(caller) == null) {
      return;
    }
    RosterGui.open(viewer.get());
  }

  public void list(CommandCaller caller, KnownTarget player) {
    if (!admin(caller)) {
      return;
    }
    KnownTarget target = player;
    if (target == null) {
      PlayerRef self = senderTarget(caller);
      if (self == null) {
        return;
      }
      target = KnownTarget.of(self);
    }
    KnownTarget chosen = target;
    PlayerRef online = AdaptApi.platform().host().player(chosen.uuid());
    if (online != null) {
      dispatch(online, () -> listOnline(online, caller), caller);
      return;
    }
    UUID audience = caller.player().map(PlayerRef::uuid).orElse(null);
    AdaptApi.scheduler().runAsync(() -> Text.renderFor(audience, () -> listOffline(chosen, caller)));
  }

  public void release(CommandCaller caller, String id, PlayerRef player) {
    PlayerRef target = onlineAdminTarget(player, caller);
    if (target == null) {
      return;
    }
    GolemRegistry registry = runningRegistry(caller);
    if (registry != null) {
      dispatch(target, () -> releaseTarget(registry, id, target, caller), caller);
    }
  }

  public void wake(CommandCaller caller, PlayerRef player) {
    PlayerRef target = onlineAdminTarget(player, caller);
    if (target == null) {
      return;
    }
    GolemRegistry registry = runningRegistry(caller);
    if (registry != null) {
      dispatch(target, () -> wakeTarget(registry, target, caller), caller);
    }
  }

  public void give(CommandCaller caller, String item, int amount, PlayerRef player) {
    if (!admin(caller) || !debugModeAllowed(caller)) {
      return;
    }
    String id = GolemGiveCatalog.normalize(item);
    boolean everything = GolemGiveCatalog.ALL.equals(id);
    if (!everything && !GolemGiveCatalog.isItem(id)) {
      GolemReply.error(Text.key("adapt.command.runtime.golem_give_unknown_item").with("item", item.trim()).resolve()).send(caller);
      return;
    }
    PlayerRef target = player != null ? player : senderTarget(caller);
    if (target == null) {
      return;
    }
    int count = Math.max(MIN_GIVE_AMOUNT, Math.min(MAX_GIVE_AMOUNT, amount));
    dispatch(target, () -> giveTarget(id, everything, count, target, caller), caller);
  }

  public void returns(CommandCaller caller, String id, String outcome) {
    if (!caller.isOperator()) {
      GolemReply.error(Text.key("adapt.command.runtime.operator_only").resolve()).send(caller);
      return;
    }
    if (!admin(caller)) {
      return;
    }
    NodeService nodes = GolemancyRuntime.nodes().orElse(null);
    if (nodes == null) {
      GolemReply.error(Text.key("adapt.command.runtime.skill_disabled").with("skill", SKILL).resolve()).send(caller);
      return;
    }
    List<NodeService.ReturnRecord> pendingReturns = nodes.unresolvedReturns();
    if (RETURNS_LIST.equals(outcome)) {
      GolemReply.info(Text.key("adapt.command.runtime.golem_returns_header").withMarkup("count", String.valueOf(pendingReturns.size())).resolve()).send(caller);
      for (NodeService.ReturnRecord pending : pendingReturns) {
        GolemReply.info(Text.key("adapt.command.runtime.golem_return_entry").withMarkup("id", String.valueOf(NodeService.returnId(pending.key()))).withMarkup("owner", String.valueOf(pending.owner())).withMarkup("type", String.valueOf(pending.type().id())).resolve()).send(caller);
      }
      return;
    }
    if (!"delivered".equals(outcome) && !"missing".equals(outcome)) {
      GolemReply.error(Text.key("adapt.command.runtime.golem_return_outcome").resolve()).send(caller);
      return;
    }
    NodeService.ReturnRecord pending = pendingReturns.stream()
        .filter((NodeService.ReturnRecord candidate) -> NodeService.returnId(candidate.key()).equals(id)).findFirst().orElse(null);
    WorldRef world = pending == null ? null : AdaptApi.platform().host().world(pending.key().world());
    if (world == null) {
      GolemReply.error(Text.key("adapt.command.runtime.golem_return_unavailable").resolve()).send(caller);
      return;
    }
    if (!AdaptApi.scheduler().runAt(pending.key().center(world), () -> {
      boolean resolved = nodes.resolveReturn(pending.owner(), pending.key(), "delivered".equals(outcome));
      (resolved
          ? GolemReply.success(Text.key("adapt.command.runtime.golem_return_resolved").withMarkup("id", String.valueOf(id)).withMarkup("outcome", String.valueOf(outcome)).resolve())
          : GolemReply.error(Text.key("adapt.command.runtime.golem_return_unavailable").resolve())).send(caller);
    })) {
      GolemReply.error(Text.key("adapt.command.runtime.golem_return_unavailable").resolve()).send(caller);
    }
  }

  /** Stacks of the catalog item {@code id}, split by the item's own stack size. */
  public static List<ItemRef> stacks(String id, int amount) {
    ItemRef template = build(id);
    int stackSize = Math.max(1, template.maxStackSize());
    List<ItemRef> stacks = new ArrayList<>((amount + stackSize - 1) / stackSize);
    for (int remaining = amount; remaining > 0; remaining -= stackSize) {
      stacks.add(template.copyWithAmount(Math.min(remaining, stackSize)));
    }
    return stacks;
  }

  /** One of every catalog item. */
  public static List<ItemRef> everything() {
    List<ItemRef> everything = new ArrayList<>(GolemGiveCatalog.ids().size());
    for (String id : GolemGiveCatalog.ids()) {
      everything.add(build(id));
    }
    return everything;
  }

  /** Runs {@code task} on the thread that owns {@code target}, at once when this thread already does. */
  static boolean onTargetThread(PlayerRef target, Runnable task) {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(task, "task");
    if (!AdaptApi.scheduler().isRegionized() && AdaptApi.scheduler().isTickThread()) {
      task.run();
      return true;
    }
    return AdaptApi.scheduler().runEntity(target, task);
  }

  private static void dispatch(PlayerRef target, Runnable task, CommandCaller caller) {
    if (!onTargetThread(target, task)) {
      GolemReply.error(Text.key("adapt.command.runtime.target_dispatch_failed").resolve()).send(caller);
    }
  }

  private static ItemRef build(String id) {
    if (MASTERWORK_HEART.equals(id)) {
      return GolemItems.masterworkHeart();
    }
    return GolemItems.resolve(Id.of(GolemItems.NAMESPACE, id));
  }

  private void listOnline(PlayerRef target, CommandCaller caller) {
    GolemRoster roster = readyRoster(target, caller);
    if (roster != null) {
      report(roster, target.name(), caller);
    }
  }

  private void listOffline(KnownTarget target, CommandCaller caller) {
    if (!AdaptApi.isRunning()) {
      GolemReply.error(Text.key("adapt.command.runtime.adapt_server_not_ready").resolve()).send(caller);
      return;
    }
    report(AdaptApi.players().peek(target.uuid(), GolemRoster.section()), target.displayName(), caller);
  }

  private void report(GolemRoster roster, String name, CommandCaller caller) {
    List<GolemRoster.GolemEntry> golems = RosterGui.entries(roster);
    if (golems.isEmpty()) {
      GolemReply.info(Text.key("adapt.command.runtime.golem_list_empty").with("player", name).resolve()).send(caller);
      return;
    }
    List<String> lines = new ArrayList<>(golems.size() + 1);
    lines.add(golems.size() == 1
        ? Text.key("adapt.command.runtime.golem_list_header_one").with("player", name).resolve()
        : Text.key("adapt.command.runtime.golem_list_header").with("player", name).withMarkup("count", String.valueOf(golems.size())).resolve());
    for (GolemRoster.GolemEntry entry : golems) {
      lines.add(describe(entry));
    }
    GolemReply.report(lines).send(caller);
  }

  private void releaseTarget(GolemRegistry registry, String id, PlayerRef target, CommandCaller caller) {
    GolemRoster roster = readyRoster(target, caller);
    if (roster == null) {
      return;
    }
    List<GolemRoster.GolemEntry> matches = roster.matching(id);
    if (matches.size() != 1) {
      GolemReply.error((matches.isEmpty() ? Text.key("adapt.command.runtime.golem_not_found") : Text.key("adapt.command.runtime.golem_ambiguous")).with("player", target.name()).with("id", id).resolve()).send(caller);
      return;
    }
    UUID golem = matches.getFirst().id();
    String shortId = shortId(golem);
    String name = target.name();
    registry.releaseFor(target.uuid(), golem).thenAccept((GolemRegistry.AdminRelease release) -> (switch (release) {
      case RELEASED -> GolemReply.success(Text.key("adapt.command.runtime.golem_released").withMarkup("id", String.valueOf(shortId)).with("player", name).resolve());
      case ACTIVE_UNLOADED -> GolemReply.error(Text.key("adapt.command.runtime.golem_release_active_unloaded").withMarkup("id", String.valueOf(shortId)).with("player", name).resolve());
      case REFUSED -> GolemReply.error(Text.key("adapt.command.runtime.golem_release_failed").withMarkup("id", String.valueOf(shortId)).with("player", name).resolve());
    }).send(caller));
  }

  private void wakeTarget(GolemRegistry registry, PlayerRef target, CommandCaller caller) {
    if (readyRoster(target, caller) == null) {
      return;
    }
    int loaded = registry.wake(target.uuid());
    GolemReply.success(Text.key("adapt.command.runtime.golems_woken").with("player", target.name()).withMarkup("count", String.valueOf(loaded)).resolve()).send(caller);
  }

  private void giveTarget(String id, boolean everything, int amount, PlayerRef target, CommandCaller caller) {
    List<ItemRef> stacks = everything ? everything() : stacks(id, amount);
    int dropped = hand(target, stacks);
    String name = target.name();
    GolemReply.success(everything
        ? Text.key("adapt.command.runtime.golem_given_all").with("player", name).withMarkup("count", String.valueOf(stacks.size())).resolve()
        : Text.key("adapt.command.runtime.golem_given").withMarkup("amount", String.valueOf(amount)).withMarkup("item", String.valueOf(id)).with("player", name).resolve())
        .send(caller);
    if (dropped > 0) {
      GolemReply.info(Text.key("adapt.command.runtime.golem_give_dropped").with("player", name).withMarkup("count", String.valueOf(dropped)).resolve()).send(caller);
    }
  }

  private static int hand(PlayerRef target, List<ItemRef> stacks) {
    int dropped = 0;
    for (ItemRef stack : stacks) {
      for (ItemRef leftover : target.inventory().addItem(stack)) {
        target.world().dropItemNaturally(target.location().position(), leftover);
        dropped += leftover.amount();
      }
    }
    return dropped;
  }

  private static String describe(GolemRoster.GolemEntry entry) {
    String golem = Text.key("adapt.golemancy.roster.entry").with("body", entry.body().displayName()).with("core", entry.core().displayName()).resolve();
    return Text.key("adapt.command.runtime.golem_list_entry").withMarkup("id", String.valueOf(shortId(entry.id()))).withMarkup("golem", String.valueOf(golem)).with("status", GolemGuiActions.statusName(entry.status())).with("location", RosterGui.lastSeen(entry)).resolve();
  }

  private static String shortId(UUID id) {
    return id.toString().substring(0, SHORT_ID_LENGTH);
  }

  private static GolemRegistry runningRegistry(CommandCaller caller) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      GolemReply.error(Text.key("adapt.command.runtime.skill_disabled").with("skill", SKILL).resolve()).send(caller);
      return null;
    }
    return registry.get();
  }

  private static GolemRoster readyRoster(PlayerRef target, CommandCaller caller) {
    Optional<PlayerProfile> profile = AdaptApi.isRunning() ? AdaptApi.players().find(target.uuid()) : Optional.empty();
    if (profile.isPresent() && profile.get().isReady() && target.equals(profile.get().player())) {
      return profile.get().section(GolemRoster.section());
    }
    GolemReply.error(Text.key("adapt.command.runtime.adapt_server_not_ready").resolve()).send(caller);
    return null;
  }

  private static PlayerRef onlineAdminTarget(PlayerRef player, CommandCaller caller) {
    if (!admin(caller)) {
      return null;
    }
    return player != null ? player : senderTarget(caller);
  }

  private static boolean admin(CommandCaller caller) {
    if (caller.hasPermission(ADMIN_PERMISSION)) {
      return true;
    }
    GolemReply.error(Text.key("adapt.command.runtime.missing_permission").withMarkup("permission", String.valueOf(ADMIN_PERMISSION)).resolve()).send(caller);
    return false;
  }

  private static boolean debugModeAllowed(CommandCaller caller) {
    Optional<PlayerRef> player = caller.player();
    if (player.isEmpty() || AdaptApi.players().isDebugging(player.get().uuid())) {
      return true;
    }
    GolemReply.error(Text.key("adapt.command.runtime.golem_give_debug_required").resolve()).send(caller);
    return false;
  }

  private static PlayerRef senderTarget(CommandCaller caller) {
    Optional<PlayerRef> player = caller.player();
    if (player.isPresent()) {
      return player.get();
    }
    GolemReply.error(Text.key("adapt.command.runtime.player_required_from_console").resolve()).send(caller);
    return null;
  }

  /** A command target that may be offline: its id and last known name, which is null when the server never saw one. */
  public record KnownTarget(UUID uuid, String name) {
    public KnownTarget {
      Objects.requireNonNull(uuid, "uuid");
    }

    static KnownTarget of(PlayerRef player) {
      return new KnownTarget(player.uuid(), player.name());
    }

    /** The name, or the id when no name is known. */
    public String displayName() {
      return name == null ? uuid.toString() : name;
    }
  }

  /** An online player by name, or any player the server knows by exact name or UUID; completes online names. */
  private static final class KnownTargets implements ArgumentType<KnownTarget> {
    @Override
    public Class<KnownTarget> valueType() {
      return KnownTarget.class;
    }

    @Override
    public Optional<KnownTarget> parse(CommandCaller caller, String text) {
      String value = text.trim();
      if (value.isEmpty()) {
        return Optional.empty();
      }
      PlatformHost host = AdaptApi.platform().host();
      PlayerRef online = host.player(value);
      if (online != null) {
        return Optional.of(KnownTarget.of(online));
      }
      if (value.length() == 36 && value.indexOf('-') == 8) {
        try {
          UUID id = UUID.fromString(value);
          return Optional.of(new KnownTarget(id, host.knownPlayerName(id)));
        } catch (IllegalArgumentException invalid) {
          return Optional.empty();
        }
      }
      UUID cached = host.knownPlayerId(value);
      if (cached == null) {
        return Optional.empty();
      }
      String name = host.knownPlayerName(cached);
      return Optional.of(new KnownTarget(cached, name == null ? value : name));
    }

    @Override
    public List<String> suggest(CommandCaller caller, String typed) {
      List<String> names = new ArrayList<>();
      for (PlayerRef player : AdaptApi.platform().host().onlinePlayers()) {
        names.add(player.name());
      }
      return names;
    }
  }

  /** Any word; completes the give catalog ids that start with what was typed. */
  static final class GiveItems implements ArgumentType<String> {
    @Override
    public Class<String> valueType() {
      return String.class;
    }

    @Override
    public Optional<String> parse(CommandCaller caller, String text) {
      return Optional.of(text);
    }

    @Override
    public List<String> suggest(CommandCaller caller, String typed) {
      String prefix = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
      List<String> matching = new ArrayList<>();
      for (String id : GolemGiveCatalog.completions()) {
        if (id.startsWith(prefix)) {
          matching.add(id);
        }
      }
      return matching;
    }
  }
}
