package br.com.labcraft.petpost.contract.persistence;

import java.util.UUID;

/**
 * Espelho lógico, sem serialização, de um vínculo persistido de pet.
 *
 * <p>Os adapters continuam responsáveis por converter este formato futuro para
 * NBT (1.21) ou Codec (26.2). A presença deste record ainda não muda nenhuma
 * leitura ou gravação de {@code SavedData}.</p>
 */
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
