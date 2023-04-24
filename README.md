<div align="center">

# Ignacio
[![License](https://img.shields.io/github/license/aecsocket/ignacio)](LICENSE)
[![CI](https://img.shields.io/github/actions/workflow/status/aecsocket/ignacio/build.yml)](https://github.com/aecsocket/ignacio/actions/workflows/build.yml)

API abstraction layer for physics engines for Minecraft servers

### [GitHub](https://github.com/aecsocket/ignacio) · [Docs](https://aecsocket.github.io/ignacio)

</div>

Ignacio is an integration project which combines a fully-featured physics engine backend into a server-side environment,
allowing a game world's state (blocks, entities, etc.) to influence the physics state, and allow that physics state to be
displayed to clients via vanilla packets (no client mod required).

The project currently uses the [JoltPhysics](https://github.com/jrouwe/JoltPhysics) backend, a rigid-body physics library,
with Java bindings via [jolt-java](https://github.com/aecsocket/jolt-java) and integrated in the `ignacio-jolt` module.

## Features

- [x] Rigid body physics and collision detection
- [x] Integration with the world, so terrain and entities are automatically included as bodies
- [x] Fully server-side; no client mods required
- [x] Compatible with [Folia](https://github.com/PaperMC/Folia)
- [x] Free and open-source under the MIT license

## Motivation

Minecraft is a game that does not have advanced 3D physics - the limits of what it can do is basically ray-tests and
simple AABB collision response. However, physics simulations are really cool, and having it integrate cleanly with the
world would be even more impressive.

The code is implemented as a server-side plugin rather than a client-side mod, because it means that:
- all calculations happen on a single consistent environment
- clients don't need to download any mods for a mod loader (Forge, Fabric etc.) to interact with physics

There have already been projects which integrate some degree of rigid-body physics into the game, however those are mainly
used for one-off specific features, and most are client-side mods. Ignacio acts instead as a framework for other projects
to use to integrate physics into a world.

## Installation

There are currently no plugin JARs available.
