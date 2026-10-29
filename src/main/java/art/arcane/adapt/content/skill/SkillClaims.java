package art.arcane.adapt.content.skill;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.claims.ClaimsConfig;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.content.adaptation.claims.ClaimsCitizenship;
import art.arcane.adapt.content.adaptation.claims.ClaimsExpansion;
import art.arcane.adapt.content.adaptation.claims.ClaimsFortification;
import art.arcane.adapt.content.adaptation.claims.ClaimsFounding;
import art.arcane.adapt.content.adaptation.claims.ClaimsHomestead;
import art.arcane.adapt.claims.ClaimsService;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.ClaimsMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDoc;
import org.bukkit.Material;

public final class SkillClaims extends SimpleSkill<SkillClaims.Config> {
  public SkillClaims() {
    super("claims", SkillPresentation.of(ClaimsMessages.SKILL_NAME, ClaimsMessages.SKILL_ICON, ClaimsMessages.SKILL_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.GOLD);
    setIcon(Material.LODESTONE);
    setInterval(60000);
    registerAdaptation(new ClaimsFounding());
    registerAdaptation(new ClaimsFortification());
    registerAdaptation(new ClaimsCitizenship());
    registerAdaptation(new ClaimsExpansion());
    registerAdaptation(new ClaimsHomestead());
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  public ClaimsConfig claimsConfig() {
    return getConfig().claims;
  }

  @Override
  protected void normalizeLoadedConfig(Config config) {
    if (config.claims == null) {
      throw new IllegalArgumentException("Claims configuration is required");
    }
    config.claims.validate();
  }

  @Override
  protected void onConfigReload(Config previous, Config current) {
    super.onConfigReload(previous, current);
    ClaimsService service = Adapt.instance == null ? null : Adapt.instance.getClaimsService();
    if (service != null) {
      service.reconfigure();
    }
  }

  public static final class Config extends SkillConfig {
    @ConfigDoc(value = "Enables the optional Claims skill and its land protection.", impact = "Claims XP is granted through administrative commands.")
    public boolean enabled = false;
    public String skillColor = "&6";
    @ConfigDoc(value = "World restrictions, city capacities, raid resistance, and invitation limits.", impact = "Existing land survives lower progression limits; new claims follow the current limits.")
    public ClaimsConfig claims = new ClaimsConfig();
  }
}
