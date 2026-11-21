package art.arcane.adapt.client.server;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.platform.entity.PlayerRef;

/**
 * Entry points for the skill screens a player asks for. A player whose client negotiated
 * {@link ClientProtocol#SCREENS} gets the native screen through an {@code OpenRoute} frame; everyone else gets the
 * chest GUI. Navigation inside the chest GUI keeps opening chest windows directly.
 */
public final class AdaptScreens {
  private AdaptScreens() {
  }

  public static void openSkills(PlayerRef player) {
    if (player == null) {
      return;
    }
    if (!route(player, new Route.Skills())) {
      SkillsGui.open(player);
    }
  }

  public static void openSkill(PlayerRef player, Skill<?> skill) {
    openSkill(player, skill, false);
  }

  /** Opens the skill when it is enabled and, if {@code checkPermissions}, the player may use it. */
  public static boolean openSkill(PlayerRef player, Skill<?> skill, boolean checkPermissions) {
    if (player == null || skill == null) {
      return false;
    }
    if (!screens(player)) {
      return skill.openGui(player, checkPermissions);
    }
    if (!skill.isEnabled() || (checkPermissions && !skill.hasUsePermission(player, skill))) {
      return false;
    }
    return route(player, new Route.Skill(skill.getName())) || skill.openGui(player, checkPermissions);
  }

  public static void openAdaptation(PlayerRef player, Adaptation<?> adaptation) {
    openAdaptation(player, adaptation, false);
  }

  /** Opens the adaptation when it and its skill are enabled and, if {@code checkPermissions}, the player may use it. */
  public static boolean openAdaptation(PlayerRef player, Adaptation<?> adaptation, boolean checkPermissions) {
    if (player == null || adaptation == null) {
      return false;
    }
    if (!screens(player)) {
      return adaptation.openGui(player, checkPermissions);
    }
    if (!adaptation.isEnabled() || adaptation.getSkill() == null || !adaptation.getSkill().isEnabled()
        || (checkPermissions && !adaptation.hasUsePermission(player, adaptation))) {
      return false;
    }
    return route(player, new Route.Adaptation(adaptation.getName()))
        || adaptation.openGui(player, checkPermissions);
  }

  /** Opens a route a client asked for, with the permission checks of a player opening it themselves. */
  public static void open(PlayerRef player, Route route) {
    SkillRegistry registry = registry();
    switch (route) {
      case Route.Skills ignored -> openSkills(player);
      case Route.Skill skill -> {
        Skill<?> target = registry == null ? null : registry.getSkill(skill.skillId());
        openSkill(player, target, true);
      }
      case Route.Adaptation adaptation -> {
        Adaptation<?> target = registry == null ? null : ClientUiService.adaptation(registry, adaptation.adaptationId());
        openAdaptation(player, target, true);
      }
    }
  }

  private static boolean screens(PlayerRef player) {
    return ClientUiService.negotiated(player, ClientProtocol.SCREENS);
  }

  private static boolean route(PlayerRef player, Route route) {
    ClientUiService service = ClientUiService.current();
    return service != null && service.has(player.uuid(), ClientProtocol.SCREENS)
        && service.send(player.uuid(), new ServerFrame.OpenRoute(route));
  }

  private static SkillRegistry registry() {
    AdaptCore core = AdaptCore.current();
    AdaptServer server = core == null ? null : core.getAdaptServer();
    return server == null ? null : server.getSkillRegistry();
  }
}
