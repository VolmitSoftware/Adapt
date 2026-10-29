package art.arcane.adapt.command;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.claims.ClaimsService;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ClaimsMessages;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.director.specialhandlers.NullableOfflinePlayerHandler;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.BukkitDirectorContext;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.BiConsumer;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

@Director(name = "claims", origin = DirectorOrigin.BOTH, description = "Claims management", descriptionKey = "claims.help")
public final class CommandClaims {
  static final String INSPECT_PERMISSION = "adapt.claims.admin.inspect";
  static final String REMOVE_PERMISSION = "adapt.claims.admin.remove";
  static final String XP_PERMISSION = "adapt.claims.admin.xp";

  @Director(origin = DirectorOrigin.PLAYER, description = "Inspect your city", descriptionKey = "claims.help-info")
  public void info() {
    playerAction((service, player) -> service.interactions().info(player));
  }

  @Director(origin = DirectorOrigin.PLAYER, description = "Show claim borders", descriptionKey = "claims.help-boundaries")
  public void boundaries() {
    playerAction((service, player) -> service.interactions().boundaries(player));
  }

  @Director(origin = DirectorOrigin.PLAYER, description = "Invite a player", descriptionKey = "claims.help-invite")
  public void invite(@Param(description = "Target player", descriptionKey = "claims.param-player") Player player) {
    playerAction((service, founder) -> service.interactions().invite(founder, player));
  }

  @Director(origin = DirectorOrigin.PLAYER, description = "Accept a city invitation", descriptionKey = "claims.help-accept")
  public void accept() {
    playerAction((service, player) -> service.interactions().accept(player));
  }

  @Director(origin = DirectorOrigin.PLAYER, description = "List city members", descriptionKey = "claims.help-members")
  public void members() {
    playerAction((service, player) -> service.interactions().members(player));
  }

  @Director(origin = DirectorOrigin.PLAYER, description = "Leave your city", descriptionKey = "claims.help-leave")
  public void leave() {
    playerAction((service, player) -> service.interactions().leave(player));
  }

  @Director(origin = DirectorOrigin.PLAYER, description = "Remove a city member", descriptionKey = "claims.help-kick")
  public void kick(@Param(description = "Target player", descriptionKey = "claims.param-player", customHandler = NullableOfflinePlayerHandler.class) OfflinePlayer player) {
    playerAction((service, founder) -> service.interactions().kick(founder, player));
  }

  @Director(origin = DirectorOrigin.PLAYER, description = "Dismantle your anchor", descriptionKey = "claims.help-dismantle")
  public void dismantle(@Param(description = "Confirm the requested release", descriptionKey = "claims.param-confirm", defaultValue = "false") boolean confirm) {
    playerAction((service, player) -> service.interactions().dismantle(player, confirm));
  }

  @Director(description = "Inspect any city or plot", descriptionKey = "claims.help-inspect")
  public void inspect(@Param(description = "Complete city or personal owner UUID", descriptionKey = "claims.param-id") String id) {
    CommandSender sender = BukkitDirectorContext.sender();
    ClaimsService service = administrativeService(sender, INSPECT_PERMISSION);
    UUID target = parseId(id, sender);
    if (service != null && target != null) {
      service.interactions().inspect(sender, target);
    }
  }

  @Director(description = "Remove any city or plot", descriptionKey = "claims.help-remove")
  public void remove(
      @Param(description = "city or plot", descriptionKey = "claims.param-kind") String kind,
      @Param(description = "Complete city or personal owner UUID", descriptionKey = "claims.param-id") String id,
      @Param(description = "Confirm the requested release", descriptionKey = "claims.param-confirm", defaultValue = "false") boolean confirm
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    ClaimsService service = administrativeService(sender, REMOVE_PERMISSION);
    UUID target = parseId(id, sender);
    if (service != null && target != null) {
      service.interactions().remove(sender, kind, target, confirm);
    }
  }

  @Director(description = "Grant Claims XP", descriptionKey = "claims.help-xp")
  public void xp(
      @Param(description = "Positive XP amount", descriptionKey = "claims.param-xp") double amount,
      @Param(description = "Target player", descriptionKey = "claims.param-player") Player player
  ) {
    CommandSender sender = BukkitDirectorContext.sender();
    ClaimsService service = administrativeService(sender, XP_PERMISSION);
    if (service == null) {
      return;
    }
    if (!validXp(amount)) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(ClaimsMessages.INVALID_XP)));
      return;
    }
    CommandTargetExecutor.run(player, () -> grantXp(player, sender, amount), sender);
  }

  static boolean validXp(double amount) {
    return Double.isFinite(amount) && amount > 0;
  }

  private static void grantXp(Player player, CommandSender sender, double amount) {
    AdaptServer server = Adapt.instance == null ? null : Adapt.instance.getAdaptServer();
    AdaptPlayer runtime = server == null ? null : server.getOnlineAdaptPlayer(player.getUniqueId());
    if (runtime == null || !runtime.isRuntimeReady() || runtime.getPlayer() != player) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.ADAPT_SERVER_NOT_READY)));
      return;
    }
    Skill<?> skill = server.getSkillRegistry().getAnySkill("claims");
    PlayerSkillLine line = runtime.getData().getSkillLine("claims");
    if (skill == null || line == null) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(ClaimsMessages.DISABLED)));
      return;
    }
    if (!Double.isFinite(line.getXp() + line.getPooledXp() + amount)) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(ClaimsMessages.INVALID_XP)));
      return;
    }
    XP.award(runtime, skill, new XpAward("claims:administrative", amount, null, true, XpAward.Kind.ADMINISTRATIVE));
    CommandTargetExecutor.send(sender, FConst.success(AdaptLanguage.text(ClaimsMessages.XP_GRANTED,
        trusted("amount", amount), untrusted("player", player.getName()))));
  }

  private static void playerAction(BiConsumer<ClaimsService, Player> action) {
    CommandSender sender = BukkitDirectorContext.sender();
    if (!(sender instanceof Player player)) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.PLAYER_ONLY)));
      return;
    }
    if (!permission(sender, "adapt.claims")) {
      return;
    }
    ClaimsService service = current(sender);
    if (service == null) {
      return;
    }
    if (!service.enabled()) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(ClaimsMessages.DISABLED)));
      return;
    }
    CommandTargetExecutor.run(player, () -> action.accept(service, player), sender);
  }

  private static ClaimsService administrativeService(CommandSender sender, String permission) {
    return permission(sender, permission) ? current(sender) : null;
  }

  private static ClaimsService current(CommandSender sender) {
    ClaimsService service = Adapt.instance == null ? null : Adapt.instance.getClaimsService();
    if (service == null) {
      CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(ClaimsMessages.DISABLED)));
    }
    return service;
  }

  private static boolean permission(CommandSender sender, String permission) {
    if (sender.hasPermission(permission)) {
      return true;
    }
    CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(CommandRuntimeMessages.MISSING_PERMISSION, trusted("permission", permission))));
    return false;
  }

  private static UUID parseId(String id, CommandSender sender) {
    try {
      UUID parsed = UUID.fromString(id);
      if (parsed.toString().equalsIgnoreCase(id)) {
        return parsed;
      }
    } catch (IllegalArgumentException ignored) {
    }
    CommandTargetExecutor.send(sender, FConst.error(AdaptLanguage.text(ClaimsMessages.INVALID_ID)));
    return null;
  }
}
