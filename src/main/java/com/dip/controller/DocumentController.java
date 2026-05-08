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
        // Start async ingestion and return immediately
        ingestionService.ingestDocumentAsync(
            request.getServiceCode(),
            request.getContent(),
            request.getDocumentType(),
            request.getVersion(),
            request.getSourceReference()
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Document ingestion started successfully. Processing in background...");
        response.put("status", "PROCESSING");
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

        ingestionService.ingestDocumentAsync(
            serviceCode,
            content,
            documentType,
            version,
            sourceReference
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Document upload started successfully. Processing in background...");
        response.put("status", "PROCESSING");
        response.put("serviceCode", serviceCode);
        response.put("documentType", documentType);
        response.put("filename", sourceReference);
        
        return ResponseEntity.ok(response);
    }
}
