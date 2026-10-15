# Postal Pet

![Postal Pet](docs/images/postal-pet-cover.jpg)

Promotional cover art supplied by the maintainer; gameplay screenshots are shown below.

[English](README.md) | [Português do Brasil](docs/pt-BR/README.md) | [Español](docs/es/README.md)

A Fabric mod for pet mailboxes, home links and home ranges. Beta build targets: Minecraft 1.21/Java 21 and 26.2/Java 25. Download the matching JAR from [Releases](https://github.com/richardikeda/postal-pet/releases). Install Fabric Loader 0.19.5 and Fabric API 0.102.0+1.21 (1.21) or 0.160.0+26.2 (26.2). Use matching client/server mod builds. Back up a closed world before upgrading; this is a beta.

## Pet Mailbox recipe

![Pet Mailbox crafting recipe](docs/images/pet-mailbox-recipe.png)

Two iron ingots, one redstone torch, two oak planks, one chest and one oak fence. The screenshot shows the Portuguese game interface; the recipe works in every language.

## Using the mod

Activate the mailbox with Shift + right-click. Hold a Silver or Gold Guide in your main hand and right-click your own tamed pet within eight blocks. The nearest owned mailbox to the pet receives the link. Linking consumes one guide in survival.

Guides yield two items: top row String/String/Empty; middle row String/Slime ball/Nugget; bottom row Empty/Empty/String. Use iron nuggets for silver or gold nuggets for gold. Vanilla leads keep their normal behavior.

To unlink, open the linked mailbox, select the pet, click × and confirm with !. Alternatively, sneak and use a special guide on the pet near its linked mailbox. Unlinking consumes and refunds no guide. To move homes, unlink first and use a new guide near the destination. Avoid equal-distance positions between nearby mailboxes.

The menu lists pets, edits the mailbox name and cycles range through 8, 16, 24 and 32 blocks. Commands: /postalpet list, info, pets, show, radius, rename <name>; aliases /pp and /petpost. Context commands use your nearest mailbox within eight blocks.

## Screenshots

![Romeo, Juliet and their Pet Mailbox in the world, without shaders.](docs/images/mailbox-with-pets-no-shaders.png)

Romeo, Juliet and their Pet Mailbox in the world, without shaders.

![Silver Guide recipe (two guides)](docs/images/silver-guide-recipe.png)

Silver Guide recipe (two guides).

![Gold Guide recipe (two guides)](docs/images/gold-guide-recipe.png)

Gold Guide recipe (two guides).

![Romeo selected: gold pendant and blue collar](docs/images/romeo-gold-pendant-blue-collar.png)

Romeo selected: gold pendant and blue collar.

![Juliet selected: silver pendant and pink collar](docs/images/juliet-silver-pendant-pink-collar.png)

Juliet selected: silver pendant and pink collar.

![Editing the mailbox name to Home; radius set to eight blocks](docs/images/mailbox-rename-home-radius-8.png)

Editing the mailbox name to Home; radius set to eight blocks.

![Confirming unlink: click the exclamation mark again](docs/images/pet-unlink-confirmation.png)

Confirming unlink: click the exclamation mark again.

[Roadmap](docs/roadmap.md)

## Documentation

[Architecture](docs/architecture.md) · [Contributing](CONTRIBUTING.md) · [Testing](docs/testing.md) · [Licenses](docs/licenses.md)

Call/return/follow actions and Habitats, Accessories, Care, Training and Field Guide are planned. Coming-soon buttons do not execute these actions.

Minecraft belongs to its respective owners. This project is not affiliated with Mojang or Microsoft.

