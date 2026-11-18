package art.arcane.adapt.command;

import art.arcane.adapt.api.world.AdaptDebugMode;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.director.specialhandlers.GolemItemIdHandler;
import art.arcane.adapt.util.director.specialhandlers.NullableOfflinePlayerHandler;
import art.arcane.adapt.util.director.specialhandlers.NullablePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

import java.util.Optional;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

@Director(name = "golem", origin = DirectorOrigin.BOTH, description = "Golem roster and admin tools", descriptionKey = "command.help.golem_roster_and_admin_tools")
public class CommandGolem {
  public static final String ADMIN_PERMISSION = "adapt.golemancy.admin";
  public static final String SKILL = "golemancy";
  private static final int MIN_GIVE_AMOUNT = 1;
  private static final int MAX_GIVE_AMOUNT = 64;

  @Director(origin = DirectorOrigin.PLAYER, description = "Open your golem roster", descriptionKey = "command.help.open_your_golem_roster")
  public void roster() {
    golems().roster(CommandContext.source(), CommandContext.player());
  }

  @Director(description = "List a player's golems", descriptionKey = "command.help.list_a_player_s_golems")
  public void list(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullableOfflinePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      KnownPlayer player
  ) {
    CommandSource sender = CommandContext.source();
    if (!admin(sender)) {
      return;
    }
    KnownPlayer target = player;
    if (target == null) {
      PlayerRef self = senderTarget(sender);
      if (self == null) {
        return;
      }
      target = KnownPlayer.of(self);
    }
    golems().list(sender, target);
  }

  @Director(description = "Release one of a player's golems", descriptionKey = "command.help.release_one_of_a_player_s_golems")
  public void release(
      @Param(description = "Golem id, or enough of its first characters to match one golem", descriptionKey = "command.help.golem_id_or_enough_of_its_first_characters_to_match_one_golem")
      String id,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    PlayerRef target = onlineAdminTarget(player, sender);
    if (target != null) {
      golems().release(sender, id, target);
    }
  }

  @Director(description = "Wake a player's loaded golems", descriptionKey = "command.help.wake_a_player_s_loaded_golems")
  public void wake(
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    PlayerRef target = onlineAdminTarget(player, sender);
    if (target != null) {
      golems().wake(sender, target);
    }
  }

  @Director(description = "Give Golemancy items for testing in debug mode. Dormant Golems and Shattered Cores are owner-bound and excluded.", descriptionKey = "command.help.give_golemancy_items_for_testing_in_debug_mode_dormant_golems_and_shattered_cores_are_owner_bound_and_excluded")
  public void give(
      @Param(description = "Item id such as crook, core/guardian, node/deposit, rune/might/2 or masterwork_heart, or all for one of each", customHandler = GolemItemIdHandler.class, descriptionKey = "command.help.item_id_such_as_crook_core_guardian_node_deposit_rune_might_2_or_masterwork_heart_or_all_for_one_of_each")
      String item,
      @Param(description = "Amount from 1 to 64, defaults to 1", defaultValue = "1", descriptionKey = "command.help.amount_from_1_to_64_defaults_to_1")
      int amount,
      @Param(description = "Target player, defaults to you", defaultValue = "---", customHandler = NullablePlayerHandler.class, descriptionKey = "command.help.target_player_defaults_to_you")
      PlayerRef player
  ) {
    CommandSource sender = CommandContext.source();
    if (!admin(sender) || !debugModeAllowed(sender)) {
      return;
    }
    String id = GolemGiveCatalog.normalize(item);
    boolean everything = GolemGiveCatalog.ALL.equals(id);
    if (!everything && !GolemGiveCatalog.isItem(id)) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_GIVE_UNKNOWN_ITEM, untrusted("item", item.trim()))).send(sender);
      return;
    }
    PlayerRef target = player != null ? player : senderTarget(sender);
    if (target == null) {
      return;
    }
    int count = Math.max(MIN_GIVE_AMOUNT, Math.min(MAX_GIVE_AMOUNT, amount));
    golems().give(sender, id, everything, count, target);
  }

  @Director(description = "Inspect unresolved node returns or confirm their delivery status")
  public void returns(
      @Param(description = "Return identifier from the pending list", defaultValue = "---") String id,
      @Param(description = "list, delivered, or missing; missing confirms no item was delivered", defaultValue = "list") String outcome
  ) {
    CommandSource sender = CommandContext.source();
    if (!sender.isOp()) {
      FConst.error(AdaptLanguage.text(CommandRuntimeMessages.OPERATOR_ONLY)).send(sender);
      return;
    }
    if (!admin(sender)) {
      return;
    }
    golems().returns(sender, id, outcome);
  }

  private static GolemCommands golems() {
    return GolemCommands.INSTANCE;
  }

  private static PlayerRef onlineAdminTarget(PlayerRef player, CommandSource sender) {
    if (!admin(sender)) {
      return null;
    }
    return player != null ? player : senderTarget(sender);
  }

  private static boolean admin(CommandSource sender) {
    if (sender.hasPermission(ADMIN_PERMISSION)) {
      return true;
    }
    FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", ADMIN_PERMISSION))).send(sender);
    return false;
  }

  private static boolean debugModeAllowed(CommandSource sender) {
    Optional<PlayerRef> player = sender.player();
    if (player.isEmpty() || AdaptDebugMode.isActive(player.get().uuid())) {
      return true;
    }
    FConst.error(AdaptLanguage.text(CommandRuntimeMessages.GOLEM_GIVE_DEBUG_REQUIRED)).send(sender);
    return false;
  }

  private static PlayerRef senderTarget(CommandSource sender) {
    Optional<PlayerRef> player = sender.player();
    if (player.isPresent()) {
      return player.get();
    }
    FConst.error(AdaptLanguage.text(CommandRuntimeMessages.PLAYER_REQUIRED_FROM_CONSOLE)).send(sender);
    return null;
  }
}
