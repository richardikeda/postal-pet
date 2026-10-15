package br.com.labcraft.petpost.contract.persistence;

import java.util.List;
import java.util.Objects;

/**
 * Espelho lógico dos campos de um Postal Pet já persistidos pelos adapters.
 *
 * <p>O {@link #storageKey()} reproduz apenas a chave lógica já usada pelos
 * adapters. {@link PostalPetSaveState} usa este record no schema lógico; NBT
 * e Codec continuam sendo responsabilidade dos adapters.</p>
 */
public record PostRecord(
        String dimension,
        long position,
        String owner,
        String name,
        int radius,
        List<PetRecord> pets) {
    public PostRecord {
        pets = List.copyOf(Objects.requireNonNull(pets, "pets"));
    }

    /** Chave lógica estável já usada pelos adapters para dimensão e posição. */
    public String storageKey() {
        return dimension + "|" + position;
    }
}
