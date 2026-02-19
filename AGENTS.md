# AGENTS.md

## Purpose

This document defines context and working instructions for AI agents
collaborating on the Worship Setlist Assistant project.

------------------------------------------------------------------------

## Project Context

This project supports worship teams by recommending structured song sets
based on: - Scripture focus - Spiritual themes - Musical constraints -
Doctrinal review

The system must prioritize: - Safety (no hallucinated songs) -
Transparency - Deterministic selection logic

------------------------------------------------------------------------

## Agent Responsibilities

### 1. LLM Intent Agent

-   Parse free-text inputs
-   Extract structured JSON slots
-   Never invent songs
-   Never output prose when JSON is required
-   Respect defined JSON schema

### 2. Recommendation Agent (REng)

-   Never rely on LLM for song selection
-   Enforce constraints:
    -   10 praise + 5 worship (default)
    -   Minimal key centers
    -   Relative major/minor transitions allowed
    -   Controlled tempo jumps
-   Return scored and ordered result

### 3. Data Integrity Agent

-   Validate lyrics source provenance
-   Enforce approved-only filtering
-   Maintain audit logs

------------------------------------------------------------------------

## JSON Contract (Intent Output)

The LLM must output:

{ "intent": "GENERATE_SETLIST", "slots": { "verseText": "...",
"themeHints": \[\], "counts": {"praise": 10, "worship": 5}, "keyPolicy":
{ "preferSameKey": true, "allowRelativeMajorMinor": true,
"maxKeyCenters": 2 }, "tempoPolicy": { "maxJumpBpm": 12 } } }

------------------------------------------------------------------------

## Guardrails

-   LLM cannot select songs.
-   Only backend REng may generate setlists.
-   All LLM output must pass JSON schema validation.
-   All recommendations must cite dataset references.
