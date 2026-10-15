package br.com.labcraft.petpost.application;

import br.com.labcraft.petpost.contract.persistence.PostRecord;

import java.util.List;
import java.util.UUID;

/** Removes a link only from the requested mailbox after confirming its owner. */
public final class UnbindPetUseCase {
    public Result execute(List<PostRecord> posts, String owner, String targetDimension, long targetPosition, UUID petUuid) {
        boolean targetContainsPet = posts.stream().anyMatch(post -> post.owner().equals(owner)
                && post.dimension().equals(targetDimension) && post.position() == targetPosition
                && post.pets().stream().anyMatch(pet -> pet.uuid().equals(petUuid)));
        if (!targetContainsPet) {
            return new Result(false, posts);
        }

        List<PostRecord> updated = posts.stream().map(post -> post.owner().equals(owner)
                        && post.dimension().equals(targetDimension) && post.position() == targetPosition
                ? new PostRecord(post.dimension(), post.position(), post.owner(), post.name(), post.radius(),
                post.pets().stream().filter(pet -> !pet.uuid().equals(petUuid)).toList())
                : post).toList();
        return new Result(true, updated);
    }

    public record Result(boolean changed, List<PostRecord> posts) {
        public Result {
            posts = List.copyOf(posts);
        }
    }
}
