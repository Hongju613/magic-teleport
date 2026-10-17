# Changelog

All notable changes to this project are documented here.

## [1.0.0] — 2026-10-18

First public release.

### Added
- Two-way teleport tunnels: bind two 3×3×6 pavilions by right-clicking their core blocks with a
  stick, then travel by right-clicking a core block with an empty hand.
- Strict structure validation against a recorded 54-block template (`/magicteleport template set`),
  anchored on the configurable core block (`/magicteleport block`).
- Countdown (default 3 s) with purple double-spiral particles; teleport cancels the moment the
  player leaves the block or changes world.
- Per-tunnel access control with the "denied after the countdown" behaviour.
- Optional item and coin (Vault) fees; items are delivered into an owner-chosen chest/barrel.
- 54-slot manager GUI with a bordered channel list and a bottom action row
  (new / delete with confirmation / fee items / fee container / buy slot).
- Per-player tunnel limits, purchasable extra slots, and a VIP integration stub.
- World-scoped creation permission (`/magicteleport createallow`).
- Per-world reload (`/magicteleport reload`) for configuration, data and language files.
- Localization: English (`en_US`) and Chinese (`zh_CN`) message files.
- Soft dependency bridge helper (`MagicTeleport.isTunnelCore`) for block-interaction-lock plugins.

### Notes
- Requires Paper 1.21+ and Java 21+.
- GUI interaction is click-only; there is no command equivalent for the fee-item editor.
