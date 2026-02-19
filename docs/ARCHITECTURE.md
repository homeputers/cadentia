# ARCHITECTURE.md

## High-Level Architecture

``` mermaid
graph TD
    User --> TelegramBot
    TelegramBot --> LLM
    LLM --> BackendAPI
    BackendAPI --> REng
    REng --> SongDataset
    BackendAPI --> TelegramBot
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
        IntentParser
    end

    subgraph Backend
        API
        REng
        Catalog
    end

    subgraph Data
        PostgreSQL
        pgvector
    end

    Telegram --> IntentParser
    WhatsApp --> IntentParser
    IntentParser --> API
    API --> REng
    REng --> Catalog
    Catalog --> PostgreSQL
    PostgreSQL --> pgvector
```

------------------------------------------------------------------------

## Process Flow

``` mermaid
sequenceDiagram
    participant U as User
    participant B as Bot
    participant L as LLM
    participant A as API
    participant R as REng
    participant D as Dataset

    U->>B: Guided inputs + free text
    B->>L: Parse intent
    L->>B: JSON slots
    B->>A: Generate setlist request
    A->>R: Apply constraints
    R->>D: Retrieve candidates
    R->>A: Ordered setlist
    A->>B: Render result
    B->>U: Display proposal
```

------------------------------------------------------------------------

## Recommendation Engine Internal Flow

``` mermaid
graph TD
    Request --> CandidateRetrieval
    CandidateRetrieval --> Scoring
    Scoring --> KeyCenterSelection
    KeyCenterSelection --> Ordering
    Ordering --> ProposalOutput
```

------------------------------------------------------------------------

## Deployment Model

-   Single VPS (API + DB + Bot)
-   Docker containers
-   Reverse proxy (Nginx)
-   Daily DB backups

------------------------------------------------------------------------

## Future Extensions

-   Multi-church tenancy
-   Analytics (which songs used most)
-   Feedback-based learning adjustments
-   Planning Center integration
