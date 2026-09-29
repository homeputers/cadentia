package com.cadentia.catalog.musicbrainz;

import com.cadentia.catalog.musicbrainz.MusicBrainzModels.EntitySnapshot;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.EntityType;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.Link;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.Proposal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMusicBrainzRepository implements MusicBrainzRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcMusicBrainzRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public EntitySnapshot saveEntity(EntitySnapshot entity) {
        String sql = """
                INSERT INTO musicbrainz_entities (
                    entity_type, mbid, display_name, normalized_key, source_uri, metadata_json, fetched_at, response_hash
                ) VALUES (
                    :entityType, :mbid, :displayName, :normalizedKey, :sourceUri, CAST(:metadataJson AS jsonb),
                    :fetchedAt, :responseHash
                )
                ON CONFLICT (entity_type, mbid) DO UPDATE SET
                    display_name = EXCLUDED.display_name,
                    normalized_key = EXCLUDED.normalized_key,
                    source_uri = EXCLUDED.source_uri,
                    metadata_json = EXCLUDED.metadata_json,
                    fetched_at = EXCLUDED.fetched_at,
                    response_hash = EXCLUDED.response_hash
                RETURNING id, entity_type, mbid, display_name, normalized_key, source_uri,
                    metadata_json::text AS metadata_json, fetched_at, response_hash
                """;
        return jdbcTemplate.queryForObject(sql, entityParams(entity), entityMapper());
    }

    @Override
    public Optional<EntitySnapshot> findEntity(EntityType type, UUID mbid) {
        return queryOptional("""
                SELECT id, entity_type, mbid, display_name, normalized_key, source_uri,
                    metadata_json::text AS metadata_json, fetched_at, response_hash
                FROM musicbrainz_entities
                WHERE entity_type = :entityType AND mbid = :mbid
                """, Map.of("entityType", type.name(), "mbid", mbid), entityMapper());
    }

    @Override
    public Proposal createProposal(Proposal proposal) {
        String sql = """
                INSERT INTO catalog_musicbrainz_proposals (
                    id, song_id, arrangement_id, artist_mbid, release_group_mbid, release_mbid,
                    recording_mbid, score, match_method, status, source_uri, source_payload, created_by
                ) VALUES (
                    :id, :songId, :arrangementId, :artistMbid, :releaseGroupMbid, :releaseMbid,
                    :recordingMbid, :score, :matchMethod, :status, :sourceUri, CAST(:sourcePayload AS jsonb), :createdBy
                )
                RETURNING id, song_id, arrangement_id, artist_mbid, release_group_mbid, release_mbid,
                    recording_mbid, score, match_method, status, source_uri, source_payload::text AS source_payload,
                    created_by, created_at, updated_at
                """;
        return jdbcTemplate.queryForObject(sql, proposalParams(proposal), proposalMapper());
    }

    @Override
    public Optional<Proposal> findProposal(UUID songId, UUID proposalId) {
        return queryOptional("""
                SELECT id, song_id, arrangement_id, artist_mbid, release_group_mbid, release_mbid,
                    recording_mbid, score, match_method, status, source_uri, source_payload::text AS source_payload,
                    created_by, created_at, updated_at
                FROM catalog_musicbrainz_proposals
                WHERE song_id = :songId AND id = :proposalId
                """, Map.of("songId", songId, "proposalId", proposalId), proposalMapper());
    }

    @Override
    public List<Proposal> findProposals(UUID songId) {
        return jdbcTemplate.query("""
                SELECT id, song_id, arrangement_id, artist_mbid, release_group_mbid, release_mbid,
                    recording_mbid, score, match_method, status, source_uri, source_payload::text AS source_payload,
                    created_by, created_at, updated_at
                FROM catalog_musicbrainz_proposals
                WHERE song_id = :songId
                ORDER BY score DESC, created_at DESC, id
                """, Map.of("songId", songId), proposalMapper());
    }

    @Override
    public Optional<Proposal> updateProposalStatus(UUID songId, UUID proposalId, MusicBrainzModels.ProposalStatus status) {
        return queryOptional("""
                UPDATE catalog_musicbrainz_proposals
                SET status = :status, updated_at = now()
                WHERE song_id = :songId AND id = :proposalId
                RETURNING id, song_id, arrangement_id, artist_mbid, release_group_mbid, release_mbid,
                    recording_mbid, score, match_method, status, source_uri, source_payload::text AS source_payload,
                    created_by, created_at, updated_at
                """, Map.of("songId", songId, "proposalId", proposalId, "status", status.name()), proposalMapper());
    }

    @Override
    public Link createLink(Link link) {
        String sql = """
                INSERT INTO catalog_musicbrainz_links (
                    id, song_id, arrangement_id, proposal_id, artist_mbid, release_group_mbid, release_mbid,
                    recording_mbid, status, score, match_method, selected_fields, reviewer, rationale
                ) VALUES (
                    :id, :songId, :arrangementId, :proposalId, :artistMbid, :releaseGroupMbid, :releaseMbid,
                    :recordingMbid, :status, :score, :matchMethod, CAST(:selectedFields AS jsonb), :reviewer, :rationale
                )
                RETURNING id, song_id, arrangement_id, proposal_id, artist_mbid, release_group_mbid, release_mbid,
                    recording_mbid, status, score, match_method, selected_fields::text AS selected_fields,
                    reviewer, rationale, accepted_at
                """;
        return jdbcTemplate.queryForObject(sql, linkParams(link), linkMapper());
    }

    @Override
    public Optional<Link> findAcceptedLink(UUID songId) {
        return queryOptional("""
                SELECT links.id, links.song_id, links.arrangement_id, links.proposal_id,
                    links.status, links.score, links.match_method, links.artist_mbid, links.release_group_mbid,
                    links.release_mbid, links.recording_mbid,
                    links.selected_fields::text AS selected_fields, links.reviewer, links.rationale, links.accepted_at
                FROM catalog_musicbrainz_links links
                JOIN catalog_musicbrainz_proposals proposals ON proposals.id = links.proposal_id
                WHERE links.song_id = :songId AND links.status = 'ACCEPTED' AND proposals.status = 'ACCEPTED'
                ORDER BY links.accepted_at DESC, links.id DESC
                LIMIT 1
                """, Map.of("songId", songId), linkMapper());
    }

    @Override
    public void updateLinkStatus(UUID songId, UUID proposalId, MusicBrainzModels.ProposalStatus status) {
        jdbcTemplate.update("""
                UPDATE catalog_musicbrainz_links
                SET status = :status
                WHERE song_id = :songId AND proposal_id = :proposalId
                """, Map.of("songId", songId, "proposalId", proposalId, "status", status.name()));
    }

    private MapSqlParameterSource entityParams(EntitySnapshot entity) {
        return new MapSqlParameterSource()
                .addValue("entityType", entity.entityType().name())
                .addValue("mbid", entity.mbid())
                .addValue("displayName", entity.displayName())
                .addValue("normalizedKey", entity.normalizedKey())
                .addValue("sourceUri", entity.sourceUri())
                .addValue("metadataJson", entity.metadataJson())
                .addValue("fetchedAt", Timestamp.from(entity.fetchedAt()))
                .addValue("responseHash", entity.responseHash());
    }

    private MapSqlParameterSource proposalParams(Proposal proposal) {
        return new MapSqlParameterSource()
                .addValue("id", proposal.id())
                .addValue("songId", proposal.songId())
                .addValue("arrangementId", proposal.arrangementId())
                .addValue("artistMbid", proposal.artistMbid())
                .addValue("releaseGroupMbid", proposal.releaseGroupMbid())
                .addValue("releaseMbid", proposal.releaseMbid())
                .addValue("recordingMbid", proposal.recordingMbid())
                .addValue("score", proposal.score())
                .addValue("matchMethod", proposal.matchMethod())
                .addValue("status", proposal.status().name())
                .addValue("sourceUri", proposal.sourceUri())
                .addValue("sourcePayload", proposal.sourcePayload())
                .addValue("createdBy", proposal.createdBy());
    }

    private MapSqlParameterSource linkParams(Link link) {
        return new MapSqlParameterSource()
                .addValue("id", link.id())
                .addValue("songId", link.songId())
                .addValue("arrangementId", link.arrangementId())
                .addValue("proposalId", link.proposalId())
                .addValue("artistMbid", link.artistMbid())
                .addValue("releaseGroupMbid", link.releaseGroupMbid())
                .addValue("releaseMbid", link.releaseMbid())
                .addValue("recordingMbid", link.recordingMbid())
                .addValue("status", link.status().name())
                .addValue("score", link.score())
                .addValue("matchMethod", link.matchMethod())
                .addValue("selectedFields", link.selectedFields())
                .addValue("reviewer", link.reviewer())
                .addValue("rationale", link.rationale());
    }

    private <T> Optional<T> queryOptional(String sql, Map<String, ?> params, RowMapper<T> mapper) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, params, mapper));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private RowMapper<EntitySnapshot> entityMapper() {
        return (rs, rowNum) -> new EntitySnapshot(
                EntityType.valueOf(rs.getString("entity_type")),
                rs.getObject("mbid", UUID.class),
                rs.getString("display_name"),
                rs.getString("normalized_key"),
                rs.getString("source_uri"),
                rs.getString("metadata_json"),
                rs.getTimestamp("fetched_at").toInstant(),
                rs.getString("response_hash"));
    }

    private RowMapper<Proposal> proposalMapper() {
        return (rs, rowNum) -> new Proposal(
                rs.getObject("id", UUID.class),
                rs.getObject("song_id", UUID.class),
                rs.getObject("arrangement_id", UUID.class),
                rs.getObject("artist_mbid", UUID.class),
                rs.getObject("release_group_mbid", UUID.class),
                rs.getObject("release_mbid", UUID.class),
                rs.getObject("recording_mbid", UUID.class),
                rs.getBigDecimal("score"),
                rs.getString("match_method"),
                MusicBrainzModels.ProposalStatus.valueOf(rs.getString("status")),
                rs.getString("source_uri"),
                rs.getString("source_payload"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private RowMapper<Link> linkMapper() {
        return (rs, rowNum) -> new Link(
                rs.getObject("id", UUID.class),
                rs.getObject("song_id", UUID.class),
                rs.getObject("arrangement_id", UUID.class),
                rs.getObject("proposal_id", UUID.class),
                MusicBrainzModels.ProposalStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("score"),
                rs.getString("match_method"),
                rs.getObject("artist_mbid", UUID.class),
                rs.getObject("release_group_mbid", UUID.class),
                rs.getObject("release_mbid", UUID.class),
                rs.getObject("recording_mbid", UUID.class),
                rs.getString("selected_fields"),
                rs.getString("reviewer"),
                rs.getString("rationale"),
                rs.getTimestamp("accepted_at").toInstant());
    }
}
