package art.arcane.adapt.api.internal;

import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The callbacks a skill or adaptation hands the engine when it attaches. They reach the author's protected hook
 * methods, which the engine cannot call from its own package.
 */
@ApiStatus.Internal
public interface ComponentHooks {
  void activated();

  List<?> companionListeners();

  /**
   * The adaptation's {@code localizedName()}: its replacement name text, an author override, or the conventional
   * name; null to fall back to the id. For a skill, the conventional name.
   */
  @Nullable String localizedName();

  boolean usesLearnerBoundTicking();

  /**
   * The adaptation's {@code effectiveLevel} for {@code player} given the {@code activeLevel} the engine resolved,
   * kept within 0 and its max level; {@code activeLevel} for a skill.
   */
  int effectiveLevel(PlayerRef player, int activeLevel);

  /** {@code config} is an instance of the component's registered configuration class. */
  void normalizeConfig(Object config);

  /** {@code previous} is null on the first load. */
  void configReloaded(Object previous, Object current);
}
