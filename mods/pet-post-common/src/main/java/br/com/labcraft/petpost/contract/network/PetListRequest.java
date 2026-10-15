package br.com.labcraft.petpost.contract.network;

/** Solicitação lógica já representada pelos pacotes específicos de cada adapter. */
public record PetListRequest(long position, int nonce) {
}
