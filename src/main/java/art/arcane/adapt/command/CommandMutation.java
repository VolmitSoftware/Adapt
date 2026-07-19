package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.mutation.MutationManager;
import art.arcane.adapt.api.mutation.MutationSelectionResult;
import art.arcane.adapt.api.mutation.MutationSnapshot;
import art.arcane.adapt.api.mutation.MutationState;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.api.mutation.PlayerMutationData;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.gui.MutationGui;
import art.arcane.adapt.service.HotloadSVC;
import art.arcane.adapt.service.MutationSVC;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

@Director(name = "mutations", origin = DirectorOrigin.BOTH, description = "View and manage experimental Mutations")
public class CommandMutation {
  @Director(name = "menu", origin = DirectorOrigin.PLAYER, description = "Open the experimental Mutation menu")
  public void menu() {
    if (!hasPlayerPermission()) {
      return;
    }
    MutationGui.open(BukkitDirectorContext.player());
  }

  @Director(name = "view", description = "View Mutation state for yourself or another player")
  public void view(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    Player target = resolveTarget(player, sender);
    if (target == null || !canInspect(target, sender)) {
      return;
    }

    runTarget(target, () -> sendSnapshot(sender, target));
  }

  @Director(name = "cooperative", origin = DirectorOrigin.PLAYER, description = "Toggle cooperative Mutation participation")
  public void cooperative(
      @Param(description = "on, off, or toggle", defaultValue = "toggle")
      String enabled
  ) {
    if (!hasPlayerPermission()) {
      return;
    }

    Player player = BukkitDirectorContext.player();
    CommandSender sender = BukkitDirectorContext.sender();
    runTarget(player, () -> {
      MutationManager manager = manager(sender);
      if (manager == null) {
        return;
      }
      MutationSnapshot snapshot = manager.snapshot(player);
      Boolean requested = parseToggle(enabled, snapshot.cooperativeOptIn());
      if (requested == null) {
        send(sender, C.RED + "Use enabled=on, enabled=off, or omit it to toggle.");
        return;
      }
      manager.setCooperativeOptIn(player, requested);
      String status = requested
          ? C.GREEN + "Cooperative Mutation effects are enabled."
          : C.YELLOW + "Cooperative Mutation effects are disabled.";
      if (!manager.getConfig().isEnabled()) {
        status += C.GRAY + " Preference saved; experimental Mutations are currently off.";
      }
      send(sender, status);
    });
  }

  @Director(name = "equip", description = "Equip a Mutation in a player slot")
  public void equip(
      @Param(description = "Mutation ID")
      String mutation,
      @Param(description = "slot 1 or 2")
      int slot,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!requireAdmin(sender)) {
      return;
    }
    Player target = resolveTarget(player, sender);
    if (target == null) {
      return;
    }

