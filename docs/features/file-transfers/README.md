# File Transfers

## Current behavior

The Files destination is an unboxed list surface intended for shared-file rows. While empty, it says “No files shared yet.” It does not select, send, receive, or store files; no transfer records are currently available to populate the list.

## Planned integration boundary

A complete transfer feature will need transfer records, peer discovery and transport integration, progress/error states, and a policy for temporary and completed files. None of those behaviors are currently connected to this screen.

## Implementation

- Screen: `app/src/main/java/com/meshdrop/ui/fragment/FilesFragment.java`
- Layout: `app/src/main/res/layout/fragment_files.xml`
