---
navigation:
  parent: index.md
  title: Crafting Tree
  icon: network_linker
  position: 1
---

# Crafting Tree

The crafting tree is the planning and confirmation screen for a Craftlines order. It expands the requested result into all required intermediate recipes and raw materials before anything is removed from the Beyond Dimensions network.

## Opening the tree

- From a **JEI recipe page**, click the Craftlines entry button. Automatic planning keeps the exact recipe shown in JEI as the root recipe, while a manual root-node choice explicitly replaces it for the current plan.
- Middle-click an ingredient in a **JEI recipe, ingredient list, or bookmark list**. Craftlines opens an order for that resource and applies the same saved-recipe preference fallback as the network item-slot entry.
- From a **Beyond Dimensions network item slot**, middle-click an item. Craftlines first tries the saved preferred recipe for that result, then falls back to an available recipe when the preference is missing or invalid.

The first loading stage incrementally indexes recipe outputs and publishes the complete tree only when that index is ready. The second stage keeps the tree visible while preparing the immutable planning catalog. Both caches are reused until recipes are reloaded.

## Reading the layout

The requested result is at the top. Its ingredients appear below it, followed by the ingredients of every intermediate recipe. Identical resources at the same recipe level are merged and their quantities are added together.

Each node shows the amount required for this order. Hover it to inspect the selected recipe's direct inputs and output, recipe family, recipe ID, network stock, and planned production count.

- **Green** means existing network stock fully satisfies the node, so that branch does not need to expand.
- **Orange** means stock satisfies only part of the demand; the tree expands only the remaining shortage.
- **Red** marks missing materials or a recipe branch stopped by a dependency cycle.
- A **purple reference** represents a repeated resource whose full node is already shown closer to the root. Left-click the reference to jump to that node.
- A node with an **orange `*`** is a self-increment seed. It shows only the minimum amount needed to run one operation and never becomes a purple reference.

Middle-click a node to collapse or expand its visible descendants. Display folding changes only the view; it never merges server-side production steps or reservations.

## Choosing recipes and ingredients

When a recipe has alternatives, right-click its product node to open the recipe candidate page.

For a tag or OR-ingredient, click the candidate marker at the top-left of its item to open the complete candidate grid. Choose the exact item Craftlines should reserve and use. The grid supports scrolling and paging when there are many choices.

Manual recipe and ingredient choices are saved as planner preferences and reused in later trees. When the current candidate already has a saved preference, a **Forget** button appears at the top-right of the candidate page; clicking it deletes that preference and restores automatic selection. A missing, unloaded, or no-longer-valid preference is ignored. The server still validates every selected recipe and ingredient when previewing and submitting the order.

## Material summary

The panel on the right presents materials as an icon grid with exact quantities in each tooltip. Its views distinguish:

- **Theoretical total** — everything the selected recipe chain consumes.
- **Network extraction** — the amount this order will actually reserve from current network stock.
- **Remainders** — reusable inputs and container items expected to remain or return.

If no complete plan is currently craftable, the panel switches to the missing-material view. This is the fastest place to see what must be added to the network or changed in the tree.

## Quantities and reusable inputs

Changing the requested amount rebuilds all required craft counts. Craftlines rounds each recipe up by its output amount, then propagates the resulting ingredient quantities down the tree. Stable containers and reusable tools are counted once where the recipe semantics permit reuse; consumable inputs scale with the number of operations.

When a recipe consumes its own result and produces more of that resource, such as smithing-template duplication, Craftlines counts operations by the net increase. Ordering 10 from a `1 → 2` recipe expands 10 sets of the other ingredients and one template seed; if the network lacks that seed, the tree still reports a shortage of one.

The inventory calculation is component-aware. The same stack of network stock cannot satisfy several nodes at once, and items with different data components are not silently treated as identical.

## Blocking mode

Blocking mode affects real machine steps, not simulated crafting-grid recipes. When enabled, Craftlines sends one recipe batch and waits for its expected output to return before sending the next. It also waits when the target machine already contains inputs for that recipe. Use it for machines that can mix recipes or retain old inputs; leave it disabled when the machine can safely accept all available batches.

Self-increment recipes are always dispatched one operation at a time on real machines, regardless of the blocking-mode setting, so each returned output can seed the next operation.

## Validation and submission

Automatic candidate search runs against a versioned network inventory snapshot and within the configured depth, branch, and time budgets. Running out of optimization time stops trying new alternatives but may still keep the best complete plan already found.

Submitting does not trust the displayed client tree. The server checks permissions, inventory and recipe revisions, every selected recipe and ingredient, recursion depth, cycles, and the complete fixed plan again. If stock or recipes changed after the preview, submission is rejected and the tree must be refreshed. Resources are reserved only after this validation succeeds.
