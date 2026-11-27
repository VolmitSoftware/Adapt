package art.arcane.adapt.api.internal;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.api.AdaptClock;
import art.arcane.adapt.api.AdaptSettings;

import art.arcane.adapt.api.content.ContentCatalog;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.player.PlayerDirectory;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.api.content.ContentVeto;
import org.jetbrains.annotations.ApiStatus;

/**
 * Everything the API asks of a running core. The engine implements it once per core.
 *
 * <p>Engine and API ship in one jar, so this interface changes freely between releases and is left out of the
 * compatibility check.
 */
@ApiStatus.Internal
public interface EngineBridge {
  /**
   * Called from the constructor of a skill, before its subclass has initialised. The engine records the instance and
   * the hooks and must not call into either until the supplier that is building the instance has returned.
   */
  SkillRuntime attach(SimpleSkill<?> skill, ComponentHooks hooks);

  /** As {@link #attach(SimpleSkill, ComponentHooks)}, for an adaptation, mutation block, upgrade or signature. */
  AdaptationRuntime attach(SimpleAdaptation<?> adaptation, ComponentHooks hooks);

  ContentCatalog content();

  PlayerDirectory players();

  /** The platform as addons see it: forwarding views without the host-only operations. */
  AdaptPlatform platform();

  AdaptSettings settings();

  PlatformLog log();

  /** Wall time and the server tick. Tests substitute it on the engine side. */
  AdaptClock clock();

  TextRuntime text();

  HudRuntime hud();

  ItemRuntime items();

  FxRuntime fx();

  MutationRuntime mutations();

  ViewerRuntime viewers();

  WorldRuntime world();

  CombatRuntime combat();

  IntegrationRuntime integrations();

  BuildRuntime builds();

  /**
   * An addon was installed while this core runs; apply it now. A no-op for an addon that is already applied. Every
   * callback that acts for an addon or a listener takes the host key and refuses any other.
   */
  void addonInstalled(HostKey key, InstalledAddon addon);

  /** Drop everything the addon registered and run its {@code register} again. */
  void addonRefreshed(HostKey key, InstalledAddon addon);

  /** Drop everything the addon registered. */
  void addonUninstalled(HostKey key, InstalledAddon addon);

  /** Block {@code content} in the addon's name. */
  ContentVeto veto(HostKey key, InstalledAddon addon, Id content);

  /** Put a listener's {@code @Handle} methods on this core's bus. */
  void listenerSubscribed(HostKey key, Object listener);

  void listenerUnsubscribed(HostKey key, Object listener);

  /** Whether this core has begun to stop. */
  boolean isStopping();

  /**
   * Posts an addon's event to this core's bus. Refuses Adapt's own event types through
   * {@link EventSubscriptions#requireAddonEvent}, as {@code AdaptApi.events().post} does.
   */
  void post(AdaptEvent event);

  /** Player progress, XP bookkeeping and platform checks that content asks of the engine. */
  ProgressRuntime progress();

  /** Ability helpers: velocity bursts, double jumps, item wear and vanilla experience. */
  AbilityRuntime abilities();
}
