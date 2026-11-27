package art.arcane.adapt.engine.asset;

import art.arcane.adapt.platform.value.Id;

import java.util.Objects;

/** What generated art is drawn from: the skill, its kit tint (else its colour) and the item its icon shows. */
public record SkillLook(Id skill, int rgb, Id icon) {
  public SkillLook {
    Objects.requireNonNull(skill, "skill");
    Objects.requireNonNull(icon, "icon");
    rgb &= 0xFFFFFF;
  }
}
