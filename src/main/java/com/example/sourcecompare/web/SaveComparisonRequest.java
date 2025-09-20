package com.example.sourcecompare.web;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SaveComparisonRequest {
    private String name;
    private String user;
    private JsonNode result;
}

