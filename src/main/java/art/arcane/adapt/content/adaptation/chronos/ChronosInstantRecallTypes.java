package art.arcane.adapt.content.adaptation.chronos;

record Snapshot(long timestamp,
                String worldName,
                double x,
                double y,
                double z,
                float yaw,
                float pitch,
                double health,
                int foodLevel,
                float saturation,
                float exhaustion,
                int fireTicks) {
}

record RecallXPContext(String fromWorld,
                       double fromX,
                       double fromY,
                       double fromZ,
                       String toWorld,
                       double toX,
                       double toY,
                       double toZ,
                       double distance,
                       double healthRecovered,
                       double hungerRecovered,
                       double saturationRecovered) {
}

record RecallXPFarmStamp(long awardedAt,
                         String fromWorld,
                         double fromX,
                         double fromY,
                         double fromZ,
                         String toWorld,
                         double toX,
                         double toY,
                         double toZ) {
}
