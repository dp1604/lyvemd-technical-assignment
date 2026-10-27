# AI working log

Status: started for Task 4. Task 6 must still reconcile earlier substantial planning and environment assistance from the actual conversation and artifacts. This document is not yet a complete submission log.

## T4-001 - Project structure and private repository

- Request: the candidate required defensible engineering, privacy/security practices and strict assignment adherence, requested a private GitHub repository, and asked to continue Task 4. The exact current request is retained in [the prompt record](docs/ai/prompts/task-4-user-request.md).
- Decisions confirmed by the candidate: repository name lyvemd-technical-assignment; plain HTML/CSS/JavaScript served by Spring Boot; use the currently configured Git email with author name Dinitha Gamage. See [decision transcript](docs/ai/prompts/task-4-decisions.md).
- AI returned: the project layout, document outlines, engineering rules, ignore rules and repository setup. A separate AI agent reviewed the proposed scope and privacy boundary; its actual final response is preserved in [the output record](docs/ai/outputs/task-4-structure-audit.md).
- Shipped in this step: the initial structure and clearly labeled document outlines; environment verification summary; engineering/security working rules; authentic Git history beginning at this work. Repository creation and push results are recorded after verification.
- Differences and reasons: the auditor suggested optional package subdivisions; the scaffold reserves only the base package to avoid choosing unneeded abstractions before implementation. No root build configuration or executable feature is claimed at this structure step. The existing local tools, credentials, database, attachments and scratch logs are excluded from Git.
- Verification: directory/file inventory, ignore-rule checks, staged-content inspection, and remote owner/private visibility/commit checks. Detailed repository results are recorded in the Task 4 evidence when complete.

## Required log work still pending

Reconcile earlier substantial AI items from their actual sources; record each subsequent substantial request/output/shipped difference; explicitly document R1-R6 delegation and reasons; preserve the exact access-rule prompt when issued. No access-rule implementation prompt has been invented or reconstructed here.
