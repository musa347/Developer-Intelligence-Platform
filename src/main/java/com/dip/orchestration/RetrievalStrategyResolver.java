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
            case ENDPOINT_DRILLDOWN -> 8;
            case TROUBLESHOOTING -> 10;
            case ARCHITECTURE -> 12;
            case DOCUMENTATION -> 15;
            case VERSION_DIFF -> 15;
        };
    }
}
