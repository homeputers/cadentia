package com.cadentia.reng;

public record ArrangementTransitionMetadata(
        Boolean compatibleWithAdjacentArrangements,
        Double parserConfidence,
        String evidenceRef) {}
