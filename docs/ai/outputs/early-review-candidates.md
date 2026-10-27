# Earlier AI review candidates - not finalized Part 3 findings

Status: preliminary AI-origin candidate findings returned during planning. They have not been finalized in PR_REVIEW.md or independently verified by the candidate. Later Tasks 77-81 must check the supplied printed code, clinical sources, scope and consequences before retaining, changing or rejecting any candidate. No supplied PR code was built, run or fixed.

Source: review_plan_audit, message msg_0faf8f73a22427cb016adf9faa33808191a4096328568d78a9, recovered from the actual earlier AI-agent response. The following is a verbatim excerpt of that AI output, not a new verification claim:

The review should cover these substantive categories:

| Category | Verifiable examples and locations |
|---|---|
| Proxy authorization and confidentiality | `ResultService` 31–33 hardcodes sensitive encounter IDs; 57–72 lacks an adult-proxy cutoff; 116–122 does not implement encounter-class/age policy. |
| Existence and metadata disclosure | Controller 52 / Service 94–98 returns an unfiltered total; Controller 104–108 includes the patient’s name in a forbidden response; Service 102–107 has different hidden/missing paths. |
| Audit completeness and atomicity | Controller 54/74/91 records successful responses only; `AccessLogWriter` 27–45 is asynchronous and swallows failures; denied helper 49–52 is unused by the controller. |
| Unit handling | `UnitNormalizer` 30/36 uses display text rather than UCUM code; Mapper 49–50 sends feed values directly to eGFR. |
| Equation and invalid-input handling | `EgfrCalculator` 44–53 mixes equation generations and includes race; line 20’s per-second unit is inconsistent with an unconverted per-minute calculation. |
| Ranges | Resolver 64/89–95 uses today and substitutes age 99; 66–70 applies a universal age-14 split; 75–85 treats nonfemale demographics as male. |
| Response mapping | Mapper 34 assumes scalar values rather than BP components; 37 defaults to normal without a range; 65 always drops correction linkage. |
| Retention and injection | Repository 48–54 interpolates query input; 61–66 replaces existing results destructively. |
| Production behaviour | Service 78/129 loads all patient results, then filters/sorts/pages in memory; Mapper 29 repeats patient lookup per result. |

There is also a static Java issue worth validating: `EgfrCalculator` 55 uses `Optional.of(Math.round(egfr))`, whose boxed value is `Long`, despite the method declaring `Optional<Double>`. Establish this through language semantics without compiling the supplied listing.

The same earlier output cautioned that the PR covers only part of the feature; unseen helpers must not be assigned invented defects; repository search was not called by the listed endpoints; static reading did not establish executed exploits, timing, failing tests or load performance; and the equation should be described as hybrid rather than simply the 2009 equation. Those cautions remain part of the later verification obligation.

Retaining this record preserves AI origin and what was returned. It does not make its assertions verified, approved or submission-ready. Guided candidate verification later must retain the actual discovery origin.
