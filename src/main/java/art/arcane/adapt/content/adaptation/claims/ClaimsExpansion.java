package art.arcane.adapt.content.adaptation.claims;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.api.claims.ClaimsConfig;
import art.arcane.adapt.content.skill.SkillClaims;
import art.arcane.adapt.claims.ClaimsService;
import art.arcane.adapt.claims.ClaimsGuide;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ClaimsMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class ClaimsExpansion extends SimpleAdaptation<ClaimsExpansion.Config> {
  public ClaimsExpansion() {
    super("claims-expansion");
    registerConfiguration(Config.class);
    setIcon(Material.MAP);
    setInterval(60000);
  }

  @Override
  public void addStats(int level, Element element) {
    ClaimsConfig config = ((SkillClaims) getSkill()).claimsConfig();
    ClaimRanks ranks = new ClaimRanks(true, 1, 0, Math.max(1, Math.min(4, level)), false);
    element.addLore(C.GREEN + AdaptLanguage.text(ClaimsMessages.EXPANSION_LORE, trusted("count", config.chunkCapacity(ranks))));
    ClaimsGuide.attach(this, level, element);
  }

  @Override
  public void onLevelChanged(Player player, int level) {
    ClaimsService service = Adapt.instance == null ? null : Adapt.instance.getClaimsService();
    if (service != null) {
      service.refreshRanks(player);
    }
  }

  @ConfigDescription("Expansion unlocks for raidable chunk claims.")
  public static final class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 4;
      initialCost = 2;
      maxLevel = 4;
      requires = List.of("claims-founding:1");
    }
  }
}
