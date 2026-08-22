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
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.MutationMessages;
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

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

@Director(name = "mutations", origin = DirectorOrigin.BOTH, description = "View and manage experimental Mutations", descriptionKey = "command.help.view_and_manage_experimental_mutations")
public class CommandMutation {
  @Director(name = "menu", origin = DirectorOrigin.PLAYER, description = "Open the experimental Mutation menu", descriptionKey = "command.help.open_the_experimental_mutation_menu")
  public void menu() {
    if (!hasPlayerPermission()) {
      return;
    }
    MutationGui.open(BukkitDirectorContext.player());
  }

  @Director(name = "view", description = "View Mutation state for yourself or another player", descriptionKey = "command.help.view_mutation_state_for_yourself_or_another_player")
  public void view(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    Player target = resolveTarget(player, sender);
    if (target == null || !canInspect(target, sender)) {
      return;
    }

    runTarget(target, () -> sendSnapshot(sender, target));
  }

  @Director(name = "cooperative", origin = DirectorOrigin.PLAYER, description = "Toggle cooperative Mutation participation", descriptionKey = "command.help.toggle_cooperative_mutation_participation")
  public void cooperative(
      @Param(description = "on, off, or toggle", defaultValue = "toggle", descriptionKey = "command.help.on_off_or_toggle")
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
        send(sender, C.RED + AdaptLanguage.text(MutationMessages.TOGGLE_USAGE));
        return;
      }
      manager.setCooperativeOptIn(player, requested);
      boolean featureEnabled = manager.getConfig().isEnabled();
      send(sender, (requested ? C.GREEN : C.YELLOW) + AdaptLanguage.text(
          requested
              ? (featureEnabled ? MutationMessages.COOPERATIVE_ENABLED : MutationMessages.COOPERATIVE_ENABLED_OFF)
              : (featureEnabled ? MutationMessages.COOPERATIVE_DISABLED : MutationMessages.COOPERATIVE_DISABLED_OFF)
      ));
    });
  }

  @Director(name = "equip", description = "Equip a Mutation in a player slot", descriptionKey = "command.help.equip_a_mutation_in_a_player_slot")
  public void equip(
      @Param(description = "Mutation ID", descriptionKey = "command.help.mutation_id")
      String mutation,
      @Param(description = "slot 1 or 2", descriptionKey = "command.help.slot_1_or_2")
      int slot,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
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
        send(sender, C.GRAY + AdaptLanguage.text(MutationMessages.CHOICE_SAVED_OFF));
      }
    });
  }

  @Director(name = "clear", description = "Clear a player's Mutation slot", descriptionKey = "command.help.clear_a_player_s_mutation_slot")
  public void clear(
      @Param(description = "slot 1 or 2", descriptionKey = "command.help.slot_1_or_2")
      int slot,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
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

  @Director(name = "discover", description = "Discover or undiscover a Mutation for a player", descriptionKey = "command.help.discover_or_undiscover_a_mutation_for_a_player")
  public void discover(
      @Param(description = "Mutation ID", descriptionKey = "command.help.mutation_id")
      String mutation,
      @Param(description = "true to discover, false to undiscover", defaultValue = "true", descriptionKey = "command.help.true_to_discover_false_to_undiscover")
      boolean discovered,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
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
      send(sender, C.GREEN + AdaptLanguage.text(
          discovered ? MutationMessages.DISCOVERED : MutationMessages.UNDISCOVERED,
          trusted("mutation", type.displayName()),
          untrusted("player", target.getName())
      ));
    });
  }

  @Director(name = "cooldown", description = "Clear both Mutation switching cooldowns", descriptionKey = "command.help.clear_both_mutation_switching_cooldowns")
  public void cooldown(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
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
        send(sender, C.GREEN + AdaptLanguage.text(
            MutationMessages.COOLDOWNS_CLEARED,
            untrusted("player", target.getName())
        ));
      }
    });
  }

  @Director(name = "refresh", description = "Refresh a player's Mutation slots and requirements", descriptionKey = "command.help.refresh_a_player_s_mutation_slots_and_requirements")
  public void refresh(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
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
        send(sender, C.GREEN + AdaptLanguage.text(
            MutationMessages.STATE_REFRESHED,
            untrusted("player", target.getName())
        ));
      }
    });
  }

  @Director(name = "slot-override", description = "Force a Mutation slot unlocked, locked, or back to its normal level requirement", descriptionKey = "command.help.force_a_mutation_slot_unlocked_locked_or_back_to_its_normal_level_requirement")
  public void slotOverride(
      @Param(description = "slot 1 or 2", descriptionKey = "command.help.slot_1_or_2")
      int slot,
      @Param(description = "on, off, or clear", descriptionKey = "command.help.on_off_or_clear")
      String enabled,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!requireAdmin(sender)) {
      return;
    }
    if (slot != 1 && slot != 2) {
      send(sender, C.RED + AdaptLanguage.text(MutationMessages.SLOT_RANGE));
      return;
    }
    OverrideValue overrideValue = parseOverride(enabled);
    if (overrideValue == OverrideValue.INVALID) {
      send(sender, C.RED + AdaptLanguage.text(MutationMessages.OVERRIDE_USAGE));
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
      send(sender, C.GREEN + AdaptLanguage.text(
          override == null
              ? MutationMessages.SLOT_NORMAL
              : (override ? MutationMessages.SLOT_FORCED_UNLOCKED : MutationMessages.SLOT_FORCED_LOCKED),
          trusted("slot", slot),
          untrusted("player", target.getName())
      ));
    });
  }

  @Director(name = "reset", description = "Clear only a player's Mutation data", descriptionKey = "command.help.clear_only_a_player_s_mutation_data")
  public void reset(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
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
      send(sender, C.GREEN + AdaptLanguage.text(
          MutationMessages.DATA_CLEARED,
          untrusted("player", target.getName())
      ));
    });
  }

  @Director(name = "perfect-test", description = "Force Perfect Adaptation on, off, or back to its normal level requirement", descriptionKey = "command.help.force_perfect_adaptation_on_off_or_back_to_its_normal_level_requirement")
  public void perfectTest(
      @Param(description = "on, off, or clear", defaultValue = "clear", descriptionKey = "command.help.on_off_or_clear")
      String enabled,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
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
      send(sender, C.RED + AdaptLanguage.text(MutationMessages.OVERRIDE_USAGE));
      return;
    }
    Boolean override = toBooleanOverride(overrideValue);
    runTarget(target, () -> {
      MutationManager manager = manager(sender);
      if (manager != null) {
        manager.setPerfectOverride(target, override);
        manager.reconcile(target);
        send(sender, C.GREEN + AdaptLanguage.text(
            override == null
                ? MutationMessages.PERFECT_NORMAL
                : (override ? MutationMessages.PERFECT_FORCED_ON : MutationMessages.PERFECT_FORCED_OFF),
            untrusted("player", target.getName())
        ));
      }
    });
  }

  @Director(name = "reload", description = "Reload Mutation configuration", descriptionKey = "command.help.reload_mutation_configuration")
  public void reload() {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!requireAdmin(sender)) {
      return;
    }
    MutationSVC service = MutationSVC.get();
    if (service == null) {
      send(sender, C.RED + AdaptLanguage.text(MutationMessages.MUTATIONS_UNAVAILABLE));
      return;
    }
    boolean reloaded = service.reload();
    if (reloaded) {
      HotloadSVC.reconcileOnlineMutations(service.getManager());
    }
    if (!reloaded) {
      send(sender, C.RED + AdaptLanguage.text(MutationMessages.RELOAD_FAILED));
      return;
    }
    if (service.getManager().getConfig().isEnabled()) {
      send(sender, C.GREEN + AdaptLanguage.text(MutationMessages.RELOAD_ENABLED));
    } else {
      send(sender, C.YELLOW + AdaptLanguage.text(MutationMessages.RELOAD_DISABLED));
    }
  }

  private boolean hasPlayerPermission() {
    if (BukkitDirectorContext.hasPermission("adapt.mutations")) {
      return true;
    }
    BukkitDirectorContext.sender().sendMessage(C.RED + AdaptLanguage.text(
        CommandRuntimeMessages.MISSING_PERMISSION,
        trusted("permission", "adapt.mutations")
    ));
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
    send(sender, C.RED + AdaptLanguage.text(
        CommandRuntimeMessages.MISSING_PERMISSION,
        trusted("permission", "adapt.mutations.admin")
    ));
    return false;
  }

  private Player resolveTarget(Player player, CommandSender sender) {
    if (player != null) {
      return player;
    }
    if (sender instanceof Player senderPlayer) {
      return senderPlayer;
    }
    send(sender, C.RED + AdaptLanguage.text(CommandRuntimeMessages.PLAYER_REQUIRED_FROM_CONSOLE_SHORT));
    return null;
  }

  private MutationManager manager(CommandSender sender) {
    MutationSVC service = MutationSVC.get();
    if (service == null || service.getManager() == null) {
      send(sender, C.RED + AdaptLanguage.text(MutationMessages.MUTATIONS_UNAVAILABLE));
      return null;
    }
    return service.getManager();
  }

  private AdaptPlayer adaptPlayer(Player player, CommandSender sender) {
    if (player == null || Adapt.instance == null || Adapt.instance.getAdaptServer() == null) {
      send(sender, C.RED + AdaptLanguage.text(MutationMessages.PLAYER_DATA_UNAVAILABLE));
      return null;
    }
    AdaptPlayer adaptPlayer = Adapt.instance.getAdaptServer().getPlayer(player);
    if (adaptPlayer == null) {
      send(sender, C.RED + AdaptLanguage.text(MutationMessages.PLAYER_DATA_UNAVAILABLE));
    }
    return adaptPlayer;
  }

  private MutationType resolveMutation(MutationManager manager, String mutation, CommandSender sender) {
    MutationType type = manager.find(mutation);
    if (type == null) {
      send(sender, C.RED + AdaptLanguage.text(
          MutationMessages.UNKNOWN_MUTATION,
          untrusted("mutation", mutation)
      ));
    }
    return type;
  }

  private void sendSnapshot(CommandSender sender, Player target) {
    MutationManager manager = manager(sender);
    if (manager == null) {
      return;
    }

    MutationSnapshot snapshot = manager.reconcile(target);
    send(sender, C.DARK_PURPLE + AdaptLanguage.text(
        MutationMessages.SNAPSHOT_TITLE,
        untrusted("player", target.getName())
    ));
    send(sender, (manager.getConfig().isEnabled() ? C.GREEN : C.YELLOW) + AdaptLanguage.text(
        manager.getConfig().isEnabled() ? MutationMessages.FEATURE_ENABLED : MutationMessages.FEATURE_DISABLED
    ));
    send(sender, formatSlot(manager, snapshot, 1, snapshot.slotOneId(), snapshot.slotOneUnlocked()));
    send(sender, formatSlot(manager, snapshot, 2, snapshot.slotTwoId(), snapshot.slotTwoUnlocked()));
    send(sender, (snapshot.perfect() ? C.GOLD : C.DARK_GRAY) + AdaptLanguage.text(
        snapshot.perfect() ? MutationMessages.PERFECT_ACTIVE : MutationMessages.PERFECT_INACTIVE
    ));
    send(sender, (snapshot.cooperativeOptIn() ? C.GREEN : C.DARK_GRAY) + AdaptLanguage.text(
        snapshot.cooperativeOptIn()
            ? MutationMessages.COOPERATIVE_STATUS_ENABLED
            : MutationMessages.COOPERATIVE_STATUS_DISABLED
    ));
    AdaptPlayer adaptPlayer = adaptPlayer(target, sender);
    if (adaptPlayer != null) {
      PlayerMutationData data = adaptPlayer.getData().getMutationData();
      send(sender, C.GRAY + AdaptLanguage.text(
          MutationMessages.STORED_RESOURCES,
          trusted("deepCharge", Form.f(data.getDeepbloodIchor(), 1)),
          trusted("rootCharge", Form.f(data.getLivingLatticeRootCharge(), 1)),
          trusted("steps", data.getFormulaSigils().size())
      ));
      send(sender, C.GRAY + AdaptLanguage.text(
          MutationMessages.LINKED_GEAR,
          trusted("temperbound", yesNo(!data.getTemperboundBondId().isBlank())),
          trusted("masterwork", yesNo(!data.getMasterworkItemId().isBlank())),
          trusted("deepblood", yesNo(!data.getDeepbloodToolId().isBlank())),
          trusted("trophy", yesNo(!data.getTrophyImprint().isBlank()))
      ));
    }
  }

  private String yesNo(boolean value) {
    return AdaptLanguage.text(value ? MutationMessages.YES : MutationMessages.NO);
  }

  private String formatState(MutationState state) {
    return switch (state) {
      case AVAILABLE -> AdaptLanguage.text(MutationMessages.STATE_READY);
      case EXPRESSED -> AdaptLanguage.text(MutationMessages.STATE_ACTIVE);
      case DORMANT -> AdaptLanguage.text(MutationMessages.STATE_INACTIVE);
      case LOCKED -> AdaptLanguage.text(MutationMessages.STATE_LOCKED);
      case DISABLED -> AdaptLanguage.text(MutationMessages.STATE_OFF);
      case RESTRICTED -> AdaptLanguage.text(MutationMessages.STATE_UNAVAILABLE);
      case CONFLICT -> AdaptLanguage.text(MutationMessages.STATE_BLOCKED);
    };
  }

  private String formatSlot(MutationManager manager, MutationSnapshot snapshot, int slot, String mutationId, boolean unlocked) {
    if (!unlocked) {
      return C.DARK_GRAY + AdaptLanguage.text(MutationMessages.SLOT_LOCKED, trusted("slot", slot));
    }
    MutationType type = manager.find(mutationId);
    if (type == null) {
      if (mutationId == null || mutationId.isBlank()) {
        return C.GRAY + AdaptLanguage.text(MutationMessages.SLOT_EMPTY, trusted("slot", slot));
      }
      return C.GRAY + AdaptLanguage.text(
          MutationMessages.SLOT_UNAVAILABLE,
          trusted("slot", slot),
          untrusted("mutation", mutationId)
      );
    }
    String reason = snapshot.reason(type);
    if (reason == null || reason.isBlank()) {
      return C.GRAY + AdaptLanguage.text(
          MutationMessages.SLOT_VALUE,
          trusted("slot", slot),
          trusted("mutation", type.displayName()),
          trusted("state", formatState(snapshot.state(type)))
      );
    }
    return C.GRAY + AdaptLanguage.text(
        MutationMessages.SLOT_VALUE_REASON,
        trusted("slot", slot),
        trusted("mutation", type.displayName()),
        trusted("state", formatState(snapshot.state(type))),
        trusted("reason", reason)
    );
  }

  private void sendSelectionResult(CommandSender sender, MutationSelectionResult result) {
    String message = result.message() == null || result.message().isBlank()
        ? AdaptLanguage.text(result.success() ? MutationMessages.RESULT_UPDATED : MutationMessages.RESULT_NOT_CHANGED)
        : result.message();
    if (!result.success() && result.cooldownRemainingMillis() > 0L) {
      message = AdaptLanguage.text(
          MutationMessages.REMAINING_COOLDOWN,
          trusted("message", message),
          trusted("duration", Form.duration(result.cooldownRemainingMillis(), 1))
      );
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
