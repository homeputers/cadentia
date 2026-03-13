# Mermaid Architecture Diagrams

## 1. High-Level System Architecture

```mermaid
flowchart LR
    U[User]
    TG[Telegram UI]
    WA[WhatsApp UI - future]
    API[Cadentia Backend API]
    LLM[LLM Intent Extractor]
    RENG[Recommendation Engine]
    CATALOG[(Song Catalog DB)]
    READMODEL[(Recommendation Read Model)]
    ADMIN[Admin Tool]
    IMPORT[Import Pipeline]

    U --> TG
    U -. future .-> WA
    TG --> API
    WA -. future .-> API

    API --> LLM
    API --> RENG
    RENG --> READMODEL
    READMODEL --> CATALOG

    ADMIN --> API
    ADMIN --> IMPORT
    IMPORT --> CATALOG
    CATALOG --> READMODEL
```

## 2. Setlist Request Flow

```mermaid
sequenceDiagram
    participant User
    participant UI as Telegram UI
    participant API as Backend API
    participant LLM as Intent Extractor
    participant RE as Recommendation Engine
    participant RM as Read Model
    participant DB as Song Catalog

    User->>UI: Enter verse, theme, preferences
    UI->>API: Submit request
    API->>LLM: Extract structured intent JSON
    LLM-->>API: intent + slots
    API->>RE: Build recommendation request
    RE->>RM: Query eligible candidate arrangements
    RM->>DB: Read approved catalog data
    DB-->>RM: Candidate dataset
    RM-->>RE: Candidate rows
    RE-->>API: Deterministic ranked setlist
    API-->>UI: Ordered recommendations + explanations
    UI-->>User: Suggested setlist
```

## 3. Recommendation Engine Internal Flow

```mermaid
flowchart TD
    A[Structured Request]
    B[Eligibility Filter]
    C[Theme and Scripture Matching]
    D[Musical Constraint Evaluation]
    E[Set Shape Builder]
    F[Ordering and Transition Check]
    G[Explanation Generator]
    H[Recommended Setlist]

    A --> B
    B --> C
    C --> D
    D --> E
    E --> F
    F --> G
    G --> H
```

## 4. Import and Approval Architecture

```mermaid
flowchart TD
    SRC[External Source / Manual Input]
    BATCH[Import Batch]
    STAGE[Staging Candidates]
    DEDUPE[Deduplication Heuristics]
    REVIEW[Admin Review]
    SONG[Canonical Song]
    ARR[Arrangement]
    LYR[Lyrics Document]
    PROV[Provenance Record]
    APPR[Approval Records]
    ELIGIBLE[Eligible for Recommendation]

    SRC --> BATCH
    BATCH --> STAGE
    STAGE --> DEDUPE
    DEDUPE --> REVIEW
    REVIEW --> SONG
    REVIEW --> ARR
    REVIEW --> LYR
    REVIEW --> PROV
    SONG --> APPR
    ARR --> APPR
    LYR --> APPR
    APPR --> ELIGIBLE
```

## 5. Read Model Refresh Architecture

```mermaid
flowchart LR
    DB[(Normalized Catalog)]
    EVENTS[Catalog Changes]
    VIEW[v_recommendable_arrangements]
    MAT[Materialized View - optional]
    RE[Recommendation Engine]

    DB --> EVENTS
    DB --> VIEW
    EVENTS -. trigger/refresh .-> MAT
    VIEW --> RE
    MAT -. optional replacement .-> RE
```

## 6. Transposition Handling Flow

```mermaid
flowchart TD
    A[Canonical Arrangement in Base Key]
    B[Chord Parser]
    C[Transposition Utility]
    D[Requested Key]
    E[Rendered Arrangement]
    F[Recommendation Output / Export]

    A --> B
    B --> C
    D --> C
    C --> E
    E --> F
```
