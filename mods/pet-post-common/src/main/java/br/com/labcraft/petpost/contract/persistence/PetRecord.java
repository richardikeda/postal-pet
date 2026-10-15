package br.com.labcraft.petpost.contract.persistence;

import java.util.UUID;

/** Serialization-free representation of a persisted pet link. Adapters convert it to NBT (1.21) or Codec data (26.2); game-specific storage stays outside the shared contract. */
public record PetRecord(
        UUID uuid,
        String type,
        String name,
        String lastDimension,
        long lastPosition,
        long lastSeen,
        String guideMaterial) {
    public PetRecord(UUID uuid, String type, String name, String lastDimension, long lastPosition, long lastSeen) {
        this(uuid, type, name, lastDimension, lastPosition, lastSeen, "none");
    }
    public PetRecord {
        if (!guideMaterial.equals("silver") && !guideMaterial.equals("gold")) guideMaterial = "none";
    }
}
