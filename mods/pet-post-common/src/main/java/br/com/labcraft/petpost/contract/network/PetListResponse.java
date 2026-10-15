package br.com.labcraft.petpost.contract.network;

import java.util.List;
import java.util.Objects;

/** Accepted or denied logical response; denied responses must not contain pets. */
public record PetListResponse(
        long position,
        int nonce,
        String dimension,
        boolean accepted,
        List<PetListView> pets, String postName, int radius) {
    public PetListResponse(long position, int nonce, String dimension, boolean accepted, List<PetListView> pets) {
        this(position, nonce, dimension, accepted, pets, "", 0);
    }
    public PetListResponse {
        pets = List.copyOf(Objects.requireNonNull(pets, "pets"));
        if (!accepted && (!pets.isEmpty() || !postName.isEmpty() || radius != 0)) {
            throw new IllegalArgumentException("Uma resposta recusada não pode expor pets");
        }
    }
}
