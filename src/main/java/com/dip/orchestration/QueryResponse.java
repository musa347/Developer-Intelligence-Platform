package com.dip.orchestration;

import com.dip.domain.DocumentChunk;
import com.dip.domain.QueryIntent;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class QueryResponse {
    private String answer;
    private List<DocumentChunk> chunks;
    private String confidence;
    private QueryIntent intent;
    private RetrievalStrategy strategy;
}
