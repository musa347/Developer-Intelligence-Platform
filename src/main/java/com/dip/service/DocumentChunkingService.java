package com.dip.service;

import com.dip.domain.ChunkType;
import com.dip.domain.DocumentChunk;
import com.dip.domain.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentChunkingService {

    private static final int MIN_CHUNK_SIZE = 120;
    private static final int TARGET_CHUNK_CHARS = 1000;
    private static final int MAX_CHUNK_CHARS = 1400;
    private static final int CHUNK_OVERLAP_CHARS = 180;

    public List<DocumentChunk> chunkDocument(String artifactId, DocumentType documentType, String content) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return chunks;
        }

        log.debug("Starting document chunking for type: {}, content length: {}", documentType, content.length());

        switch (documentType) {
            case MARKDOWN, README, MANUAL, RUNBOOK, ARCHITECTURE:
                chunks = chunkMarkdownDocument(artifactId, content);
                break;
            case OPENAPI, SWAGGER:
                chunks = chunkOpenAPIDocument(artifactId, content);
                break;
            default:
                chunks = chunkTextDocument(artifactId, content);
                break;
        }

        log.debug("Created {} chunks for document type: {}", chunks.size(), documentType);
        return chunks;
    }

    private List<DocumentChunk> chunkMarkdownDocument(String artifactId, String content) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");
        StringBuilder currentSection = new StringBuilder();
        String currentTitle = "Introduction";
        int sectionNumber = 0;

        for (String line : lines) {
            line = line.trim();

            if (isHeading(line)) {
                int newHeadingLevel = getHeadingLevel(line);

                if (currentSection.length() >= MIN_CHUNK_SIZE) {
                    sectionNumber = appendMarkdownChunks(
                            chunks,
                            artifactId,
                            currentTitle,
                            currentSection.toString().trim(),
                            sectionNumber
                    );
                }

                if (newHeadingLevel <= 2) {
                    currentTitle = extractTitle(line);
                    currentSection = new StringBuilder();
                } else {
                    currentSection.append("\n").append(line).append("\n");
                }
            } else if (!line.isEmpty()) {
                currentSection.append(line).append("\n");
            }
        }

        if (currentSection.length() >= MIN_CHUNK_SIZE) {
            appendMarkdownChunks(
                    chunks,
                    artifactId,
                    currentTitle,
                    currentSection.toString().trim(),
                    sectionNumber
            );
        }

        return chunks;
    }

    private List<DocumentChunk> chunkOpenAPIDocument(String artifactId, String content) {
        List<DocumentChunk> chunks = new ArrayList<>();
        
        // Split by major OpenAPI sections
        String[] sections = content.split("(?=(?i)paths:|components:|info:|servers:|security:)");
        
        for (int i = 0; i < sections.length; i++) {
            String section = sections[i].trim();
            if (!section.isEmpty()) {
                String title = extractOpenAPITitle(section);
                chunks.add(createChunk(
                        artifactId,
                        title,
                        section,
                        i,
                        ChunkType.ENDPOINT
                ));
            }
        }
        
        return chunks;
    }

    private List<DocumentChunk> chunkTextDocument(String artifactId, String content) {
        List<DocumentChunk> chunks = new ArrayList<>();
        
        // Split into paragraphs
        String[] paragraphs = content.split("\\n\\s*\\n");
        
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i].trim();
            if (!paragraph.isEmpty() && paragraph.length() >= MIN_CHUNK_SIZE) {
                chunks.add(createChunk(
                        artifactId,
                        "Paragraph " + (i + 1),
                        paragraph,
                        i,
                        ChunkType.CONCEPT
                ));
            }
        }
        
        return chunks;
    }

    private boolean isHeading(String line) {
        if (line == null || line.trim().isEmpty()) {
            return false;
        }

        // Check for markdown headings
        if (line.matches("^#{1,6}\\s+.+")) {
            return true;
        }

        // Check for numbered sections (e.g., "1.1 Section Title" or "SECTION 1: Title")
        if (line.matches("^(?:SECTION\\s+)?[0-9]+(?:\\.[0-9]+)*\\.?\\s+[A-Z].*")) {
            return true;
        }

        // ALL CAPS detection disabled - causes false splits on PDF-parsed text
        return false;
    }

    private int appendMarkdownChunks(List<DocumentChunk> chunks,
                                     String artifactId,
                                     String title,
                                     String content,
                                     int sectionNumber) {
        for (String chunk : splitOversizedContent(content)) {
            chunks.add(createChunk(
                    artifactId,
                    title,
                    title + "\n" + chunk,
                    sectionNumber,
                    ChunkType.CONCEPT
            ));
            sectionNumber++;
        }
        return sectionNumber;
    }

    private List<String> splitOversizedContent(String content) {
        List<String> chunks = new ArrayList<>();
        if (content.length() <= MAX_CHUNK_CHARS) {
            chunks.add(content);
            return chunks;
        }

        String remaining = content;
        while (!remaining.isBlank()) {
            if (remaining.length() <= MAX_CHUNK_CHARS) {
                chunks.add(remaining.trim());
                break;
            }

            int splitIndex = findSplitIndex(remaining);
            chunks.add(remaining.substring(0, splitIndex).trim());
            int nextStart = Math.max(0, splitIndex - CHUNK_OVERLAP_CHARS);
            if (nextStart >= remaining.length()) {
                break;
            }
            remaining = remaining.substring(nextStart).trim();
        }

        return chunks;
    }

    private int findSplitIndex(String content) {
        int preferred = Math.min(content.length(), TARGET_CHUNK_CHARS);
        int hardLimit = Math.min(content.length(), MAX_CHUNK_CHARS);

        int paragraphBreak = content.lastIndexOf("\n\n", hardLimit);
        if (paragraphBreak >= preferred / 2) {
            return paragraphBreak;
        }

        int lineBreak = content.lastIndexOf('\n', hardLimit);
        if (lineBreak >= preferred / 2) {
            return lineBreak;
        }

        int sentenceBreak = Math.max(content.lastIndexOf(". ", hardLimit), content.lastIndexOf(": ", hardLimit));
        if (sentenceBreak >= preferred / 2) {
            return sentenceBreak + 1;
        }

        int whitespaceBreak = content.lastIndexOf(' ', hardLimit);
        return whitespaceBreak > 0 ? whitespaceBreak : hardLimit;
    }

    private int getHeadingLevel(String line) {
        if (line.startsWith("####")) return 4;
        if (line.startsWith("###")) return 3;
        if (line.startsWith("##")) return 2;
        if (line.startsWith("#")) return 1;
        return 0;
    }

    private String extractTitle(String line) {
        if (line == null) {
            return "Untitled";
        }

        // Remove markdown headers
        line = line.replaceAll("^#+\\s*", "");

        // Remove section numbers (e.g., "1.1 " or "SECTION 1.1: ")
        line = line.replaceAll("^(?:SECTION\\s+)?[0-9]+(?:\\.[0-9]+)*\\.?\\s*:?\\s*", "");

        // Clean up any remaining special characters and extra spaces
        line = line.replaceAll("[^\\w\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // If we're left with nothing, return a default title
        if (line.isEmpty()) {
            return "Untitled Section";
        }

        // Capitalize first letter of each word for consistency
        String[] words = line.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.length() > 1 ? word.substring(1).toLowerCase() : "")
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    private String extractOpenAPITitle(String section) {
        String firstLine = section.lines()
                .filter(line -> !line.trim().isEmpty())
                .findFirst()
                .orElse("");
        
        if (firstLine.toLowerCase().startsWith("paths:")) return "API Paths";
        if (firstLine.toLowerCase().startsWith("components:")) return "Components";
        if (firstLine.toLowerCase().startsWith("info:")) return "API Information";
        if (firstLine.toLowerCase().startsWith("servers:")) return "Servers";
        if (firstLine.toLowerCase().startsWith("security:")) return "Security";
        
        return "API Section";
    }

    private DocumentChunk createChunk(String artifactId, String title, String content, 
                                     int sectionNumber, ChunkType chunkType) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setVectorId(UUID.randomUUID().toString());
        chunk.setSection(title);
        chunk.setContent(content); // Content will now be persisted in PostgreSQL
        chunk.setContentLength(content.length()); // Store length as metadata
        chunk.setChunkType(chunkType);
        chunk.setSectionNumber(sectionNumber);
        
        log.debug("Created chunk: {} (type: {}, length: {})", title, chunkType, content.length());
        return chunk;
    }
}
