package br.com.labcraft.petpost.contract.port;

import java.util.UUID;

/** Adapter-provided observation of whether an authorized pet is currently loaded. */
@FunctionalInterface
public interface PetLoadedObservation {
    boolean isLoaded(UUID petUuid);
}
