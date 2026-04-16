package com.dip.controller;

import com.dip.orchestration.QueryOrchestrator;
import com.dip.orchestration.QueryRequest;
import com.dip.orchestration.QueryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class QueryController {
    
    private final QueryOrchestrator queryOrchestrator;
    
    @PostMapping
    public ResponseEntity<QueryResponseDTO> query(@RequestBody QueryRequest request) throws ExecutionException, InterruptedException {
        QueryResponse response = queryOrchestrator.execute(request);
        
        QueryResponseDTO dto = new QueryResponseDTO();
        dto.setAnswer(response.getAnswer());
        dto.setConfidence(response.getConfidence());
        dto.setServiceCode(request.getServiceCode());
        dto.setSources(response.getChunks().stream()
                .map(chunk -> {
                    var source = new QueryResponseDTO.Source();
                    source.setDocumentType(chunk.getArtifact().getDocumentType().name());
                    source.setVersion(chunk.getArtifact().getVersion());
                    source.setSection(chunk.getSection());
                    source.setExcerpt(chunk.getContent().substring(0, Math.min(200, chunk.getContent().length())));
                    return source;
                })
                .collect(Collectors.toList()));
        
        return ResponseEntity.ok(dto);
    }
    
    @lombok.Data
    public static class QueryResponseDTO {
        private String answer;
        private java.util.List<Source> sources;
        private String confidence;
        private String serviceCode;
        
        @lombok.Data
        public static class Source {
            private String documentType;
            private String version;
            private String section;
            private String excerpt;
        }
    }
}
