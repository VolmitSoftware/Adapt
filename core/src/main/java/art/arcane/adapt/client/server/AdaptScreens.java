package art.arcane.adapt.client.server;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.engine.gui.MutationGui;
import art.arcane.adapt.engine.gui.SkillsGui;
import art.arcane.adapt.pack.link.PackDelivery;
import art.arcane.adapt.pack.screen.PackScreens;
import art.arcane.adapt.platform.entity.PlayerRef;

/**
 * Entry points for the skill screens a player asks for. A player whose client negotiated
 * {@link ClientProtocol#SCREENS} gets the native screen through an {@code OpenRoute} frame; a player whose client
 * loaded the Adapt resource pack gets the pack screen; everyone else gets the chest GUI. Navigation inside the chest
 * GUI keeps opening chest windows directly.
 */
public final class AdaptScreens {
  private AdaptScreens() {
  }

  public static void openSkills(PlayerRef player) {
    if (player == null) {
      return;
    }
    if (route(player, new Route.Skills())) {
      return;
    }
    PackDelivery.opened(player);
    PackScreens pack = pack(player);
    if (pack == null || !pack.openSkills(player)) {
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
    PackScreens pack = pack(player);
    if (!screens(player) && pack == null) {
      return ComponentHost.of(skill).openGui(player, checkPermissions);
    }
    if (!skill.isEnabled() || (checkPermissions && !skill.hasUsePermission(player))) {
      return false;
    }
    return route(player, new Route.Skill(SkillHost.lineKey(skill))) || (pack != null && pack.openSkill(player, SkillHost.lineKey(skill)))
        || ComponentHost.of(skill).openGui(player, checkPermissions);
  }

  public static void openAdaptation(PlayerRef player, Adaptation<?> adaptation) {
    openAdaptation(player, adaptation, false);
  }

  /** Opens the adaptation when it and its skill are enabled and, if {@code checkPermissions}, the player may use it. */
  public static boolean openAdaptation(PlayerRef player, Adaptation<?> adaptation, boolean checkPermissions) {
    if (player == null || adaptation == null) {
      return false;
    }
    PackScreens pack = pack(player);
    if (!screens(player) && pack == null) {
      return ComponentHost.of(adaptation).openGui(player, checkPermissions);
    }
    if (!adaptation.isEnabled() || adaptation.getSkill() == null || !adaptation.getSkill().isEnabled()
        || (checkPermissions && !adaptation.hasUsePermission(player))) {
      return false;
    }
    return route(player, new Route.Adaptation(adaptation.getId().toString()))
        || (pack != null && pack.openAdaptation(player, adaptation.getId().toString()))
        || ComponentHost.of(adaptation).openGui(player, checkPermissions);
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
      case Route.Mutate mutate -> {
        Skill<?> lead = registry == null ? null : registry.getSkill(mutate.skillId());
        if (lead != null) {
          openMutation(player, lead);
        }
      }
    }
  }

  /**
   * Opens the Mutate flow led by {@code lead}: the client's spread when it has one, the pack spread for pack viewers,
   * otherwise the partner menu.
   */
  public static void openMutation(PlayerRef player, Skill<?> lead) {
    if (ClientUiService.negotiated(player, ClientProtocol.MUTATE_SPREAD) && route(player, new Route.Mutate(SkillHost.lineKey(lead)))) {
      return;
    }
    PackScreens pack = pack(player);
    if (pack == null || !pack.openMutate(player, SkillHost.lineKey(lead))) {
      MutationGui.openPartners(player, lead);
    }
  }

  /** Opens the pack's Mutate lead picker for pack viewers; false for everyone else, who get the chest lead menu. */
  public static boolean openMutationLeads(PlayerRef player) {
    PackScreens pack = pack(player);
    return pack != null && pack.openMutateLeads(player);
  }

  private static boolean screens(PlayerRef player) {
    return ClientUiService.negotiated(player, ClientProtocol.SCREENS);
  }

  /** The pack screens when {@code player} gets them, else null. */
  private static PackScreens pack(PlayerRef player) {
    PackScreens screens = PackScreens.current();
    return screens != null && screens.book(player) ? screens : null;
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
