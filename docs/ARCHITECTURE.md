# ARCHITECTURE.md

## High-Level Architecture

Cadentia separates intent parsing from catalog ownership and deterministic
recommendation. The LLM layer may only turn user language into validated JSON
slots; it cannot create catalog songs or select songs for a setlist. The backend
API owns catalog writes and reads against PostgreSQL, and the Recommendation
Engine (REng) uses only approved backend-provided catalog candidates.

``` mermaid
graph TD
    User --> Bot[Telegram / WhatsApp Bot]
    Bot --> IntentParser[LLM Intent Parser]
    IntentParser --> Bot
    Bot --> BackendAPI[Backend API]
    BackendAPI --> CatalogService[Catalog Service]
    CatalogService --> PostgreSQL[(PostgreSQL Source of Truth)]
    BackendAPI --> REng[Recommendation Engine]
    REng --> CatalogService
    REng --> BackendAPI
    BackendAPI --> Bot
```

------------------------------------------------------------------------

## Component Diagram

``` mermaid
graph LR
    subgraph UI
        Telegram
        WhatsApp
    end

    subgraph LLM Layer
        IntentParser[Intent Parser]
        JsonSchema[JSON Schema Validation]
    end

    subgraph Backend[Java Spring Boot API]
        API[Setlist API]
        Catalog[Catalog Data Access]
        REng[Deterministic Recommendation Engine]
    end

    subgraph Data[PostgreSQL]
        Songs[(songs)]
        Arrangements[(arrangements)]
        Lyrics[(lyrics_documents)]
        Tags[(tags)]
        Imports[(import_batches)]
        Provenance[(provenance_records)]
        Approvals[(approval_records)]
    end

    Telegram --> IntentParser
    WhatsApp --> IntentParser
    IntentParser --> JsonSchema
    JsonSchema --> API
    API --> Catalog
    API --> REng
    REng --> Catalog
    Catalog --> Songs
    Catalog --> Arrangements
    Catalog --> Lyrics
    Catalog --> Tags
    Catalog --> Imports
    Catalog --> Provenance
    Catalog --> Approvals
```

------------------------------------------------------------------------

## ADR-001 Catalog Source of Truth

ADR-001 is implemented by Flyway migration
`apps/api/src/main/resources/db/migration/V002__core_catalog_schema.sql`.
PostgreSQL is the source of truth for curated catalog data. The implemented
canonical tables are:

- `songs` — canonical song identities, normalized title lookup fields, optional
  CCLI number, lifecycle status, and doctrinal notes.
- `arrangements` — musical versions owned by a song, including source type,
  language, key, mode, tempo, time signature, duration, energy, difficulty, and
  active/default flags.
- `lyrics_documents` — versioned lyrics/chord documents owned by an
  arrangement, with content hashes, source references, and current-version
  constraints.
- `tags` — controlled taxonomy values for deterministic classification, with
  ADR-007 tag types, descriptions, sort order, and active/inactive lifecycle.
- `tag_aliases` — controlled alternate names that resolve to existing canonical
  tags without creating free-form production vocabulary.
- `song_tags`, `arrangement_tags`, and `lyrics_document_tags` — many-to-many
  assignments from the controlled tag taxonomy to songs, arrangements, and
  lyrics documents.
- `import_batches` — auditable import or fixture load batches.
- `provenance_records` — first-class evidence tied to exactly one song,
  arrangement, or lyrics document and one import batch.
- `approval_records` — first-class review decisions tied to exactly one song,
  arrangement, or lyrics document.

The schema intentionally does not store generated recommendations. It also does
not include ADR-002 recommendation read models, ADR-003 import staging tables, or
future semantic-search structures. Those later ADRs must build on the catalog
model without weakening the rule that only persisted, curated, approved catalog
records are eligible for deterministic recommendation.

### Ownership Boundaries

- **LLM Intent Parser:** extracts request intent and slots only. It must not
  invent songs, write catalog records, or select setlist items.
- **Catalog Data Access:** validates enum-like values and persists canonical
  songs, arrangements, lyrics documents, tags, provenance, approval records, and
  import batches through the PostgreSQL schema created by
  `V002__core_catalog_schema.sql`.
- **Recommendation Engine:** retrieves backend catalog candidates and applies
  deterministic constraints such as counts, key centers, relative major/minor
  transitions, and controlled tempo jumps. It must not rely on the LLM for song
  selection.
- **Data Integrity Workflow:** keeps provenance and approval state as auditable
  data. Recommendation eligibility must be gated by approval requirements rather
  than by unreviewed metadata.

