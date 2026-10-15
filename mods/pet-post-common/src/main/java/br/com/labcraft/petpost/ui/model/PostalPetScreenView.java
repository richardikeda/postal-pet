package br.com.labcraft.petpost.ui.model;

import br.com.labcraft.petpost.contract.network.PetListResponse;
import br.com.labcraft.petpost.contract.network.PetListView;

import java.util.List;
import java.util.Objects;

/**
 * Instantâneo imutável que a tela pode apresentar. A posição, o nonce e a
 * dimensão pertencem ao contexto da abertura e impedem que uma resposta de
 * outra tela se torne visível.
 */
public record PostalPetScreenView(long position, int nonce, String dimension, List<PetListView> pets) {
    public PostalPetScreenView {
        dimension = Objects.requireNonNull(dimension, "dimension");
        pets = List.copyOf(Objects.requireNonNull(pets, "pets"));
    }

    public static PostalPetScreenView from(PetListResponse response) {
        Objects.requireNonNull(response, "response");
        return new PostalPetScreenView(response.position(), response.nonce(), response.dimension(), response.pets());
    }

    public boolean matches(PetListResponse response) {
        return response != null
                && position == response.position()
                && nonce == response.nonce()
                && dimension.equals(response.dimension());
    }
}
