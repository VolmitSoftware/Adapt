package art.arcane.adapt.client.view;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.api.mutation.upgrade.UpgradeLoans;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;

import java.util.ArrayList;
import java.util.List;

/** What a player spends knowledge and power on, from the same held adaptations and refund costs an unlearn uses. */
public final class SpendViews {
  private SpendViews() {
  }

  /**
   * Every held adaptation with the knowledge its unlearn would refund and its power slot, then kept and lent originals
   * at their free level. A raised kept original appears twice: its paid levels as learned, its free level as kept.
   */
  public static List<SpendView> build(SkillRegistry registry, AdaptPlayer player) {
    PlayerData data = player.getData();
    List<SpendView> spends = new ArrayList<>();
    for (PlayerData.HeldAdaptation held : data.heldAdaptations()) {
      int floor = SkillUpgrades.keptFloor(held.adaptation(), data);
      spends.add(new SpendView(held.line(), held.adaptation().getName(), held.level(),
          held.adaptation().getRefundCostFor(floor, held.level()), 1, SpendView.Source.LEARNED));
    }
    for (SkillMutation mutation : data.getMutations()) {
      MutatedSkill mutated = registry.getMutatedSkill(mutation.id());
      if (mutated == null) {
        continue;
      }
      for (Adaptation<?> kept : mutated.getKept()) {
        spends.add(new SpendView(mutated.getName(), kept.getName(), SkillUpgrades.keptLevel(kept), 0, 0, SpendView.Source.KEPT));
      }
      if (player.getPlayer() != null) {
        for (UpgradeLoans.Running running : UpgradeLoans.running(player.getPlayer(), player, registry, mutation)) {
          spends.add(new SpendView(mutated.getName(), running.original().getName(), running.level(), 0, 0, SpendView.Source.LENT));
        }
      }
    }
    return spends;
  }
}
