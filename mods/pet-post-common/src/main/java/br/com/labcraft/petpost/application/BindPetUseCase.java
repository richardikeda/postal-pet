package br.com.labcraft.petpost.application;

import br.com.labcraft.petpost.contract.persistence.PetRecord;
import br.com.labcraft.petpost.contract.persistence.PostRecord;

import java.util.ArrayList;
import java.util.List;

/** Keeps one home link per pet and owner, independently of game and storage APIs. */
public final class BindPetUseCase {
    /** Returns a new snapshot; repeated links are rejected before an adapter consumes a guide. */
    public Result execute(List<PostRecord> posts, String owner, String targetDimension, long targetPosition, PetRecord pet) {
        boolean targetFound = posts.stream().anyMatch(post -> post.owner().equals(owner)
                && post.dimension().equals(targetDimension) && post.position() == targetPosition);
        if (!targetFound) {
            return Result.rejected(posts);
        }
        // A change of home must be an explicit unbind followed by a new bind.
        // This also makes a repeated click idempotent and prevents another lead charge.
        if (posts.stream().anyMatch(post -> post.owner().equals(owner)
                && post.pets().stream().anyMatch(candidate -> candidate.uuid().equals(pet.uuid())))) {
            return Result.rejected(posts);
        }

        List<PostRecord> updated = new ArrayList<>(posts.size());
        for (PostRecord post : posts) {
            if (!post.owner().equals(owner)) {
                updated.add(post);
                continue;
            }
            List<PetRecord> pets = new ArrayList<>(post.pets());
            if (post.dimension().equals(targetDimension) && post.position() == targetPosition) {
                pets.add(pet);
            }
            updated.add(new PostRecord(post.dimension(), post.position(), post.owner(), post.name(), post.radius(), pets));
        }
        return Result.accepted(updated);
    }

    public record Result(boolean changed, List<PostRecord> posts) {
        public Result {
            posts = List.copyOf(posts);
        }

        private static Result accepted(List<PostRecord> posts) {
            return new Result(true, posts);
        }

        private static Result rejected(List<PostRecord> posts) {
            return new Result(false, posts);
        }
    }
}
