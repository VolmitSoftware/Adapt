package art.arcane.adapt.pack.screen;

import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.KeptView;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.RecipeView;
import art.arcane.adapt.client.view.ToolView;
import art.arcane.adapt.client.view.XpGuideView;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.List;

/**
 * The view models pack screens draw from, the same ones the native client receives, built for one viewer in the
 * viewer's language. Null where the viewer has none yet.
 */
public interface PackViews {
  /** The catalog with the viewer's short names, or null while the player is not ready. */
  CatalogIndex catalog(PlayerRef viewer);

  PlayerView player(PlayerRef viewer);

  /** Changes whenever the player's stored numbers change. */
  long fingerprint(PlayerRef viewer);

  List<RecipeView> recipes(PlayerRef viewer, String adaptationId);

  XpGuideView guide(PlayerRef viewer, String skillId);

  List<KeptView> kept(PlayerRef viewer);

  /** Names of the skill's adaptation groups, in order; empty for an ungrouped skill. */
  List<String> groupLabels(PlayerRef viewer, String skillId);

  /**
   * For a mutated skill, the skill each of its adaptation groups comes from (the lead, the partner or the mutated skill
   * itself), in the order of its catalog {@code rowGroups}; empty for any other skill.
   */
  List<String> groupOwners(PlayerRef viewer, String skillId);

  /** Every mutation tool, each under the adaptation whose page hands it out. */
  List<ToolView> tools(PlayerRef viewer);

  /** Whether Skills shows the skills the viewer has not started, greyed, or leaves them out. */
  boolean inactiveSkills(PlayerRef viewer);

  boolean mutateOffered(PlayerRef viewer, String skillId);

  boolean unmutateOffered(PlayerRef viewer, String skillId);

  /** The skills the viewer may lead a mutation with, in the order the chest lead menu lists them. */
  List<String> mutationLeads(PlayerRef viewer);

  /** The spread for {@code lead} with an optional partner (empty for none), or null when it cannot lead. */
  MutationSpreadView spread(PlayerRef viewer, String lead, String partner);
}
