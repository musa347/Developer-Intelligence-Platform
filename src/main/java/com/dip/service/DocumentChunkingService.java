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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentChunkingService {
    
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(?:" +
                    "#+\\s*|" +                           // Markdown headings (#, ##, ###)
                    "(?:SECTION\\s+)?[0-9]+(?:\\.[0-9]+)*\\.?\\s*|" +  // Section numbers (1, 1.1, 1.1.1)
                    "(?=[A-Z][A-Z0-9\\s]*[A-Z0-9]$)" +    // ALL CAPS lines
                    ")",
            Pattern.MULTILINE
    );

    public List<DocumentChunk> chunkDocument(String artifactId, DocumentType documentType, String content) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return chunks;
        }

        log.debug("Starting document chunking for type: {}, content length: {}", documentType, content.length());

        switch (documentType) {
            case MARKDOWN, README:
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
        boolean inCodeBlock = false;
        int headingLevel = 0;

        for (String line : lines) {
            line = line.trim();

            // Skip code blocks
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                if (!inCodeBlock) {
                    currentSection.append("```\n");
                } else {
                    currentSection.append("```\n");
                }
                continue;
            }
            if (inCodeBlock) {
                currentSection.append(line).append("\n");
                continue;
            }

            if (isHeading(line)) {
                int newHeadingLevel = getHeadingLevel(line);
                
                // Save previous section if it has meaningful content
                if (currentSection.length() > 50) { // Require minimum content length
                    chunks.add(createChunk(
                            artifactId,
                            currentTitle,
                            currentSection.toString().trim(),
                            sectionNumber,
                            ChunkType.CONCEPT
                    ));
                    sectionNumber++;
                }
                
                // For subheadings, append to current section instead of creating new chunk
                if (newHeadingLevel <= 2) { // Only create new chunks for main sections (# and ##)
                    currentTitle = extractTitle(line);
                    currentSection = new StringBuilder();
                    headingLevel = newHeadingLevel;
                } else {
                    // Add subheading to current section content
                    currentSection.append("\n").append(line).append("\n");
                }
            } else if (!line.isEmpty()) {
                currentSection.append(line).append("\n");
            }
        }

        // Add the last section if it has meaningful content
        if (currentSection.length() > 50) {
            chunks.add(createChunk(
                    artifactId,
                    currentTitle,
                    currentSection.toString().trim(),
                    sectionNumber,
                    ChunkType.CONCEPT
            ));
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
            if (!paragraph.isEmpty() && paragraph.length() > 50) { // Skip very short paragraphs
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

        // Check for ALL CAPS headings (minimum 3 characters, not just numbers)
        if (line.matches("^[A-Z][A-Z0-9\\s-]{2,}[A-Z0-9]$") &&
                !line.matches(".*[a-z].*")) {
            return true;
        }

        return false;
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
        chunk.setContent(content);
        chunk.setChunkType(chunkType);
        chunk.setSectionNumber(sectionNumber);
        
        log.debug("Created chunk: {} (type: {}, length: {})", title, chunkType, content.length());
        return chunk;
    }
}
