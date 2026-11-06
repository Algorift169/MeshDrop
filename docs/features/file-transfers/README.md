# File Transfers

## Current behavior

The Files destination presents an empty state and explanatory copy for future transfer history. It does not select, send, receive, or store files.

## Planned integration boundary

A complete transfer feature will need transfer records, peer discovery and transport integration, progress/error states, and a policy for temporary and completed files. None of those behaviors are currently connected to this screen.

## Implementation

- Screen: `app/src/main/java/com/meshdrop/ui/fragment/FilesFragment.java`
- Layout: `app/src/main/res/layout/fragment_files.xml`
