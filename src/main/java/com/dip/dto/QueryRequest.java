package com.dip.dto;

import lombok.Data;

@Data
public class QueryRequest {
    private String serviceCode;
    private String query;
    private String userId;
}
