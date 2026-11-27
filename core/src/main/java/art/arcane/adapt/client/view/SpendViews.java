package art.arcane.adapt.client.view;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.engine.mutation.upgrade.Lending;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerData;

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
      spends.add(new SpendView(held.line(), held.adaptation().getId().toString(), held.level(),
          AdaptationHost.refundCostFor(held.adaptation(), floor, held.level()), 1, SpendView.Source.LEARNED));
    }
    for (SkillPair mutation : data.getMutations()) {
      MutatedSkill mutated = registry.getMutatedSkill(mutation);
      if (mutated == null) {
        continue;
      }
      for (Adaptation<?> kept : mutated.getKept()) {
        spends.add(new SpendView(SkillHost.lineKey(mutated), kept.getId().toString(), SkillUpgrades.keptLevel(kept), 0, 0, SpendView.Source.KEPT));
      }
      if (player.getPlayer() != null) {
        for (Lending.Running running : Lending.running(player.getPlayer(), player, registry, mutation)) {
          spends.add(new SpendView(SkillHost.lineKey(mutated), running.original().getId().toString(), running.level(), 0, 0, SpendView.Source.LENT));
        }
      }
    }
    return spends;
  }
}
