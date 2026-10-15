package br.com.labcraft.petpost.application;

import br.com.labcraft.petpost.contract.persistence.PostRecord;

import java.util.Optional;

/** Normalizes a mailbox name after adapter selection, preserving the 32-character limit. */
public final class RenamePostUseCase {
    public Optional<PostRecord> execute(PostRecord post, String owner, String requestedName) {
        if (!post.owner().equals(owner)) {
            return Optional.empty();
        }
        String name = requestedName.strip();
        if (name.isEmpty()) {
            return Optional.empty();
        }
        if (name.length() > 32) {
            name = name.substring(0, 32);
        }
        return Optional.of(new PostRecord(post.dimension(), post.position(), post.owner(), name, post.radius(), post.pets()));
    }
}
