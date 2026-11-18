package art.arcane.adapt.command;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.gui.GolemGuiActions;
import art.arcane.adapt.api.golem.gui.RosterGui;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.command.Feedback;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.localization.LanguageAudience;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

/** The golemancy runtime behind {@code /adapt golem}, reached after the command checked permissions and resolved its targets. */
public final class GolemCommands {
  static final GolemCommands INSTANCE = new GolemCommands();
  private static final int SHORT_ID_LENGTH = 8;
  private static final String MASTERWORK_HEART = GolemItemKind.MASTERWORK_HEART.id();

  private GolemCommands() {
  }

  public void roster(CommandSource sender, PlayerRef viewer) {
    if (runningRegistry(sender) == null) {
      return;
    }
    RosterGui.open(viewer);
  }

  public void list(CommandSource sender, KnownPlayer target) {
    PlayerRef online = target.online();
    if (online != null) {
      CommandTargetExecutor.run(online, () -> listOnline(online, sender), sender);
      return;
    }
    UUID audience = sender.player().map(PlayerRef::uuid).orElse(null);
    Tasks.a(() -> LanguageAudience.run(audience, () -> listOffline(target, sender)));
  }

  public void release(CommandSource sender, String id, PlayerRef target) {
    GolemRegistry registry = runningRegistry(sender);
    if (registry == null) {
      return;
    }
    CommandTargetExecutor.run(target, () -> releaseTarget(registry, id, target, sender), sender);
  }

  public void wake(CommandSource sender, PlayerRef target) {
    GolemRegistry registry = runningRegistry(sender);
    if (registry == null) {
      return;
    }
    CommandTargetExecutor.run(target, () -> wakeTarget(registry, target, sender), sender);
  }

  public void give(CommandSource sender, String id, boolean everything, int amount, PlayerRef target) {
    CommandTargetExecutor.run(target, () -> giveTarget(id, everything, amount, target, sender), sender);
  }

