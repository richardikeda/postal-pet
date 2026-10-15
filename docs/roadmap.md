# Roadmap

Roadmap items describe intended work, not available features or release dates.

## Current foundation

Pet Mailbox crafting and activation; silver/gold guides; unique pet links; explicit unlinking; mailbox names and range; selected-pet portraits, health, growth stage and armor; collar/pendant information; server-validated persistence and networking. The shared core and two version adapters have automated build and contract checks.

## Next milestones

1. Validate the current release on client and dedicated server for each target, including save/restart, ownership checks, unloaded entities and overlapping mailboxes.
2. Stabilize the developer API and boundaries while keeping persisted IDs compatible.
3. Implement typed, server-authorized call, return and follow actions and explicit Home / Roam Area / Free modes.
4. Improve discovery with search, filters, location information and accessibility.

## Optional expansions

- Field Guide: read-only creature scanner, then field journal.
- Habitats: dog house, cat bed, parrot perch and horse stall.
- Accessories: collars, tags, tracker tags, cosmetics and a compass using last known location.
- Care: bowls, water, treats, grooming and toys; lack of care must not kill pets.
- Training: whistle, clicker and targets, after action permissions and behavior limits are defined.

## Version policy

Keep 1.21 and 26.2 behavior aligned. New Minecraft versions require their own adapter, dependency set, CI target and gameplay acceptance. Add-ons keep their own data and must remain removable without damaging core links.
