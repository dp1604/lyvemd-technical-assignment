# Reproducible commands

Status: the history-checkpoint helper is available. Application build/start and dataset-generation/loading scripts are not implemented yet.

The final submission requires one command that builds and starts the service and one that creates and loads the deterministic dataset. Dataset source belongs under data/ here; generated output belongs in ignored local work. Verification and backup/restore helpers may be added when their corresponding work is performed.

Scripts must accept configuration without committing credentials, fail visibly on errors, keep synthetic data generation deterministic, and document their prerequisites and effects. Application/data command outcomes will be recorded after implementation and execution.

The history command is "pwsh -NoProfile -File ./scripts/checkpoint-history.ps1". See [its scope and verification](../docs/HISTORY.md). It handles committed Git history; it is not either of the required application/data commands.
