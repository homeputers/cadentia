# ADR-008 Implementation Plan: Song Acquisition and Import Connector Architecture

## Objective

Create a connector architecture that stages imported song data with provenance,
licensing, validation, deduplication, and review gates. Connectors may normalize
candidates, but they must never directly create recommendable catalog records.

## Subtask 1: Define connector interfaces and lifecycle contracts

### Context

ADR-008 defines a common connector lifecycle: configure, discover, fetch, parse,
normalize, validate, stage, deduplicate, review, promote, and audit.

### Prompt

Design provider adapter and shared import pipeline interfaces that separate
source-specific acquisition from Cadentia import candidate staging. Include
lifecycle state objects, connector capability metadata, and consistent error
translation.

### Acceptance criteria

- Interfaces distinguish provider adapters from shared import pipeline services.
- Connectors declare supported import method, legal mode, credential needs,
  payload types, rate-limit behavior, and automation level.
- Lifecycle events are representable from discovery through staged candidate.
- Unit tests verify a fake connector can traverse the lifecycle without writing
  approved catalog records.

### Restrictions

- Do not implement scraping that violates terms, robots, licensing, or copyright.
- Do not let connector code write directly to canonical approved catalog tables.
- Do not couple provider-specific parsing to shared pipeline validation.

## Subtask 2: Implement import batch and job state orchestration

### Context

Imports must be grouped by batch, tracked by job state, retried safely, and
observable for admin review.

### Prompt

Implement import job orchestration that creates import batches, records connector
runs, tracks job status, captures retryable versus terminal failures, and links
every staged candidate to a batch and source.

### Acceptance criteria

- Job states cover queued, running, succeeded, partially succeeded, failed,
  canceled, and policy-blocked outcomes.
- Every candidate is linked to an import batch and connector run.
- Retry attempts are bounded and audit-visible.
- Partial failures preserve successful staged candidates and record failed source
  records separately.
- Tests cover successful, partial, failed, canceled, and retry-exhausted runs.

### Restrictions

- Do not delete source payload evidence on failure.
- Do not retry policy-blocked connectors automatically.
- Do not mark candidates recommendable from job completion alone.

## Subtask 3: Enforce provenance and licensing gates

### Context

ADR-008 requires every imported candidate to be traceable to a source and to
carry provenance and licensing metadata before review or promotion.

### Prompt

Add provenance validation to the shared pipeline. Require source identifiers,
import method, source URL or file reference where applicable, content hashes,
license type, collected-at timestamp, and operator or connector identity.

### Acceptance criteria

- Candidates without required provenance are rejected or held in a blocked state.
- Licensing metadata supports permitted, manual-review-required, and prohibited
  outcomes.
- Content hashes are generated deterministically for raw and normalized payloads.
- Tests prove missing source, missing license, and prohibited source candidates
  cannot proceed to review-ready state.

### Restrictions

- Do not infer license permission from the presence of lyrics or chords.
- Do not allow operators to bypass provenance requirements with free-text notes.
- Do not store credentials in provenance records.

## Subtask 4: Build first-party safe connectors

### Context

Manual entry, CSV, ChordPro, OpenSong, local Markdown, and approved export
imports are lower-risk connector types that can validate the architecture before
provider-specific automation.

### Prompt

Implement initial safe connectors for manual payloads, CSV files, ChordPro,
OpenSong, and local Markdown repositories. Normalize each source into import
candidate records and raw source documents.

### Acceptance criteria

- Each connector has parser tests with valid and invalid fixture payloads.
- Normalized candidates include title, artist or author data where available,
  lyrics/chord payload references, source metadata, and content hashes.
- Unsupported or ambiguous fields are captured as review notes, not canonical
  facts.
- Connector outputs are staged only as import candidates.

### Restrictions

- Do not add provider-specific network automation in this subtask.
- Do not scrape public websites.
- Do not transform ambiguous source metadata into approved catalog metadata.

## Subtask 5: Add policy-blocked provider adapter behavior

### Context

Some sources such as CCLI or Ultimate Guitar may only be usable through legally
permitted access paths. The architecture must represent blocked or manual-only
connectors safely.

### Prompt

Create policy declarations and blocked-connector behavior for providers that do
not have approved automation terms. The UI and jobs should explain that the
connector is unavailable, manual-only, or requires administrator configuration.

### Acceptance criteria

- Provider adapters can be registered in disabled, manual-only, or enabled mode.
- Disabled adapters return policy-blocked job outcomes before fetch attempts.
- Policy-blocked outcomes are auditable and visible to admins.
- Tests prove blocked adapters perform no network fetches and stage no content.

### Restrictions

- Do not add credentials, scraping logic, or unofficial API calls for blocked
  providers.
- Do not provide instructions for bypassing source restrictions.
- Do not treat policy-blocked failures as retryable technical errors.

## Subtask 6: Integrate deduplication with staged imports

### Context

ADR-008 builds on ADR-003; staged candidates must be deduplicated against
canonical records and other staged records before review.

### Prompt

Invoke deterministic duplicate detection after candidate staging and persist
proposed duplicate matches for admin review. Include match signals and confidence
without automatically merging.

### Acceptance criteria

- Deduplication compares staged candidates to canonical songs and active staged
  candidates.
- Proposed matches include title, artist, lyrics hash, source identifier, and
  confidence signals.
- Review status reflects whether a candidate has no match, possible match, or
  strong match.
- Tests cover duplicate, possible duplicate, and unique candidates.

### Restrictions

- Do not automatically merge based on confidence alone.
- Do not promote candidates during deduplication.
- Do not compare against untrusted fields as if they were approved facts.

## Subtask 7: Record audit logs and operational metrics

### Context

Import activity, errors, retries, reviewer actions, and final outcomes must be
auditable.

### Prompt

Add audit logging and operational metrics for connector runs, policy decisions,
validation failures, staging outcomes, duplicate proposals, retries, and manual
operator actions.

### Acceptance criteria

- Audit records identify actor or connector, batch, candidate, action, timestamp,
  and result.
- Metrics expose counts for staged, blocked, failed, duplicate-suspected, and
  review-ready candidates.
- Sensitive payloads and credentials are excluded from logs.
- Tests verify important lifecycle actions emit audit events.

### Restrictions

- Do not log provider credentials or raw secrets.
- Do not make audit entries mutable except through explicit correction records.
- Do not let metrics become the only source of lifecycle truth.
