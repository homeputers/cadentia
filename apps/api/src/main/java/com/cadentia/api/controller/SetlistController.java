package com.cadentia.api.controller;

import com.cadentia.api.dto.GenerateSetlistRequest;
import com.cadentia.api.dto.SetlistProposalResponse;
import com.cadentia.reng.SetlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setlists")
public class SetlistController {

    private final SetlistService setlistService;

    public SetlistController(SetlistService setlistService) {
        this.setlistService = setlistService;
    }

    @PostMapping("/proposals")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SetlistProposalResponse generateProposal(@Valid @RequestBody GenerateSetlistRequest request) {
        return setlistService.generate(request);
    }
}
