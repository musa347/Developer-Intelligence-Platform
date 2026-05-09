package com.dip.controller;


import com.dip.dto.IngestRequest;
import com.dip.domain.DocumentType;
import com.dip.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {
    
    private final DocumentIngestionService ingestionService;
    
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingestDocument(@RequestBody IngestRequest request) {
        String jobId = ingestionService.startIngestionJob(
            request.getServiceCode(),
            request.getContent(),
            request.getDocumentType(),
            request.getVersion(),
            request.getSourceReference()
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Document ingestion job created successfully");
        response.put("status", "PROCESSING");
        response.put("jobId", jobId);
        response.put("serviceCode", request.getServiceCode());
        response.put("documentType", request.getDocumentType());
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("serviceCode") String serviceCode,
            @RequestParam("documentType") DocumentType documentType,
            @RequestParam("version") String version) throws IOException {
        
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        String sourceReference = file.getOriginalFilename();

        String jobId = ingestionService.startIngestionJob(
            serviceCode,
            content,
            documentType,
            version,
            sourceReference
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Document upload accepted and ingestion job created");
        response.put("status", "PROCESSING");
        response.put("jobId", jobId);
        response.put("serviceCode", serviceCode);
        response.put("documentType", documentType);
        response.put("filename", sourceReference);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> getIngestionJobStatus(@PathVariable String jobId) {
        return ingestionService.getIngestionJobStatus(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("jobId", jobId);
                    response.put("status", "NOT_FOUND");
                    response.put("message", "Ingestion job not found");
                    return ResponseEntity.status(404).body(response);
                });
    }
}
