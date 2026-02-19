# Cadentia

## Overview

Cadentia is an AI-assisted recommendation platform designed to help musicians build cohesive, musically-continuous setlists.

The system combines:

- Guided conversational UI (Telegram / future WhatsApp)
- LLM-based intent interpretation layer
- Deterministic Recommendation Engine (REng)
- Curated Song Dataset
- Admin Song Import/Scraper Tool

The platform is designed for church and mission-network use,
prioritizing: 
- Doctrinal safety 
- Musical continuity (no cuts between songs) 
- Key grouping and energy arc modeling 
- Controlled AI usage (LLM for interpretation, not selection)

------------------------------------------------------------------------

## Core Modules

### 1. UI Layer (Chat Interfaces)

-   Telegram Bot (primary)
-   Optional WhatsApp integration
-   Guided menus to gather structured parameters
-   Final free-text "spiritual sensing" input

### 2. LLM Layer

-   Interprets natural language input
-   Extracts intent and slots
-   Requests clarifications when needed
-   Outputs strict JSON to backend
-   Never selects songs directly

### 3. Recommendation Engine (REng)

-   Deterministic scoring and selection
-   Applies constraints (keys, tempo, transitions, counts)
-   Produces setlist proposal ("quote" style)
-   Provides alternatives and transition notes

### 4. Song Dataset

-   Songs
-   Arrangements
-   Tags
-   Lyrics
-   Metadata (tempo, key, time signature)
-   Approval status

### 5. Song Scraper / Import Admin Tool

-   Import artist discography (via metadata providers)
-   Deduplicate tracks
-   Manual lyrics ingestion
-   License provenance tracking
-   Tag enrichment

------------------------------------------------------------------------

## Suggested Directory Structure


    cadentia/
    ├── api/
    │   ├── controller/
    │   ├── dto/
    │   ├── config/
    │   └── Application.java
    │
    ├── bot/
    │   ├── telegram/
    │   ├── whatsapp/
    │   ├── session/
    │   └── BotAdapter.java
    │
    ├── llm/
    │   ├── IntentService.java
    │   ├── LlmClient.java
    │   ├── OpenRouterClient.java
    │   └── SchemaValidator.java
    │
    ├── reng/
    │   ├── CandidateRetriever.java
    │   ├── KeyCenterChooser.java
    │   ├── SongSelector.java
    │   ├── SetOrderer.java
    │   └── SetlistService.java
    │
    ├── catalog/
    │   ├── entity/
    │   ├── repository/
    │   ├── service/
    │   └── LyricsProvider.java
    │
    ├── scraper-admin/
    │   ├── ArtistResolverService.java
    │   ├── DiscographyService.java
    │   ├── SongDeduper.java
    │   └── ImportService.java
    │
    ├── db/
    │   └── migrations/
    │
    └── docs/
        └── ARCHITECTURE.md

------------------------------------------------------------------------

## Proposed Tech Stack

-   Java 21
-   Spring Boot 3
-   PostgreSQL + pgvector
-   Telegram Bot API
-   OpenRouter or OpenAI (LLM)
-   Docker deployment
-   Flyway migrations

------------------------------------------------------------------------

## Design Philosophy

-   AI assists, but deterministic engine decides
-   Songs always selected from curated dataset
-   Hard constraints enforced in backend
-   Transparent explanations to build user trust
