package com.dip.controller;

import com.dip.domain.DocumentArtifact;
import com.dip.domain.DocumentType;
import com.dip.domain.UserRole;
import com.dip.security.RoleRequired;
import com.dip.service.DocumentIngestionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {
    
    private final DocumentIngestionService ingestionService;
    
    @PostMapping("/ingest")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<DocumentArtifact> ingestDocument(@RequestBody IngestRequest request) throws ExecutionException, InterruptedException {
        DocumentArtifact artifact = ingestionService.ingestDocument(
            request.getServiceCode(),
            request.getContent(),
            request.getDocumentType(),
            request.getVersion(),
            request.getSourceReference()
        );
        return ResponseEntity.ok(artifact);
    }
    
    @PostMapping("/upload")
    @RoleRequired(UserRole.ADMIN)
    public ResponseEntity<DocumentArtifact> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("serviceCode") String serviceCode,
            @RequestParam("documentType") DocumentType documentType,
            @RequestParam("version") String version) throws ExecutionException, InterruptedException, IOException {
        
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        String sourceReference = file.getOriginalFilename();
        
        DocumentArtifact artifact = ingestionService.ingestDocument(
            serviceCode,
            content,
            documentType,
            version,
            sourceReference
        );
        return ResponseEntity.ok(artifact);
    }
    
    @Data
    public static class IngestRequest {
        private String serviceCode;
        private String content;
        private DocumentType documentType;
        private String version;
        private String sourceReference;
    }
}
