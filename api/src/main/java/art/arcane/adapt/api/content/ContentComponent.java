package art.arcane.adapt.api.content;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.Milestone;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/** What a registered skill and a registered adaptation have in common. */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface ContentComponent {
  /** The namespaced id this component is registered under, such as {@code adapt:swords}. */
  Id getId();

  /** The id as a string, {@code namespace:path}. Stable; use it wherever a string key is needed. */
  String getName();

  /** The namespace of the addon that registered this component, which for an extension is not the skill's. */
  String getOwner();

  /** False when its config disables it or a veto stands against it. A disabled component does nothing. */
  boolean isEnabled();

  /** Whether this component may show particles, after the server's, the skill's and its own switches. */
  boolean areParticlesEnabled();

  /** Whether this component may play sounds, after the server's, the skill's and its own switches. */
  boolean areSoundsEnabled();

  /** True between activation and retirement; false for an instance a reload replaced. */
  boolean isRuntimeRegistered();

  /** What this component needs from the Minecraft version. A component with an unmet need is left out. */
  List<PlatformNeed> getPlatformNeeds();

  /** The choices each player can make for this component. */
  List<PlayerPreference<?>> getPlayerPreferences();

  /** The advancements this component registered, in registration order. */
  List<AdaptAdvancement> getAdvancements();

  /** The milestones this component registered, in registration order. */
  List<Milestone> getMilestones();
}
