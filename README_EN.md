# Beyond: Craftlines

**English** | [简体中文](README.md)

Recipe-driven autocrafting for Beyond Dimensions. Beyond: Craftlines turns JEI recipes into interactive recursive crafting trees, reserves resources from BD networks, schedules crafting grids and real machines, collects their outputs, and carries production from “find the recipe” to “receive the result” through persistent orders that can be inspected and cancelled.

## Features

- **Order directly from JEI, EMI, or the network**: JEI and optional EMI recipe pages expose a Craftlines button; EMI recipes are mapped back to and revalidated by the JEI execution backend. EMI recipe slots, index entries, and favorites support middle-click ordering. Root selection prioritizes explicit Craftlines preferences, a readable EMI BoM preference, then automatic candidates.
- **Readable crafting trees and material totals**: An EMI-style top-down tree merges repeated resources on the same level and provides recipe previews, ingredient alternatives, missing-resource highlighting, and views for theoretical totals, actual network extraction, and remainders.
- **Client preview, server submission validation**: The client searches and computes extraction or shortages against a versioned inventory snapshot. After Submit is clicked, the server linearly recomputes the fixed choices against current stock; any missing material rejects the request without creating an order.
- **Beyond Dimensions network execution**: Orders reserve and extract resources from the relevant BD network and support positive `long` quantities. Orders on the same network are attempted in FIFO order; orders whose non-crafting recipe families are disjoint may run concurrently, and anything rejected by a machine is safely returned to storage.
- **Parallel scheduling within an order**: Recipe steps whose dependencies are complete can advance together. Different machines may work concurrently, while one machine position remains strictly exclusive.
- **Authentic recipe and container behavior**: Crafting-grid steps invoke original server logic. JEI virtual recipes distinguish consumed, permanently reusable, and durability-limited inputs and recognize public tool/output-container accessors; Farmer's Delight bowls and bottles route through a dedicated `container` group.
- **Recipe-machine automation**: Use the Network Linker to bind or unbind vanilla and third-party machines; BD network components are excluded. Craftlines deterministically maps JEI catalyst categories to server-side `RecipeType` IDs, then uses each machine's real capabilities for input insertion, output detection, and rollback without hard-coded branches for individual technology mods.
- **Native network furnace support**: Beyond Dimensions network furnaces, blast furnaces, and smokers require no manual binding. Craftlines locates an idle matching furnace on the same network, inserts the inputs, and waits for the real result to return to network storage.
- **Controlled batch scheduling**: AE2-style blocking mode sends only one recipe batch at a time and waits until its result has been collected before sending the next batch. It also waits when a target machine already contains inputs for that recipe.
- **Generic self-increment recipes**: when a recipe consumes its own result and has positive net output, the tree withdraws only one operation's minimum seed, expands other inputs by net growth, and automatically dispatches real machines one operation at a time.
- **Cycle recovery and bucket/fluid alternatives**: a cycle rejects only the current recipe/tag combination, so sibling candidates remain searchable. Bucket-backed crafting prefers network fluid but retains the real bucket item as a manual and automatic shortage fallback.
- **Craftline Provisioners**: A provisioner accepts order-generated resources for selected recipe types and exposes its inventory for external extraction. The Network Linker can assign both a direct-supply face and a request-time direct-extraction face on the same remote device. Supply supports round-robin, nearest-first, and farthest-first ordering. Each provisioner accepts 16 distinct devices by default (server-configurable), with state shown in its GUI, Jade, world highlights, and edit-mode connection lines.
- **Craftline Dashboards**: Attach a thin dashboard to a container or network device, then drop an item, fluid, or chemical into its JEI ghost ingredient slot. It stores the maintained amount, network/container monitoring mode, redstone behavior, blocking mode, and a fixed recipe tree. Automatic refill orders do not consume player order slots and default to ten active orders per network.
- **Persistent orders and status management**: Orders, step progress, and machine bindings survive world saves. Players can inspect network orders, diagnose why a step is waiting, and cancel unfinished work.

## Supported Versions

- Minecraft 1.20.1 / Forge 47 / Java 17
- Minecraft 1.21.1 / NeoForge 21.1 / Java 21
- Minecraft 26.1.2 / NeoForge 26.1.2 / Java 25

Unless explicitly scoped to one version, feature descriptions and development changes apply to all three versions above.

- `mod_id`: `beyond_craftlines`
- Required dependencies: Beyond Dimensions and JEI
- Optional dependencies: GuideME; EMI on Minecraft 1.20.1 Forge and 1.21.1 NeoForge. EMI currently has no 26.1.2 artifact, so that build intentionally provides a no-op bridge rather than claiming runtime support.

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

- `versions/1.20.1/build/libs/beyond_craftlines-0.4.0+1.20.1.jar`
- `build/libs/beyond_craftlines-0.4.0+1.21.1.jar`
- `versions/26.1.2/build/libs/beyond_craftlines-0.4.0+26.1.2.jar`

## Usage Overview

