package com.dip.service;

import com.dip.domain.ChunkType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import lombok.Data;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentParser {
    
    public List<ParsedChunk> parseText(String content) {
        List<ParsedChunk> chunks = new ArrayList<>();
        String[] paragraphs = content.split("\n\n+");
        for (String para : paragraphs) {
            para = para.trim();
            if (para.length() < 50) continue;
            String[] lines = para.split("\n", 2);
            String title = lines[0].substring(0, Math.min(60, lines[0].length()));
            chunks.add(new ParsedChunk(para, title, ChunkType.CONCEPT));
        }
        if (chunks.isEmpty() && content.trim().length() > 50) {
            chunks.add(new ParsedChunk(content.trim(), "Document", ChunkType.CONCEPT));
        }
        return chunks;
    }

    public List<ParsedChunk> parseMarkdown(String content) {
        List<ParsedChunk> chunks = new ArrayList<>();

        String[] sections = content.split("(?m)^#{1,3}\\s+|(?m)^={3,}$|(?m)^-{3,}$");
        
        for (String section : sections) {
            if (section.trim().isEmpty()) continue;
            
            String[] lines = section.trim().split("\n", 2);
            String title = lines[0].trim();
            String body = lines.length > 1 ? lines[1].trim() : "";
            

            if (title.matches("[=\\-\\s]+") || title.length() < 3) continue;

            if (body.length() > 50) {
                chunks.add(new ParsedChunk(
                    title + "\n\n" + body,
                    title,
                    ChunkType.CONCEPT
                ));
            }
        }

        if (chunks.isEmpty()) {
            String[] paragraphs = content.split("\n\n+");
            for (int i = 0; i < paragraphs.length; i++) {
                String para = paragraphs[i].trim();
                if (para.length() > 100) {
                    String[] paraLines = para.split("\n", 2);
                    String paraTitle = paraLines[0].substring(0, Math.min(50, paraLines[0].length()));
                    chunks.add(new ParsedChunk(para, paraTitle, ChunkType.CONCEPT));
                }
            }
        }
        
        return chunks;
    }
    
    public List<ParsedChunk> parseOpenAPI(String yamlContent) {
        List<ParsedChunk> chunks = new ArrayList<>();
        try {
            OpenAPI openAPI = new OpenAPIV3Parser().readContents(yamlContent).getOpenAPI();
            
            if (openAPI == null) return chunks;
            
            openAPI.getPaths().forEach((path, pathItem) -> {
                pathItem.readOperationsMap().forEach((method, operation) -> {
                    StringBuilder content = new StringBuilder();
                    content.append("Endpoint: ").append(method).append(" ").append(path).append("\n");
                    content.append("Summary: ").append(operation.getSummary()).append("\n");
                    
                    if (operation.getDescription() != null) {
                        content.append("Description: ").append(operation.getDescription()).append("\n");
                    }
                    
                    chunks.add(new ParsedChunk(
                        content.toString(),
                        method + " " + path,
                        ChunkType.ENDPOINT
                    ));
                });
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAPI document. Ensure the file is valid JSON/YAML OpenAPI spec.", e);
        }
        
        return chunks;
    }
    
    @Data
    public static class ParsedChunk {
        private final String content;
        private final String section;
        private final ChunkType chunkType;
    }
}
