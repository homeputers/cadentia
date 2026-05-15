package com.cadentia.reng;

import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.SetlistProposalResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SetlistService {

    public SetlistProposalResponse generate(GenerateSetlistRequest request) {
        return new SetlistProposalResponse()
                .status("PENDING_CATALOG_IMPLEMENTATION")
                .auditMessages(List.of(
                        "Recommendation Engine scaffold accepted the structured request.",
                        "No songs were selected because catalog retrieval is not implemented yet."));
    }
}
