# Beyond: Craftlines

**English** | [简体中文](README.md)

Recipe-driven autocrafting for Beyond Dimensions. Beyond: Craftlines turns JEI recipes into interactive recursive crafting trees, reserves resources from BD networks, schedules crafting grids and real machines, collects their outputs, and carries production from “find the recipe” to “receive the result” through persistent orders that can be inspected and cancelled.

## Features

- **Order directly from JEI**: Craftlines enters the order screen immediately. Stage one builds and caches recipe lookups within the configured per-tick item limit, then publishes the complete tree atomically. Stage two keeps that tree stable while preparing the planning catalog. Both caches survive reopening and are invalidated only by a recipe reload. The recipe used to open the planner remains the root recipe, while lower levels may be planned automatically or changed manually.
- **Readable crafting trees and material totals**: An EMI-style top-down tree merges repeated resources on the same level and provides recipe previews, ingredient alternatives, missing-resource highlighting, and views for theoretical totals, actual network extraction, and remainders.
- **Server-validated planning**: The client searches against a versioned inventory snapshot signed by the server. Before accepting an order, the server deterministically validates the complete proposal again and rejects stale plans when recipes or network contents have changed.
- **Beyond Dimensions network execution**: Orders reserve and extract resources from the relevant BD network and support positive `long` quantities. Orders on the same network are submitted in FIFO order, and anything rejected by a machine is safely returned to storage.
- **Parallel scheduling within an order**: Recipe steps whose dependencies are complete can advance together. Different machines may work concurrently, while one machine position remains strictly exclusive.
- **Authentic recipe and container behavior**: Crafting-grid steps invoke the original server recipe logic, preserving dynamic components, damageable tools, container remainders, and mod-defined `assemble` and remaining-item behavior.
- **Recipe-machine automation**: Use the Network Linker to bind or unbind vanilla and third-party machines; BD network components are excluded. Craftlines deterministically maps JEI catalyst categories to server-side `RecipeType` IDs, then uses each machine's real capabilities for input insertion, output detection, and rollback without hard-coded branches for individual technology mods.
- **Native network furnace support**: Beyond Dimensions network furnaces, blast furnaces, and smokers require no manual binding. Craftlines locates an idle matching furnace on the same network, inserts the inputs, and waits for the real result to return to network storage.
- **Controlled batch scheduling**: AE2-style blocking mode sends only one recipe batch at a time and waits until its result has been collected before sending the next batch. It also waits when a target machine already contains inputs for that recipe.
- **Craftline Provisioners**: A provisioner accepts order-generated resources for selected recipe types supported by its bound machine and exposes its inventory for external extraction. Its configuration screen, target material icon, and Jade information keep the purpose of each production line visible in the world.
- **Persistent orders and status management**: Orders, step progress, and machine bindings survive world saves. Players can inspect network orders, diagnose why a step is waiting, and cancel unfinished work.

## Supported Versions

- Minecraft 1.20.1 / Forge 47 / Java 17
- Minecraft 1.21.1 / NeoForge 21.1 / Java 21
- Minecraft 26.1.2 / NeoForge 26.1.2 / Java 25

Unless explicitly scoped to one version, feature descriptions and development changes apply to all three versions above.

- `mod_id`: `beyond_craftlines`
- Required dependencies: Beyond Dimensions and JEI

This mod is focused exclusively on the ordering system: a JEI recipe-page entry point, an EMI-style recursive recipe tree, AE2/RS-style order confirmation, BD network resource extraction and crafting, vanilla and third-party machine recipe-type bindings, persistent order status and cancellation, and configurable Craftline Provisioners.

Structure capture, sandbox trial production, reports, steady-state production lines, and blueprint copying have moved to the separate `Sky Logistics: Linefold` mod. Beyond: Craftlines no longer registers any structure blocks, items, commands, dimensions, or SavedData for those systems.

## Building

Unless a version is explicitly scoped, changes, verification, and packaging cover all three supported versions. Build them with Java 17, 21, and 25 respectively:

```bash
# Minecraft 1.20.1 / Forge
cd versions/1.20.1 && ../../gradlew --no-daemon build

# Minecraft 1.21.1 / NeoForge (run from the repository root)
cd ../.. && ./gradlew --no-daemon build

# Minecraft 26.1.2 / NeoForge
cd versions/26.1.2 && ../../gradlew --no-daemon build
```

The three artifacts are written to:

- `versions/1.20.1/build/libs/beyond_craftlines-0.1.3+1.20.1.jar`
- `build/libs/beyond_craftlines-0.1.3+1.21.1.jar`
- `versions/26.1.2/build/libs/beyond_craftlines-0.1.3+26.1.2.jar`

