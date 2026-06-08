ALTER TABLE service_arrangement_overrides
    ADD COLUMN capo_fret integer,
    ADD COLUMN transposition_semitones integer,
    ADD COLUMN chart_annotations text,
    ADD COLUMN section_order_notes text,
    ADD COLUMN transition_cues text,
    ADD COLUMN instrumentation_notes text,
    ADD COLUMN asset_selection_notes text,
    ADD CONSTRAINT service_arrangement_overrides_capo_range CHECK (capo_fret IS NULL OR capo_fret BETWEEN 0 AND 12),
    ADD CONSTRAINT service_arrangement_overrides_transposition_range CHECK (
        transposition_semitones IS NULL OR transposition_semitones BETWEEN -11 AND 11
    );

COMMENT ON COLUMN service_arrangement_overrides.capo_fret IS
    'Service-only capo guidance for rehearsal/chart rendering; never canonical arrangement metadata.';
COMMENT ON COLUMN service_arrangement_overrides.transposition_semitones IS
    'Service-only explicit transposition instruction for diagnostics and chart rendering.';
COMMENT ON COLUMN service_arrangement_overrides.chart_annotations IS
    'Service-only chart annotations such as repeats or cue labels; does not duplicate canonical chart content.';
COMMENT ON COLUMN service_arrangement_overrides.section_order_notes IS
    'Service-only section-order notes for rehearsal rendering.';
COMMENT ON COLUMN service_arrangement_overrides.transition_cues IS
    'Service-only transition cues before or after this arrangement in the service plan.';
COMMENT ON COLUMN service_arrangement_overrides.instrumentation_notes IS
    'Service-only instrumentation notes for this service execution.';
COMMENT ON COLUMN service_arrangement_overrides.asset_selection_notes IS
    'Service-only asset selection notes that reference canonical media/assets instead of copying content.';
