package art.arcane.adapt.mod;

public enum ModLoader {
    FABRIC("Fabric"),
    FORGE("Forge"),
    NEOFORGE("NeoForge");

    private final String displayName;

    ModLoader(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
