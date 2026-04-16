package com.dip.orchestration;

import com.dip.domain.QueryIntent;
import org.springframework.stereotype.Component;

@Component
public class RetrievalStrategyResolver {
    
    public RetrievalStrategy resolve(QueryIntent intent) {
        return switch (intent) {
            case ENDPOINT_LOOKUP -> RetrievalStrategy.ENDPOINT_DRILLDOWN;
            case ERROR_EXPLANATION -> RetrievalStrategy.TROUBLESHOOTING;
            case ARCHITECTURAL_OVERVIEW -> RetrievalStrategy.ARCHITECTURE;
            case INTEGRATION_GUIDANCE, CONCEPTUAL_EXPLANATION, CHANGE_HISTORY, LOG_ANALYSIS -> RetrievalStrategy.DOCUMENTATION;
        };
    }
    
    public int getTopK(RetrievalStrategy strategy) {
        return switch (strategy) {
            case ENDPOINT_DRILLDOWN -> 5;
            case TROUBLESHOOTING -> 8;
            case ARCHITECTURE -> 10;
            case DOCUMENTATION -> 10;
            case VERSION_DIFF -> 15;
        };
    }
}