### pgvector Position

`pgvector` is an optional future enrichment for semantic discovery or assisted
review workflows. It is not implemented by ADR-001, is not required for local
schema creation, and must not become the authority for deterministic song
selection. If vector embeddings are added later, they must reference canonical
catalog rows and remain subordinate to provenance, approval, and deterministic
constraint checks.

------------------------------------------------------------------------

## Process Flow

``` mermaid
sequenceDiagram
    participant U as User
    participant B as Bot
    participant L as LLM Intent Parser
    participant A as API
    participant C as Catalog Service
    participant R as REng
    participant D as PostgreSQL Catalog

    U->>B: Guided inputs + free text
    B->>L: Parse intent only
    L->>B: Validated JSON slots
    B->>A: Generate setlist request
    A->>R: Apply constraints
    R->>C: Request eligible catalog candidates
    C->>D: Read curated songs, arrangements, tags, approvals
    D->>C: Catalog rows with provenance/approval state
    C->>R: Candidate arrangements
    R->>A: Scored and ordered proposal
    A->>B: Render result with dataset references
    B->>U: Display proposal
```

------------------------------------------------------------------------

## Recommendation Engine Internal Flow

``` mermaid
graph TD
    Request --> CandidateRetrieval
    CandidateRetrieval --> ApprovalGate
    ApprovalGate --> Scoring
    Scoring --> KeyCenterSelection
    KeyCenterSelection --> Ordering
    Ordering --> ProposalOutput
```

------------------------------------------------------------------------

## Deployment Model

- Single VPS (API + DB + Bot)
- Docker containers
- PostgreSQL managed through Flyway migrations in the API module
- Reverse proxy (Nginx)
- Daily DB backups

------------------------------------------------------------------------


## ADR-016 Setlist Versioning Operations

ADR-016 extends operational observability and lifecycle controls to immutable
setlist lineage. Platform operators must monitor version creation, diff usage,
and retrieval latency while protecting privacy and keeping telemetry
low-cardinality.

### Required Metrics

- `cadentia_setlist_version_created_total`
  - Counter for generated baselines and manual edit commits.
  - Labels constrained to low-cardinality operational facets only.
- `cadentia_setlist_version_diff_requests_total`
  - Counter for diff endpoint demand and outcomes.
- `cadentia_setlist_version_retrieval_latency_seconds`
  - Histogram for version/diff read latency used in SLO monitoring.
- `cadentia_setlist_edit_commit_conflict_total`
  - Counter for optimistic concurrency conflicts and retry pressure.
- `cadentia_setlist_draft_retention_actions_total`
  - Counter for archive/delete/restore lifecycle events.

### Retention Policy

- Immutable published history is never deleted outside explicit policy execution.
- Draft lineages may be archived after 90 days of inactivity.
- Archived draft lineages may be hard-deleted after 365 days when no hold applies.
- Retention jobs must emit auditable action records and be reversible from backup
  checkpoints.

### Operator Runbook

Operational procedures for partial edit commit recovery, conflict retries,
retention execution, and restoration drills are documented in
`docs/runbooks/adr-016-setlist-versioning-operations.md`.

------------------------------------------------------------------------

## Future Extensions

- ADR-002 recommendation candidate read model derived from canonical catalog
  tables.
- ADR-003 import and deduplication staging is documented in
  `docs/import-workflow.md`; future work can add concrete source adapters or
  operator-facing endpoints without bypassing staged review and provenance.
- ADR-004 lyrics parsing and format-specific validation for `lyrics_documents`.
- ADR-005 approval workflows that update `approval_records` and gate
  recommendation eligibility.
- ADR-006 transposition policy over arrangement key and mode metadata.
- ADR-007 taxonomy governance for `tags`, `song_tags`, and `arrangement_tags`.
- Multi-church tenancy.
- Analytics for song usage.
- Planning Center integration.

------------------------------------------------------------------------

## ADR-015 Observability and Operations

The guided menu and conversational request flow adds a state machine boundary
between intent parsing and recommendation execution. Operations must observe how
requests progress from `START` to `CONFIRMED`, and detect churn caused by
clarification loops or session expiry.

### Required Metrics

- `cadentia_request_state_transition_total`
  - Counter for every transition (`from_state`, `to_state`, `channel`,
    `reason`).
  - Allowed low-cardinality labels only; do not include raw user text or IDs.
