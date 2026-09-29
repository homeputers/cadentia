package com.cadentia.catalog.musicbrainz;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMusicBrainzRepository implements MusicBrainzRepository {
    private final Map<String, MusicBrainzModels.EntitySnapshot> entities = new ConcurrentHashMap<>();
    private final Map<UUID, MusicBrainzModels.Proposal> proposals = new ConcurrentHashMap<>();
    private final Map<UUID, MusicBrainzModels.Link> links = new ConcurrentHashMap<>();

    @Override
    public MusicBrainzModels.EntitySnapshot saveEntity(MusicBrainzModels.EntitySnapshot entity) {
        entities.put(key(entity.entityType(), entity.mbid()), entity);
        return entity;
    }

    @Override
    public Optional<MusicBrainzModels.EntitySnapshot> findEntity(MusicBrainzModels.EntityType type, UUID mbid) {
        return Optional.ofNullable(entities.get(key(type, mbid)));
    }

    @Override
    public MusicBrainzModels.Proposal createProposal(MusicBrainzModels.Proposal proposal) {
        proposals.put(proposal.id(), proposal);
        return proposal;
    }

    @Override
    public Optional<MusicBrainzModels.Proposal> findProposal(UUID songId, UUID proposalId) {
        return Optional.ofNullable(proposals.get(proposalId)).filter(proposal -> proposal.songId().equals(songId));
    }

    @Override
    public List<MusicBrainzModels.Proposal> findProposals(UUID songId) {
        return proposals.values().stream().filter(proposal -> proposal.songId().equals(songId)).toList();
    }

    @Override
    public Optional<MusicBrainzModels.Proposal> updateProposalStatus(
            UUID songId, UUID proposalId, MusicBrainzModels.ProposalStatus status) {
        return findProposal(songId, proposalId).map(existing -> {
            MusicBrainzModels.Proposal updated = new MusicBrainzModels.Proposal(
                    existing.id(), existing.songId(), existing.arrangementId(), existing.artistMbid(),
                    existing.releaseGroupMbid(), existing.releaseMbid(), existing.recordingMbid(), existing.score(),
                    existing.matchMethod(), status, existing.sourceUri(), existing.sourcePayload(), existing.createdBy(),
                    existing.createdAt(), java.time.Instant.now());
            proposals.put(proposalId, updated);
            return updated;
        });
    }

    @Override
    public MusicBrainzModels.Link createLink(MusicBrainzModels.Link link) {
        links.put(link.id(), link);
        return link;
    }

    @Override
    public void updateLinkStatus(UUID songId, UUID proposalId, MusicBrainzModels.ProposalStatus status) {
        links.values().stream()
                .filter(link -> link.songId().equals(songId) && link.proposalId().equals(proposalId))
                .findFirst()
                .ifPresent(link -> links.put(link.id(), new MusicBrainzModels.Link(
                        link.id(), link.songId(), link.arrangementId(), link.proposalId(), status, link.score(),
                        link.matchMethod(), link.artistMbid(), link.releaseGroupMbid(), link.releaseMbid(),
                        link.recordingMbid(), link.selectedFields(), link.reviewer(), link.rationale(),
                        link.acceptedAt())));
    }

    @Override
    public Optional<MusicBrainzModels.Link> findAcceptedLink(UUID songId) {
        return links.values().stream()
                .filter(link -> link.songId().equals(songId))
                .filter(link -> Optional.ofNullable(proposals.get(link.proposalId()))
                        .map(proposal -> proposal.status() == MusicBrainzModels.ProposalStatus.ACCEPTED)
                        .orElse(false))
                .findFirst();
    }

    private static String key(MusicBrainzModels.EntityType type, UUID mbid) {
        return type.name() + ":" + mbid;
    }
}
