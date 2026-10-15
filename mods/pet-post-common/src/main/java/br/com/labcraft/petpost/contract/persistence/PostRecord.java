package br.com.labcraft.petpost.contract.persistence;

import java.util.List;
import java.util.Objects;

/** Logical representation of mailbox fields persisted by the adapters. {@link #storageKey()} preserves the existing dimension/position key. {@link PostalPetSaveState} uses this record for the logical schema; NBT and Codec remain adapter responsibilities. */
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

    /** Stable dimension/position key shared by the adapters. */
    public String storageKey() {
        return dimension + "|" + position;
    }
}