    runTarget(target, () -> {
      MutationManager manager = manager(sender);
      if (manager == null) {
        return;
      }
      MutationType type = resolveMutation(manager, mutation, sender);
      if (type == null) {
        return;
      }
      MutationSelectionResult result = manager.select(target, slot, type, true);
      sendSelectionResult(sender, result);
      if (result.success() && !manager.getConfig().isEnabled()) {
        send(sender, C.GRAY + "The choice was saved but stays inactive while experimental Mutations are off.");
      }
    });
  }

  @Director(name = "clear", description = "Clear a player's Mutation slot")
  public void clear(
      @Param(description = "slot 1 or 2")
      int slot,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!requireAdmin(sender)) {
      return;
    }
    Player target = resolveTarget(player, sender);
    if (target == null) {
      return;
    }

    runTarget(target, () -> {
      MutationManager manager = manager(sender);
      if (manager != null) {
        sendSelectionResult(sender, manager.clear(target, slot, true));
      }
    });
  }

  @Director(name = "discover", description = "Discover or undiscover a Mutation for a player")
  public void discover(
      @Param(description = "Mutation ID")
      String mutation,
      @Param(description = "true to discover, false to undiscover", defaultValue = "true")
      boolean discovered,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!requireAdmin(sender)) {
      return;
    }
    Player target = resolveTarget(player, sender);
    if (target == null) {
      return;
    }

    runTarget(target, () -> {
      MutationManager manager = manager(sender);
      if (manager == null) {
        return;
      }
      MutationType type = resolveMutation(manager, mutation, sender);
      if (type == null) {
        return;
      }
      manager.setDiscovered(target, type, discovered);
      send(sender, C.GREEN + (discovered ? "Discovered " : "Undiscovered ") + type.displayName()
          + " for " + target.getName() + ".");
    });
  }

  @Director(name = "cooldown", description = "Clear both Mutation switching cooldowns")
  public void cooldown(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!requireAdmin(sender)) {
      return;
    }
    Player target = resolveTarget(player, sender);
    if (target == null) {
      return;
    }
    runTarget(target, () -> {
      MutationManager manager = manager(sender);
      if (manager != null) {
        manager.clearCooldowns(target);
        send(sender, C.GREEN + "Cleared Mutation switching cooldowns for " + target.getName() + ".");
      }
    });
  }

  @Director(name = "refresh", description = "Refresh a player's Mutation slots and requirements")
  public void refresh(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!requireAdmin(sender)) {
      return;
    }
    Player target = resolveTarget(player, sender);
    if (target == null) {
      return;
    }
    runTarget(target, () -> {
      MutationManager manager = manager(sender);
      if (manager != null) {
        manager.reconcile(target);
        send(sender, C.GREEN + "Refreshed Mutation state for " + target.getName() + ".");
      }
    });
  }

  @Director(name = "slot-override", description = "Force a Mutation slot unlocked, locked, or back to its normal level requirement")
  public void slotOverride(
      @Param(description = "slot 1 or 2")
      int slot,
      @Param(description = "on, off, or clear")
      String enabled,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!requireAdmin(sender)) {
      return;
    }
    if (slot != 1 && slot != 2) {
      send(sender, C.RED + "Mutation slot must be 1 or 2.");
      return;
    }
    OverrideValue overrideValue = parseOverride(enabled);
    if (overrideValue == OverrideValue.INVALID) {
      send(sender, C.RED + "Use enabled=on, enabled=off, or enabled=clear.");
      return;
    }
    Player target = resolveTarget(player, sender);
    if (target == null) {
      return;
    }
    Boolean override = toBooleanOverride(overrideValue);
    runTarget(target, () -> {
      MutationManager manager = manager(sender);
      if (manager == null) {
        return;
      }
      manager.setSlotOverride(target, slot, override);
      String state = override == null ? "using its normal level requirement" : (override ? "forced unlocked" : "forced locked");
      send(sender, C.GREEN + "Mutation slot " + slot + " is now " + state + " for " + target.getName() + ".");
    });
  }

  @Director(name = "reset", description = "Clear only a player's Mutation data")
  public void reset(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!requireAdmin(sender)) {
      return;
    }
    Player target = resolveTarget(player, sender);
    if (target == null) {
      return;
    }
    runTarget(target, () -> {
      MutationManager manager = manager(sender);
      AdaptPlayer adaptPlayer = adaptPlayer(target, sender);
      if (manager == null || adaptPlayer == null) {
        return;
      }
      adaptPlayer.getData().clearMutations();
      manager.setPerfectOverride(target, null);
      adaptPlayer.saveNow();
      manager.reconcile(adaptPlayer);
      send(sender, C.GREEN + "Cleared Mutation data for " + target.getName()
          + " without changing XP, skills, Knowledge, Adaptations, or advancements.");
    });
  }

  @Director(name = "perfect-test", description = "Force Perfect Adaptation on, off, or back to its normal level requirement")
  public void perfectTest(
      @Param(description = "on, off, or clear", defaultValue = "clear")
      String enabled,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class)
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!requireAdmin(sender)) {
      return;
    }
    Player target = resolveTarget(player, sender);
    if (target == null) {
      return;
    }

    OverrideValue overrideValue = parseOverride(enabled);
    if (overrideValue == OverrideValue.INVALID) {
      send(sender, C.RED + "Use enabled=on, enabled=off, or enabled=clear.");
      return;
    }
    Boolean override = toBooleanOverride(overrideValue);
    runTarget(target, () -> {
      MutationManager manager = manager(sender);
      if (manager != null) {
        manager.setPerfectOverride(target, override);
        manager.reconcile(target);
        String state = override == null ? "using its normal level requirement" : (override ? "forced on" : "forced off");
        send(sender, C.GREEN + "Perfect Adaptation is now " + state + " for " + target.getName() + ".");
      }
    });
  }

  @Director(name = "reload", description = "Reload Mutation configuration")
  public void reload() {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!requireAdmin(sender)) {
      return;
    }
    MutationSVC service = MutationSVC.get();
    if (service == null) {
      send(sender, C.RED + "Mutations are not available right now.");
      return;
    }
    boolean reloaded = service.reload();
    if (reloaded) {
      HotloadSVC.reconcileOnlineMutations(service.getManager());
    }
    if (!reloaded) {
      send(sender, C.RED + "Mutation configuration reload failed; previous settings remain active.");
      return;
    }
    if (service.getManager().getConfig().isEnabled()) {
      send(sender, C.GREEN + "Mutation configuration reloaded. Experimental Mutations are enabled; online slots are being refreshed.");
    } else {
      send(sender, C.YELLOW + "Mutation configuration reloaded. Experimental Mutations are disabled; saved choices are retained.");
    }
  }

  private boolean hasPlayerPermission() {
    if (BukkitDirectorContext.hasPermission("adapt.mutations")) {
      return true;
    }
    BukkitDirectorContext.sender().sendMessage(C.RED + "You lack the permission 'adapt.mutations'.");
    return false;
  }

  private boolean canInspect(Player target, CommandSender sender) {
    if (sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId())) {
      return hasPlayerPermission();
    }
    return requireAdmin(sender);
  }

  private boolean requireAdmin(CommandSender sender) {
    if (sender.hasPermission("adapt.mutations.admin")) {
      return true;
    }
    send(sender, C.RED + "You lack the permission 'adapt.mutations.admin'.");
    return false;
  }

  private Player resolveTarget(Player player, CommandSender sender) {
    if (player != null) {
      return player;
    }
    if (sender instanceof Player senderPlayer) {
      return senderPlayer;
    }
    send(sender, C.RED + "You must specify a player from console.");
    return null;
  }

  private MutationManager manager(CommandSender sender) {
    MutationSVC service = MutationSVC.get();
    if (service == null || service.getManager() == null) {
      send(sender, C.RED + "Mutations are not available right now.");
      return null;
    }
    return service.getManager();
  }

  private AdaptPlayer adaptPlayer(Player player, CommandSender sender) {
    if (player == null || Adapt.instance == null || Adapt.instance.getAdaptServer() == null) {
      send(sender, C.RED + "Player Mutation data is unavailable.");
      return null;
    }
    AdaptPlayer adaptPlayer = Adapt.instance.getAdaptServer().getPlayer(player);
    if (adaptPlayer == null) {
      send(sender, C.RED + "Player Mutation data is unavailable.");
    }
    return adaptPlayer;
  }

  private MutationType resolveMutation(MutationManager manager, String mutation, CommandSender sender) {
    MutationType type = manager.find(mutation);
    if (type == null) {
      send(sender, C.RED + "Unknown Mutation: " + mutation);
    }
    return type;
  }

  private void sendSnapshot(CommandSender sender, Player target) {
    MutationManager manager = manager(sender);
    if (manager == null) {
      return;
    }

    MutationSnapshot snapshot = manager.reconcile(target);
    send(sender, C.DARK_PURPLE + "Experimental Mutations for " + C.WHITE + target.getName());
    send(sender, C.GRAY + "Feature: " + (manager.getConfig().isEnabled() ? C.GREEN + "enabled" : C.YELLOW + "disabled"));
    send(sender, formatSlot(manager, snapshot, 1, snapshot.slotOneId(), snapshot.slotOneUnlocked()));
    send(sender, formatSlot(manager, snapshot, 2, snapshot.slotTwoId(), snapshot.slotTwoUnlocked()));
    send(sender, C.GRAY + "Perfect Adaptation: " + (snapshot.perfect() ? C.GOLD + "active" : C.DARK_GRAY + "inactive"));
    send(sender, C.GRAY + "Cooperative effects: " + (snapshot.cooperativeOptIn() ? C.GREEN + "enabled" : C.DARK_GRAY + "disabled"));
    AdaptPlayer adaptPlayer = adaptPlayer(target, sender);
    if (adaptPlayer != null) {
      PlayerMutationData data = adaptPlayer.getData().getMutationData();
      send(sender, C.GRAY + "Stored resources: " + C.WHITE
          + "Deep Charge " + Form.f(data.getDeepbloodIchor(), 1)
          + C.DARK_GRAY + " • " + C.WHITE + "Root Charge " + Form.f(data.getLivingLatticeRootCharge(), 1)
          + C.DARK_GRAY + " • " + C.WHITE + "Craft/Brew/Enchant steps " + data.getFormulaSigils().size());
      send(sender, C.GRAY + "Linked gear: " + C.WHITE
          + "Temperbound " + yesNo(!data.getTemperboundBondId().isBlank())
          + C.DARK_GRAY + " • " + C.WHITE + "Masterwork " + yesNo(!data.getMasterworkItemId().isBlank())
          + C.DARK_GRAY + " • " + C.WHITE + "Deepblood Tool " + yesNo(!data.getDeepbloodToolId().isBlank())
          + C.DARK_GRAY + " • " + C.WHITE + "Trophy " + yesNo(!data.getTrophyImprint().isBlank()));
    }
  }

  private String yesNo(boolean value) {
    return value ? "yes" : "no";
  }

  private String formatState(MutationState state) {
    return switch (state) {
      case AVAILABLE -> "ready";
      case EXPRESSED -> "active";
      case DORMANT -> "inactive";
      case LOCKED -> "locked";
      case DISABLED -> "off";
      case RESTRICTED -> "unavailable";
      case CONFLICT -> "blocked";
    };
  }

  private String formatSlot(MutationManager manager, MutationSnapshot snapshot, int slot, String mutationId, boolean unlocked) {
    if (!unlocked) {
      return C.GRAY + "Slot " + slot + ": " + C.DARK_GRAY + "locked";
    }
    MutationType type = manager.find(mutationId);
    if (type == null) {
      String value = mutationId == null || mutationId.isBlank() ? "empty" : mutationId + " (unavailable)";
      return C.GRAY + "Slot " + slot + ": " + C.WHITE + value;
    }
    String value = C.GRAY + "Slot " + slot + ": " + C.WHITE + type.displayName()
        + C.DARK_GRAY + " [" + formatState(snapshot.state(type)) + "]";
    String reason = snapshot.reason(type);
    return reason == null || reason.isBlank() ? value : value + C.GRAY + " - " + reason;
  }

  private void sendSelectionResult(CommandSender sender, MutationSelectionResult result) {
    String message = result.message() == null || result.message().isBlank()
        ? (result.success() ? "Mutation state updated." : "Mutation state was not changed.")
        : result.message();
    if (!result.success() && result.cooldownRemainingMillis() > 0L) {
      message += " Remaining cooldown: " + Form.duration(result.cooldownRemainingMillis(), 1);
    }
    send(sender, (result.success() ? C.GREEN : C.RED) + message);
  }

  private Boolean parseToggle(String raw, boolean current) {
    if (raw == null || raw.isBlank() || "toggle".equalsIgnoreCase(raw)) {
      return !current;
    }
    return switch (raw.toLowerCase(Locale.ROOT)) {
      case "true", "on", "yes", "enabled" -> Boolean.TRUE;
      case "false", "off", "no", "disabled" -> Boolean.FALSE;
      default -> null;
    };
  }

  private OverrideValue parseOverride(String raw) {
    if (raw == null || raw.isBlank() || "clear".equalsIgnoreCase(raw) || "level".equalsIgnoreCase(raw)) {
      return OverrideValue.CLEAR;
    }
    return switch (raw.toLowerCase(Locale.ROOT)) {
      case "true", "on", "yes", "enabled" -> OverrideValue.ENABLED;
      case "false", "off", "no", "disabled" -> OverrideValue.DISABLED;
      default -> OverrideValue.INVALID;
    };
  }

  private Boolean toBooleanOverride(OverrideValue overrideValue) {
    return switch (overrideValue) {
      case ENABLED -> Boolean.TRUE;
      case DISABLED -> Boolean.FALSE;
      case CLEAR -> null;
      case INVALID -> throw new IllegalArgumentException("Invalid override state");
    };
  }

  private void runTarget(Player target, Runnable task) {
    if (!J.isFoliaThreading() && J.isPrimaryThread()) {
      task.run();
      return;
    }
    J.runEntity(target, task);
  }

  private void send(CommandSender sender, String message) {
    if (sender instanceof Player player) {
      if (!J.isFoliaThreading() && J.isPrimaryThread()) {
        player.sendMessage(message);
        return;
      }
      J.runEntity(player, () -> player.sendMessage(message));
      return;
    }
    sender.sendMessage(message);
  }

  private enum OverrideValue {
    ENABLED,
    DISABLED,
    CLEAR,
    INVALID
  }
}
