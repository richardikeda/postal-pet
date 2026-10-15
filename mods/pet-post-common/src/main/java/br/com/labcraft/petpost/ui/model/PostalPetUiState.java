package br.com.labcraft.petpost.ui.model;

import br.com.labcraft.petpost.contract.network.PetListResponse;
import br.com.labcraft.petpost.contract.network.PetListView;

import java.util.List;
import java.util.Objects;

/** Estado efêmero e imutável da lista de pets; nunca é persistido ou enviado. */
public record PostalPetUiState(
        PostalPetScreenView view,
        SnapshotStatus snapshotStatus,
        int firstPet,
        int selectedPet) {
    public enum SnapshotStatus {
        LOADING,
        UNAVAILABLE,
        READY
    }

    public PostalPetUiState {
        view = Objects.requireNonNull(view, "view");
        snapshotStatus = Objects.requireNonNull(snapshotStatus, "snapshotStatus");
        if (firstPet < 0) throw new IllegalArgumentException("firstPet não pode ser negativo");
        if (selectedPet < -1) throw new IllegalArgumentException("selectedPet inválido");
    }

    public static PostalPetUiState loading(long position, int nonce, String dimension) {
        return new PostalPetUiState(new PostalPetScreenView(position, nonce, dimension, List.of()),
                SnapshotStatus.LOADING, 0, -1);
    }

    /** Atualiza somente o contexto da abertura antes de a resposta chegar. */
    public PostalPetUiState withExpectedDimension(String dimension) {
        return new PostalPetUiState(new PostalPetScreenView(view.position(), view.nonce(), dimension, view.pets()),
                snapshotStatus, firstPet, selectedPet);
    }

    /** Respostas de outra abertura, posto ou dimensão não mudam o estado local. */
    public PostalPetUiState accept(PetListResponse response, int visibleRows) {
        requireVisibleRows(visibleRows);
        if (!view.matches(response)) return this;
        if (snapshotStatus == SnapshotStatus.UNAVAILABLE) return this;
        if (!response.accepted()) return new PostalPetUiState(view, SnapshotStatus.UNAVAILABLE, firstPet, selectedPet);

        PostalPetScreenView acceptedView = PostalPetScreenView.from(response);
        int boundedFirstPet = Math.min(firstPet, maxFirstPet(acceptedView.pets().size(), visibleRows));
        int boundedSelectedPet = selectedPet >= acceptedView.pets().size() ? -1 : selectedPet;
        return new PostalPetUiState(acceptedView, SnapshotStatus.READY, boundedFirstPet, boundedSelectedPet);
    }

    public PostalPetUiState unavailable() {
        return new PostalPetUiState(view, SnapshotStatus.UNAVAILABLE, firstPet, selectedPet);
    }

    public boolean hasSnapshot() {
        return snapshotStatus == SnapshotStatus.READY;
    }

    public List<PetListView> pets() {
        return view.pets();
    }

    public PostalPetUiState select(int petIndex) {
        if (!hasSnapshot() || petIndex < 0 || petIndex >= pets().size()) return this;
        return new PostalPetUiState(view, snapshotStatus, firstPet, petIndex);
    }

    /**
     * Seleciona a linha clicada ou retorna ao contexto da caixa quando ela já
     * estava selecionada. É estado exclusivamente local: não envia nem grava
     * dados do pet.
     */
    public PostalPetUiState toggleSelection(int petIndex) {
        if (!hasSnapshot() || petIndex < 0 || petIndex >= pets().size()) return this;
        return petIndex == selectedPet
                ? new PostalPetUiState(view, snapshotStatus, firstPet, -1)
                : new PostalPetUiState(view, snapshotStatus, firstPet, petIndex);
    }

    /** Limpa explicitamente o detalhe do pet e mantém a lista/rolagem atual. */
    public PostalPetUiState clearSelection() {
        return selectedPet < 0 ? this : new PostalPetUiState(view, snapshotStatus, firstPet, -1);
    }

    /** Desloca uma linha, preservando o comportamento de rolagem atual da tela. */
    public PostalPetUiState scroll(boolean towardsStart, int visibleRows) {
        requireVisibleRows(visibleRows);
        if (!hasSnapshot() || pets().size() <= visibleRows) return this;
        int nextFirstPet = Math.max(0, Math.min(maxFirstPet(pets().size(), visibleRows),
                firstPet + (towardsStart ? -1 : 1)));
        return new PostalPetUiState(view, snapshotStatus, nextFirstPet, selectedPet);
    }

    public List<PetListView> visiblePets(int visibleRows) {
        requireVisibleRows(visibleRows);
        if (!hasSnapshot()) return List.of();
        int endExclusive = Math.min(pets().size(), firstPet + visibleRows);
        return pets().subList(firstPet, endExclusive);
    }

    private static int maxFirstPet(int petCount, int visibleRows) {
        return Math.max(0, petCount - visibleRows);
    }

    private static void requireVisibleRows(int visibleRows) {
        if (visibleRows < 1) throw new IllegalArgumentException("visibleRows deve ser positivo");
    }
}