1. Open Craftlines from a JEI recipe page, review the recursive recipe tree and amount, then submit the order. Automatic planning keeps the exact JEI entry recipe at the root, while a manual root-node choice explicitly replaces it and recalculates the plan. The EMI-style tree places the target at the top and expands ingredients downward, merging identical resources on the same recipe level and accumulating their quantities. By default, only the occurrence closest to the root for an identical component-aware resource key is shown in full; later, deeper occurrences are clickable jump references, and this display folding can be disabled in the client config. Hover a node to see its input-to-output preview, recipe family, and ID. The material summary on the right uses an EMI-style icon grid with quantity overlays and can switch between theoretical totals, actual network extraction, and remainders. Missing plans immediately switch to a red missing-resource grid. Click a tag or OR-ingredient node to choose from all valid items, or right-click a recipe node to open its production-recipe candidates. When that candidate already has a saved preference, a Forget button appears at the top-right of the candidate page. Saved recipe and ingredient preferences take priority over automatic suggestions. Client candidate search runs for at most about three seconds; once its node or time budget is exhausted, it starts no new craftable-plan alternatives and only finishes validating the fixed choices in the current recipe tree.
2. Right-click a vanilla or third-party machine with the Network Linker to bind it, or sneak-right-click to unbind it. This experimental build exclusively uses the JEI category UID and current JEI layout to create temporary recipes; it does not load server `RecipeType` aliases or category allowlists. JEI builds a bounded recursive candidate catalog, and only descriptors selected by the current tree are uploaded in pages. JEI input slot names become provisioner sublabels: semantic names such as `catalyst` remain distinct, while numbered generic names such as `input_0` fold into `ingredients`. Anvils, brewing stands, composters, smithing tables, and stonecutters remain provisioner-only.
3. External machines receive inputs and expose outputs through their real capabilities. Craftlines does not contain built-in branches for Mekanism or other individual mods.
4. BD network furnaces, blast furnaces, and smokers do not need to be bound. Orders insert inputs directly into an idle matching furnace on the same network and wait for its actual result to return to storage.
5. Orders on the same BD network attempt to acquire inventory transaction access in FIFO order. An order may proceed when all of its non-crafting recipe families are disjoint from orders already admitted during the tick and `crafting.maxConcurrentOrdersPerNetwork` (default 4) has not been reached. Ordinary `crafting` is ignored for conflicts. Each admitted order advances every dependency-ready step in parallel; different machines may process concurrently, while one machine accepts only one step at a time. Set the option to 1 for strict serial execution.
6. Enable AE2-style blocking mode when ordering to send one recipe batch at a time. Craftlines waits until the previous batch is complete and collected before sending another, and also waits if the target machine already contains inputs for that recipe.
7. Craftline Provisioners accept only order-produced resources and cannot be filled by pipes. Right-click one to open its configuration screen; left-clicking it with the linker opens the same GUI. After assigning recipe types, right-click the provisioner with the linker to enter wireless binding mode. Right-click a device face to set or move its supply connection; sneak-right-click a face to set or move its extraction connection. Repeating the matching action on its bound face disconnects only that role. One device may have both roles and still counts as one of the default 16 distinct-device limit. While editing, supply faces and their lines to the provisioner are blue, extraction faces and lines are deep orange, and the crosshair candidate is yellow. Outside editing, every connected face uses the persistent blue-black dimensional pixel frame. Client option `binding.showProvisionerBoundFaceFrames` independently controls these wireless face frames; it does not affect direct-machine frames controlled by `binding.showBoundMachineFrames`, nor edit-mode colored highlights and lines. Extraction runs only while this provisioner actually participates in the active request and transfers directly from the selected face into the BD network without using provisioner storage. The GUI shows `Supply: x, Extract: y`; its single right-side button cycles round-robin, nearest-first, and farthest-first supply. The server option `provisioner.resetRoundRobinOnRecipeActivation`, enabled by default, restarts round-robin from the first binding for each independent recipe activation and for every feeding round in blocking mode; disabling it preserves continuous polling across requests. A rejecting supply target is skipped so the next target is attempted. Jade shows the same split counts. Clearing recipe types also clears every connection. Sneak-right-click scanning remains the recipe-candidate workflow; outside wireless editing, sneak-right-clicking a machine still removes its direct binding. Recipe groups, side target icons, external extraction, and “Return all to network” retain their previous behavior.
8. Crafting-grid recipes are simulated with their original server implementations, retaining dynamic components, damageable tools, container remainders, and custom `assemble/getRemainingItems` behavior. Stable inputs sharing the same BD component key are processed in batches according to availability, while tools whose damage or components change are advanced one craft at a time. Order quantities are positive `long` values and support up to `Long.MAX_VALUE`.
9. Client-side recipe search, stock consumption, and shortage summaries use versioned inventory snapshots issued by the server. Only after Submit is clicked does the server linearly recompute the fixed choices against current stock; any missing material rejects the request without creating an order. Output defaults to the BD network, can be toggled below the quantity controls to the player inventory, and the client remembers that choice.

## Design Documentation

Current features, architecture, execution semantics, configuration, and compatibility boundaries are documented in [`docs/DESIGN.md`](docs/DESIGN.md). The document describes the behavior of the current source tree rather than removed legacy designs.

Manual acceptance steps for the provisioner recipe confirmation GUI are available in [`docs/ACCEPTANCE.md`](docs/ACCEPTANCE.md), using a Create basin that supports both mixing and compacting recipe types as the example.

## License and Third-Party Notice

This project is licensed as a whole under GNU General Public License version 3. The wireless
provisioner binding interaction references and adapts LGPL-3.0 source from
[AE2 Lightning Tech](https://github.com/ae2lt/AE2-Lightning-Tech); attribution and relevant files are
listed in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md). No CC BY-NC-SA assets from that project are included.
