package com.dip.orchestration;

import lombok.Data;

@Data
public class QueryRequest {
    private String serviceCode;
    private String query;
    private String userId;
}
