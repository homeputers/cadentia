package com.cadentia.reng;

import com.cadentia.api.dto.GenerateSetlistRequest;
import com.cadentia.api.dto.SetlistProposalResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SetlistService {

    public SetlistProposalResponse generate(GenerateSetlistRequest request) {
        return new SetlistProposalResponse(
                "PENDING_CATALOG_IMPLEMENTATION",
                List.of(
                        "Recommendation Engine scaffold accepted the structured request.",
                        "No songs were selected because catalog retrieval is not implemented yet."));
    }
}
