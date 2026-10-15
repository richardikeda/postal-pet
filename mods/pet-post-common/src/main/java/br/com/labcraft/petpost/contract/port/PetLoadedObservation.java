package br.com.labcraft.petpost.contract.port;

import java.util.UUID;

/** Observação pontual, fornecida pelo adapter, sobre a carga de um pet autorizado. */
@FunctionalInterface
public interface PetLoadedObservation {
    boolean isLoaded(UUID petUuid);
}