  public void returns(CommandSource sender, String id, String outcome) {
    NodeService nodes = GolemancyRuntime.nodes().orElse(null);
    if (nodes == null) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.SKILL_DISABLED, untrusted("skill", CommandGolem.SKILL))).send(sender);
      return;
    }
    List<NodeService.ReturnRecord> returns = nodes.unresolvedReturns();
    if ("list".equals(outcome)) {
      CommandTargetExecutor.send(sender, FConst.info(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RETURNS_HEADER, trusted("count", returns.size()))));
      for (NodeService.ReturnRecord pending : returns) {
        CommandTargetExecutor.send(sender, FConst.info(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RETURN_ENTRY,
            trusted("id", NodeService.returnId(pending.key())), trusted("owner", pending.owner()), trusted("type", pending.type().id()))));
      }
      return;
    }
    if (!"delivered".equals(outcome) && !"missing".equals(outcome)) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RETURN_OUTCOME)));
      return;
    }
    NodeService.ReturnRecord pending = returns.stream().filter(candidate -> NodeService.returnId(candidate.key()).equals(id)).findFirst().orElse(null);
    WorldRef world = pending == null ? null : AdaptCore.platform().host().world(pending.key().world());
    if (world == null) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RETURN_UNAVAILABLE)));
      return;
    }
    if (!Tasks.runAt(pending.key().center(world), () -> {
      boolean resolved = nodes.resolveReturn(pending.owner(), pending.key(), "delivered".equals(outcome));
      CommandTargetExecutor.send(sender, resolved
          ? FConst.success(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RETURN_RESOLVED, trusted("id", id), trusted("outcome", outcome)))
          : FConst.error(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RETURN_UNAVAILABLE)));
    })) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RETURN_UNAVAILABLE)));
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

  private static ItemRef build(String id) {
    if (MASTERWORK_HEART.equals(id)) {
      return GolemItems.masterworkHeart();
    }
    return GolemItems.resolve(Id.of(GolemItems.NAMESPACE, id));
  }

  private void listOnline(PlayerRef target, CommandSource sender) {
    GolemRoster roster = readyRoster(target, sender);
    if (roster != null) {
      report(roster, target.name(), sender);
    }
  }

  private void listOffline(KnownPlayer target, CommandSource sender) {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    if (server == null) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.ADAPT_SERVER_NOT_READY)));
      return;
    }
    report(server.peekData(target.uuid()).getGolemancy(), target.displayName(), sender);
  }

  private void report(GolemRoster roster, String name, CommandSource sender) {
    List<GolemRoster.GolemEntry> golems = RosterGui.entries(roster);
    if (golems.isEmpty()) {
      CommandTargetExecutor.send(sender, FConst.info(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_LIST_EMPTY, untrusted("player", name))));
      return;
    }
    String header = golems.size() == 1
        ? AdaptLanguage.text(CommandRuntimeMessages.GOLEM_LIST_HEADER_ONE, untrusted("player", name))
        : AdaptLanguage.text(CommandRuntimeMessages.GOLEM_LIST_HEADER, untrusted("player", name), trusted("count", golems.size()));
    Feedback.FeedbackBuilder report = Feedback.builder().message(FConst.infoText(header));
    for (GolemRoster.GolemEntry entry : golems) {
      report.message(FConst.infoText(describe(entry)));
    }
    CommandTargetExecutor.send(sender, report.build());
  }

  private void releaseTarget(GolemRegistry registry, String id, PlayerRef target, CommandSource sender) {
    GolemRoster roster = readyRoster(target, sender);
    if (roster == null) {
      return;
    }
    List<GolemRoster.GolemEntry> matches = roster.matching(id);
    if (matches.size() != 1) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(
          matches.isEmpty() ? CommandRuntimeMessages.GOLEM_NOT_FOUND : CommandRuntimeMessages.GOLEM_AMBIGUOUS,
          untrusted("player", target.name()),
          untrusted("id", id))));
      return;
    }
    UUID golem = matches.getFirst().id();
    String shortId = shortId(golem);
    String name = target.name();
    registry.releaseFor(target.uuid(), golem).thenAccept(release -> CommandTargetExecutor.send(sender, switch (release) {
      case RELEASED -> FConst.success(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RELEASED, trusted("id", shortId), untrusted("player", name)));
      case ACTIVE_UNLOADED -> FConst.error(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RELEASE_ACTIVE_UNLOADED, trusted("id", shortId),
          untrusted("player", name)));
      case REFUSED -> FConst.error(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_RELEASE_FAILED, trusted("id", shortId), untrusted("player", name)));
    }));
  }

  private void wakeTarget(GolemRegistry registry, PlayerRef target, CommandSource sender) {
    if (readyRoster(target, sender) == null) {
      return;
    }
    int loaded = registry.wake(target.uuid());
    CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(CommandRuntimeMessages.GOLEMS_WOKEN,
        untrusted("player", target.name()),
        trusted("count", loaded))));
  }

  private void giveTarget(String id, boolean everything, int amount, PlayerRef target, CommandSource sender) {
    List<ItemRef> stacks = everything ? everything() : stacks(id, amount);
    int dropped = hand(target, stacks);
    String name = target.name();
    CommandTargetExecutor.send(sender, FConst.success(everything
        ? AdaptLanguage.text(CommandRuntimeMessages.GOLEM_GIVEN_ALL, untrusted("player", name), trusted("count", stacks.size()))
        : AdaptLanguage.text(CommandRuntimeMessages.GOLEM_GIVEN, trusted("amount", amount), trusted("item", id), untrusted("player", name))));
    if (dropped > 0) {
      CommandTargetExecutor.send(sender, FConst.info(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_GIVE_DROPPED,
          untrusted("player", name), trusted("count", dropped))));
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
    String golem = AdaptLanguage.text(GolemancyMessages.ROSTER_ENTRY,
        trusted("body", AdaptLanguage.text(entry.body().displayName())),
        trusted("core", AdaptLanguage.text(entry.core().displayName())));
    return AdaptLanguage.text(CommandRuntimeMessages.GOLEM_LIST_ENTRY,
        trusted("id", shortId(entry.id())),
        trusted("golem", golem),
        trusted("status", AdaptLanguage.text(GolemGuiActions.statusName(entry.status()))),
        untrusted("location", RosterGui.lastSeen(entry)));
  }

  private static String shortId(UUID id) {
    return id.toString().substring(0, SHORT_ID_LENGTH);
  }

  private static GolemRegistry runningRegistry(CommandSource sender) {
    Optional<GolemRegistry> registry = GolemancyRuntime.registry();
    if (registry.isEmpty()) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.SKILL_DISABLED, untrusted("skill", CommandGolem.SKILL))).send(sender);
      return null;
    }
    return registry.get();
  }

  private static GolemRoster readyRoster(PlayerRef target, CommandSource sender) {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    AdaptPlayer adaptPlayer = server == null ? null : server.getOnlineAdaptPlayer(target.uuid());
    if (adaptPlayer != null && adaptPlayer.isRuntimeReady() && target.equals(adaptPlayer.getPlayer())) {
      return adaptPlayer.getData().getGolemancy();
    }
    CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.ADAPT_SERVER_NOT_READY)));
    return null;
  }
}