## Usage Overview

1. Open Craftlines from a JEI recipe page, review the recursive recipe tree and amount, then submit the order. The root node always uses the exact recipe that opened the planner; automatic planning and manual recipe switching affect only lower levels. The EMI-style tree places the target at the top and expands ingredients downward, merging identical resources on the same recipe level and accumulating their quantities. By default, only the occurrence closest to the root for an identical component-aware resource key is shown in full; later, deeper occurrences are clickable jump references, and this display folding can be disabled in the client config. Hover a node to see its input-to-output preview, recipe family, and ID. The material summary on the right uses an EMI-style icon grid with quantity overlays and can switch between theoretical totals, actual network extraction, and remainders. Missing plans immediately switch to a red missing-resource grid. Click a tag or OR-ingredient node to choose from all valid items, right-click a lower node to switch its production recipe, or Ctrl-right-click to restore automatic selection. Saved recipe and ingredient preferences take priority over automatic suggestions. Client candidate search runs for at most about three seconds; once its node or time budget is exhausted, it starts no new craftable-plan alternatives and only finishes validating the fixed choices in the current recipe tree.
2. Right-click a vanilla or third-party machine with the Network Linker to bind it, or sneak-right-click to unbind it. Left-click an already bound machine while holding the linker to configure its recipe types; doing so never breaks the machine in Creative mode. BD network components, such as network furnaces, cannot be bound. Recipe types are derived from a deterministic mapping between official JEI catalyst category UIDs and server-side `RecipeType` IDs. For machine recipes that leave vanilla `Recipe#getIngredients()` empty, Craftlines reads public input representations for items, fluids, and registered chemicals or other BD resources, then validates, reserves, and inserts those resources on the server.
3. External machines receive inputs and expose outputs through their real capabilities. Craftlines does not contain built-in branches for Mekanism or other individual mods.
4. BD network furnaces, blast furnaces, and smokers do not need to be bound. Orders insert inputs directly into an idle matching furnace on the same network and wait for its actual result to return to storage.
5. Orders on the same BD network acquire inventory transaction access serially in FIFO order. The order holding that access advances every dependency-ready step in parallel; different machines may process concurrently, while one machine accepts only one step at a time. Any rejected amount is returned automatically.
6. Enable AE2-style blocking mode when ordering to send one recipe batch at a time. Craftlines waits until the previous batch is complete and collected before sending another, and also waits if the target machine already contains inputs for that recipe.
7. Craftline Provisioners accept only resources produced by orders and cannot be filled by pipes. Their inventory remains available for external extraction. Right-click a provisioner to open its configuration screen, but recipe types appear only after selecting the provisioner with the Network Linker and scanning its target machine; types cannot be entered manually. If only one candidate exists and the provisioner is still unconfigured, it is selected and enabled automatically when binding completes. When a recipe type exposes multiple logical input groups, narrower multi-select buttons appear below it: selecting none accepts every group, while selecting any buttons restricts the provisioner to those groups. Multiple provisioners may handle different groups of the same recipe step. Routing prefers a provisioner that explicitly selects the current input group; an unrestricted provisioner is only the wildcard fallback for groups without an exact endpoint. After a successful scan, the center of each horizontal side displays an 8×8 Jade-style item icon representing the nearest target block. If the target uses a GeckoLib, ISTER, or another custom item renderer that cannot be baked, a scaled localized device name is displayed instead. The “Return all to network” button in the lower-right corner becomes available while the provisioner contains resources; it returns everything the network can accept and safely leaves rejected remainders in place.
8. Crafting-grid recipes are simulated with their original server implementations, retaining dynamic components, damageable tools, container remainders, and custom `assemble/getRemainingItems` behavior. Stable inputs sharing the same BD component key are processed in batches according to availability, while tools whose damage or components change are advanced one craft at a time. Order quantities are positive `long` values and support up to `Long.MAX_VALUE`.
9. Client-side recipe candidate searches use versioned inventory snapshots issued by the server. The server performs a deterministic linear validation of the complete proposal and rejects the order if the inventory or recipe version has changed.

## Design Documentation

Current features, architecture, execution semantics, configuration, and compatibility boundaries are documented in [`docs/DESIGN.md`](docs/DESIGN.md). The document describes the behavior of the current source tree rather than removed legacy designs.

Manual acceptance steps for the provisioner recipe confirmation GUI are available in [`docs/ACCEPTANCE.md`](docs/ACCEPTANCE.md), using a Create basin that supports both mixing and compacting recipe types as the example.
