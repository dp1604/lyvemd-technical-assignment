# Repository working rules

1. Follow the supplied Technical Task and house rules. Keep the fictional Aldercrest scenario separate from employer/interview context. Record ambiguities in SPECIFICATION_AND_ARCHITECTURE.md; verify uncertain facts and ask the candidate about unresolved decisions.
2. Use the required Java 21+, Spring Boot 3.x, HAPI FHIR R4 and PostgreSQL 16 stack. Preserve the approved one-service plain HTML/CSS/JavaScript client and the exclusions in the specification.
3. Centralize authorization, visibility and transactional auditing on every clinical read, including totals, derived content and metadata. Fail closed on required audit failure. Untrusted test actor headers are not production authentication.
4. Keep clinical calculations, sources, effective-time context, canonical units, release/proxy policies and retained amendments consistent with docs/DECISIONS.md and the technical contracts. Do not invent clinical values or silently replace an ambiguous requirement.
5. Attachment A is static review material: never fix, build or run it. State actual finding origins and checking methods in PR_REVIEW.md.
6. Use synthetic data only. Exclude credentials, original PDFs/transcripts/correspondence, tools, local databases, generated operational files and raw logs from Git and archives. Preserve authentic Git authorship and history. Inspect changes and run proportionate checks; record actual outcomes and limits in docs/VERIFICATION.md.
7. Every meaningful change needs a meaningful commit and private push. Do not send the assignment, grant access or make the repository public without the candidate's instruction.
8. Maintain AI_WORKING_LOG.md with substantial requests, returns, shipped changes and reasons; retain R1–R6 delegation and the exact access instruction. Credit actual candidate direction/review and AI execution accurately. The verbatim instruction at docs/ai/prompts/access-rules-v1.txt must remain byte-identical.
9. Consult README.md, docs/CANDIDATE_REVIEW.md and docs/VERIFICATION.md for current operation and acceptance scope. Earlier execution checklists and detailed provenance remain in authentic Git history. Do not infer test completion from review selections or packaging.
10. Announce completed numbered tasks to the candidate. Submission is a separate manual candidate action; do not mark it complete before confirmation.

## Set up and run the implementation

When evaluating the runnable application, follow README.md and scripts/README.md. Check prerequisites before starting: Windows, PowerShell 7.2+, JDK 21+, Maven 3.9+, and a complete PostgreSQL 16 binary distribution. Verify the actual versions and paths; set JAVA_HOME/PATH and ATLAS_PG_BIN as documented. Do not assume downloaded tools or a populated dependency cache are included. Allow the first build to resolve dependencies from Maven Central.

From the repository root:

1. Run `pwsh -NoProfile -File ./scripts/start.ps1`. This is a foreground service: keep it running in a managed session and wait for actual readiness at http://127.0.0.1:18081/. A still-running process alone is not readiness. If using a different ATLAS_HTTP_PORT, use that port consistently.
2. In another terminal/session run `pwsh -NoProfile -File ./scripts/load-dataset.ps1`. Wait for successful exit and `ATLAS_DATASET 200 LOAD_AND_VERIFICATION_COMPLETED`. The loader uses its own helper on port 18091 and preserves the dataset across retries. Use generated manifest IDs rather than assuming fixed IDs from another database.
3. Open the client and apply a matching synthetic actor/role from the generated manifest. Verify the requested behavior through the real API/client, including denied reads where relevant. Describe exactly what ran and what succeeded. Use docs/VERIFICATION.md as existing evidence, not as proof that your current run passed.

If a command fails, stop dependent steps, inspect its actual error and the troubleshooting table, and fix the prerequisite or specific cause before retrying. Do not hide errors, disable validation/auditing, remove data or credentials, change the contract, kill unrelated processes, or call a timeout a pass. Report a blocker if the required environment cannot be supplied. Keep local credentials, generated requests, database files and raw logs out of commits and reports. Stop only processes you started when finished. These instructions concern this repository's implementation; never compile, build or run the supplied Attachment A listing.
