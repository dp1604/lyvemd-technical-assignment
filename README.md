# LyveMD technical lead assignment - Aldercrest Atlas

This private repository contains the work for the supplied technical assessment. Aldercrest, Careline, Atlas and the supplied figures are fictional assessment context.

Status: project structure and development environment prepared. Application implementation, dataset generation, acceptance testing and the final written deliverables are pending. This is not yet a runnable submission.

## Required submission components

| Component | Location | Status |
|---|---|---|
| Specification and Architecture Note, with Dana and engineering sections | [SPECIFICATION_AND_ARCHITECTURE.md](SPECIFICATION_AND_ARCHITECTURE.md) | Outline only |
| Build, history, generator and run instructions | src/, scripts/, Git history and this README | Structure only |
| Static review of Attachment A | [PR_REVIEW.md](PR_REVIEW.md) | Pending |
| AI working log | [AI_WORKING_LOG.md](AI_WORKING_LOG.md), docs/ai/ | Maintained through Task 7; delegation and exact adopted instruction recorded; final reconciliation pending |

## Approved approach

One Spring Boot service will serve both the API and a plain HTML/CSS/JavaScript client. Source, tests, database migrations, dataset scripts and evidence have separate locations; see [project structure](docs/PROJECT_STRUCTURE.md). This choice avoids a second frontend build and keeps the client and API on the same origin. Required charts and keyboard access remain implementation obligations.

The required stack is Java 21+, Spring Boot 3.x, HAPI FHIR R4/FHIR 4.0.1 and PostgreSQL 16. The verified development combination is summarized in [environment evidence](docs/evidence/environment.md).

## Run instructions - pending implementation

The final instructions will provide one command to build and start the service, and one command to create and load the deterministic dataset. Those commands do not exist yet. Dataset size, measured response times, eGFR LOINC code and verified clinical sources will be recorded here when implemented and verified.

## Engineering and scope

Follow [engineering and security rules](docs/ENGINEERING_AND_SECURITY.md) and [AGENTS.md](AGENTS.md). The brief excludes identity provisioning, login screens, notifications, billing/insurance, scheduling, Careline write-back and container/cloud deployment. Its test actor headers do not provide production authentication. Attachment A is reviewed statically and will not be built or run.

Only curated source, documents and sanitized evidence belong in this repository. Local tools, credentials, database files, source attachments, generated output and scratch material are excluded. The four completed submission components and recoverable history must be checked together before delivery.

## Development history

See [history preservation and recovery](docs/HISTORY.md). The checkpoint script preserves committed Git history; build/start and dataset commands remain pending.
