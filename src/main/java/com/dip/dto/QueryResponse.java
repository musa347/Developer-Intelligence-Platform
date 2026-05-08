package com.dip.dto;

import lombok.Data;
import java.util.List;

@Data
public class QueryResponse {
    private String answer;
    private List<SourceReference> sources;
    private String confidence;
    private String serviceCode;
    
    @Data
    public static class SourceReference {
        private String documentType;
        private String version;
        private String section;
        private String excerpt;
    }
}
