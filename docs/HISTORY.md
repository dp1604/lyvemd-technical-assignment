# Development history

The repository retains authentic incremental commits and their original author metadata. Planning before the first repository commit was not reconstructed as earlier development history.

A delivery archive also contains history.bundle, its SHA-256 sidecar and HISTORY_RECOVERY.md. To recover a working repository from the archive, follow that recovery file. Inspect earlier versions with git log and git show. The detailed pre-consolidation AI log and source/adoption records remain at commit 356502c7.

To create another verified checkpoint from a clean working tree, run:

```powershell
pwsh -NoProfile -File ./scripts/checkpoint-history.ps1
```

This verifies a self-contained bundle and an independently restored mirror under ignored work/history. It does not include ignored tools, local configuration, credentials, uncommitted files or databases. Later commits require a new checkpoint. Restore application data separately; Git history is not a database backup.

See [verification](VERIFICATION.md) for runtime evidence and [the working log](../AI_WORKING_LOG.md) for the documented work split and revisions.
