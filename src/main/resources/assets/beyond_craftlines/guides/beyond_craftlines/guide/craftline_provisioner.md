---
navigation:
  parent: index.md
  title: Craftline Provisioner
  icon: craftline_provisioner
  position: 3
item_ids:
  - beyond_craftlines:craftline_provisioner
---

# Craftline Provisioner

<BlockImage id="craftline_provisioner" scale="1.5" float="right" />

The Craftline Provisioner is a configurable endpoint for recipe steps that should not be inserted into one directly bound machine. It accepts only resources produced for an active Craftlines order; pipes cannot insert arbitrary contents into it.

## Opening and configuring

Right-click the block, or left-click it while holding a <ItemLink id="network_linker" />, to open its screen.

Enable the recipe types this provisioner should serve, then optionally select material groups within each type. Selecting no material groups makes the provisioner a wildcard for that recipe type. Explicit group endpoints are preferred over wildcard endpoints. Its numeric priority is compared with other endpoints at the same match level.

Clearing all recipe types also clears every wireless device connection, so the provisioner cannot retain stale routing.

## Using wireless edit mode

The provisioner must have at least one enabled recipe type before wireless connections can be edited.

1. Hold a <ItemLink id="network_linker" /> and **right-click the provisioner** to enter wireless edit mode.
2. **Right-click a target device face** to set its supply face. Right-clicking another face on the same device moves the supply connection; repeating the action on its currently bound face disconnects supply.
3. **Sneak-right-click a target device face** to set its extraction face. Sneak-right-clicking another face moves the extraction connection; repeating the action on its currently bound face disconnects extraction.
4. **Right-click air** with the linker to leave wireless edit mode without clearing the provisioner's recipe configuration.

One device may have both a supply face and an extraction face. While editing, supply faces and connection lines are blue, extraction faces and lines are deep orange, and the candidate face under the crosshair is yellow. Outside edit mode, connected faces use a persistent blue-black dimensional frame; the client configuration can hide that persistent frame without disabling edit-mode highlights.

## Stored-output workflow

Order resources routed to the provisioner enter its inventory. External pipes may extract them and deliver them to the intended machine. The order remains in progress until the complete expected output returns to the Beyond Dimensions network.

This workflow is useful when a machine needs a pipe network, filtering, or another transport layer that Craftlines should not control directly.

## Direct wireless supply

Use the <ItemLink id="network_linker" /> to assign a supply face on a remote device. The provisioner sends matching order resources directly to that face through the device's exposed capability.

The delivery button cycles through:

- **Round robin** — rotate across available targets.
- **Nearest first** — try the closest target first.
- **Farthest first** — try the farthest target first.

If a target rejects the current resource, the provisioner continues to the next eligible target instead of stalling immediately.

## Request-time extraction

A remote device may also have an extraction face. Extraction runs only while this provisioner participates in the active request and moves supported results directly from that face into the Beyond Dimensions network; those results do not pass through the provisioner's inventory.

A single device may have both a supply face and an extraction face. By default, one provisioner can connect to 16 distinct devices; the server configuration may change this limit. The GUI and Jade report supply and extraction counts separately.

## Choosing a routing style

- Use a **directly bound machine** when its exposed capabilities are sufficient and one machine should own the whole recipe step; no Craftline Provisioner is needed in this case.
- Use **stored output plus pipes** when an external transport system should distribute inputs.
- Use **wireless supply** when the provisioner should choose among several remote targets itself.
- Add **request-time extraction** when the same provisioner should collect results from those devices only for requests it is serving.