- `cadentia_request_state_duration_seconds`
  - Histogram for time spent in each state (`state`, `channel`).
  - Used to identify stalled `CLARIFICATION_REQUIRED` or `READY_TO_CONFIRM`
    states.
- `cadentia_request_clarification_total`
  - Counter for clarification prompts (`channel`, `conflict_type`).
- `cadentia_request_confirmation_outcome_total`
  - Counter for confirm/cancel outcomes (`outcome`, `channel`).
- `cadentia_request_expiry_total`
  - Counter for session expiry (`expiry_type`: `inactivity` or `absolute`,
    plus `channel`).

### Logging and Trace Requirements

- Emit structured transition logs per merge/revision event with:
  `session_id`, `channel`, `from_state`, `to_state`, `source`,
  `merge_decision`, and optional `conflict_reason`.
- Redact free-text user content in logs; store only normalized slot keys and
  decision metadata.
- Attach request/trace IDs so API logs can be correlated with bot adapter and
  recommendation calls.

### Alerting Guidance

Define baseline alerts:

- Expiry spike alert: expiry rate > 2x seven-day baseline for 15 minutes.
- Clarification loop alert: repeated clarification retries per session exceed
  threshold (for example 3 retries without confirmation).
- Confirmation funnel regression alert: confirmed/started ratio drops below
  expected weekly baseline.

Operational procedures, troubleshooting steps, and triage checklists are
maintained in `docs/runbooks/adr-015-conversational-flow-operations.md`.

------------------------------------------------------------------------

## ADR-017 Feedback Tuning Observability and Governance Operations

ADR-017 introduces explicit feedback ingestion and deterministic ranking influence.
Operators must monitor ingestion quality, negative-signal drift, and scope-reset
safety without collecting unnecessary personal context.

### Required Metrics

- `cadentia_feedback_events_total`
  - Counter grouped by `outcome` (`accepted`, `rejected`, `skipped`,
    `favorited`) and `scope_layer` (`personal`, `team`, `policy`).
  - Supports capacity planning and anomaly detection for feedback volume shifts.
- `cadentia_feedback_ranking_impact_distribution`
  - Distribution metric for feedback contribution values applied during ranking
    (`min`, `max`, `avg`, candidate count per recommendation run).
  - Used to detect profile drift when feedback dominates or is unexpectedly null.
- `cadentia_feedback_scope_resets_total`
  - Counter for reset actions by `scope_layer` and actor role.
  - Used for governance and incident-response trigger thresholds.

### Audit and Data Governance Requirements

- Feedback mutations and reset actions must emit structured audit entries with:
  `actor_id`, `scope_layer`, `scope_id`, operation type, and timestamp.
- Audit logs must not include free-form personal context or raw conversation
  content; only deterministic identifiers and controlled taxonomy fields.
- Feedback event retention must preserve governance traceability while avoiding
  indefinite storage of personal-layer preference details.

### Alerting Guidance

Define baseline alerts:

- Negative feedback spike alert: `rejected` event rate exceeds 2x seven-day
  baseline for 15 minutes.
- Reset surge alert: `cadentia_feedback_scope_resets_total` exceeds normal
  daily baseline or repeats from same scope in short windows.
- Ranking-impact drift alert: feedback contribution average or max exceeds
  expected profile envelope, indicating tuning misconfiguration.

### Operator Runbook

Operational procedures for role boundaries, reset authorization, anomaly
triage, and retention review are documented in
`docs/runbooks/adr-017-feedback-tuning-operations.md`.

------------------------------------------------------------------------

## ADR-018 Service Plan Observability and Operations

ADR-018 introduces service-plan lifecycle operations that must be monitored
across draft composition, publish validation, and execution readiness.

### Required Metrics

- `cadentia_service_plan_draft_to_publish_total`
  - Counter for successful draft-to-publish transitions.
- `cadentia_service_plan_publish_total`
  - Counter for completed publish operations.
- `cadentia_service_plan_publish_failures_total`
  - Counter for publish conflicts by controlled reason labels.
- `cadentia_service_plan_block_reorder_total`
  - Counter for block reorder activity in draft plans.

### Audit and Logging Requirements

- Publish and revision-sensitive actions must emit structured action codes and
  before/after sequence metadata.
- Do not use free-text fields (for example service title or notes) as metric
  labels.
- Include actor identity and outcome in auditable publish conflict/success logs.

### Alerting and Runbook

Operators should alert on repeated publish conflicts, stale-reference failure
surges, and anomalous reorder churn. Triage and multi-campus fork/share
procedures are documented in
`docs/runbooks/adr-018-service-plan-operations.md`.
