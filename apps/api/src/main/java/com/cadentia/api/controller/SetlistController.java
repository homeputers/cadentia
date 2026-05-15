package com.cadentia.api.controller;

import com.cadentia.generated.api.SetlistsApi;
import com.cadentia.generated.model.GenerateSetlistRequest;
import com.cadentia.generated.model.SetlistProposalResponse;
import com.cadentia.reng.SetlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SetlistController implements SetlistsApi {

    private final SetlistService setlistService;

    public SetlistController(SetlistService setlistService) {
        this.setlistService = setlistService;
    }

    @Override
    public ResponseEntity<SetlistProposalResponse> generateSetlistProposal(GenerateSetlistRequest request) {
        return ResponseEntity.accepted().body(setlistService.generate(request));
    }
}
