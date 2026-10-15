package br.com.labcraft.petpost.application.query;

import br.com.labcraft.petpost.contract.network.PetListView;
import br.com.labcraft.petpost.contract.persistence.PostRecord;
import br.com.labcraft.petpost.contract.port.PetLoadedObservation;

import java.util.List;
import java.util.Objects;

/** Projects links from an already authorized mailbox into the screen's read-only response. Does not locate entities, write data or force chunk loading. */
public final class ListPetsQuery {
    private static final int MAX_PETS = 32;
    private static final int MAX_TEXT = 96;

    public List<PetListView> execute(PostRecord authorizedPost, PetLoadedObservation loadedObservation) {
        Objects.requireNonNull(authorizedPost, "authorizedPost");
        Objects.requireNonNull(loadedObservation, "loadedObservation");
        return authorizedPost.pets().stream().limit(MAX_PETS)
                .map(pet -> new PetListView(clean(pet.name()), clean(pet.type()),
                        loadedObservation.isLoaded(pet.uuid()), pet.uuid().toString(), -1, pet.guideMaterial()))
                .toList();
    }

    private static String clean(String value) {
        return value == null ? "" : value.length() <= MAX_TEXT ? value : value.substring(0, MAX_TEXT);
    }
}
