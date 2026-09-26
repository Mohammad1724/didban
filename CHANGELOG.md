# Changelog

## Unreleased — 2026-09-26

### UI and product audit

- Completed the final product-owner audit for DNS, Tunnel, Manage Servers and Workbench.
- Made narrow layouts predictable for record actions, saved-server actions, SSH/SFTP server selectors and active-file controls.
- Moved visible clipboard labels into localized `CommandCopy` entries and kept important dimensions in `CommandMetrics`.
- Added explicit semantics and touch-target coverage for SFTP entries and batch selection.
- Added deterministic Compose coverage for the local empty states of DNS, Tunnel, Manage Servers and SFTP at 320dp with a 1.5 font scale.
- Aligned the Network Tools and DNS indexes with the shared section-title and `CommandToolLink` primitives: no custom hero, no grouped-card exception and no duplicated page title.

### Verification

- Android CI and Agent CI are green for the final audit commit: [Android #318](https://github.com/Mohammad1724/didban/actions/runs/36188090880) and [Agent #312](https://github.com/Mohammad1724/didban/actions/runs/36188090887).
- The Android report artifact includes the existing redesign screenshot output under `app/build/reports/redesign/**`.
- The current repository already has a `v0.7.0` tag; no new release tag is created by this audit. The next version tag should be chosen as part of the release decision.

### CI maintenance

- Updated GitHub Actions pins to Node 24-compatible releases.
- Pinned runners to `ubuntu-24.04` to avoid the upcoming `ubuntu-latest` image migration warning.
- Kept every action reference pinned to a full commit SHA.
