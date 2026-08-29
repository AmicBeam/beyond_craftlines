---
navigation:
  parent: index.md
  title: Network Linker
  icon: network_linker
  position: 2
item_ids:
  - beyond_craftlines:network_linker
---

# Network Linker

<ItemImage id="network_linker" scale="2" float="right" />

The Network Linker associates real recipe machines and Craftline Provisioners with the current Beyond Dimensions network. It does not replace the machine's own inventory or recipe logic: Craftlines still inserts through exposed capabilities and waits for the machine's real output.

## Direct machine binding

1. **Right-click a supported machine** to bind it to the network.
2. **Left-click a bound machine** while holding the linker to configure its recipe types, material groups, and priority. Creative players will not break the machine with this left-click.
3. **Sneak-right-click a bound machine** outside provisioner editing mode to unbind it.

Recipe types are resolved from JEI catalysts and server recipe types. Beyond Dimensions network components, including its native network furnaces, cannot be bound because Craftlines handles them directly.

If a recipe exposes several logical material groups, different endpoints may handle different groups. An explicitly selected group is preferred over an unrestricted endpoint; among equal matches, the higher configured priority wins.

## Teaching a provisioner recipe types

1. **Sneak-right-click the provisioner** to select it for recipe scanning.
2. Use the linker on the manual recipe target. Detected recipe types become candidates on the provisioner.
3. Open the provisioner and enable the recipe types and material groups it should accept.

When only one recipe type is detected, it can be selected automatically. Right-clicking air exits the current linker mode or clears the active selection.

The vanilla brewing stand, smithing table, composter, anvil, and stonecutter are provisioner-only recipe targets. They can label a provisioner without a block entity or item capability, but cannot be bound as direct machines. These bindings are labels only and do not make their menu-based or JEI-only operations executable Craftlines recipe steps.

## Wireless provisioner connections

After a provisioner has recipe types, **right-click it** with the linker to enter wireless connection editing.

- **Right-click a device face** to set or move that device's supply face.
- **Sneak-right-click a device face** to set or move its extraction face.
- Repeat the same action on the matching bound face to disconnect only that role.
- One device may have both roles and still counts as one device toward the provisioner's limit.

During editing, supply faces and lines are blue, extraction faces and lines are deep orange, and the face under the crosshair is yellow. Outside editing, connected faces use the persistent blue-black dimensional frame; this frame can be disabled in the client configuration without disabling edit-mode highlights.

See <ItemLink id="craftline_provisioner" /> for routing, extraction, and delivery behavior.
