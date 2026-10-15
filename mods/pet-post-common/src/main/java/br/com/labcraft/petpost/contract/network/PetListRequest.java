package br.com.labcraft.petpost.contract.network;

/** Logical request represented by each adapter's version-specific packets. */
public record PetListRequest(long position, int nonce) {
}
