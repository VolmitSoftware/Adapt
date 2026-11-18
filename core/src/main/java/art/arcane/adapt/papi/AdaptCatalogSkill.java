package art.arcane.adapt.papi;

public record AdaptCatalogSkill(
    String id,
    String nameText,
    boolean enabled,
    int adaptationCount,
    String adaptationCountText
) {
}
