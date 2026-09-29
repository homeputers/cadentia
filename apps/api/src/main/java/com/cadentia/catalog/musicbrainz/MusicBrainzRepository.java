package com.cadentia.catalog.musicbrainz;

import com.cadentia.catalog.musicbrainz.MusicBrainzModels.EntitySnapshot;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.Link;
import com.cadentia.catalog.musicbrainz.MusicBrainzModels.Proposal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MusicBrainzRepository {
    EntitySnapshot saveEntity(EntitySnapshot entity);

    Optional<EntitySnapshot> findEntity(MusicBrainzModels.EntityType type, UUID mbid);

    Proposal createProposal(Proposal proposal);

    Optional<Proposal> findProposal(UUID songId, UUID proposalId);

    List<Proposal> findProposals(UUID songId);

    Optional<Proposal> updateProposalStatus(UUID songId, UUID proposalId, MusicBrainzModels.ProposalStatus status);

    void updateLinkStatus(UUID songId, UUID proposalId, MusicBrainzModels.ProposalStatus status);

    Link createLink(Link link);

    Optional<Link> findAcceptedLink(UUID songId);
}
