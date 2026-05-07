package com.dip.orchestration;

import com.dip.domain.DocumentChunk;
import com.dip.service.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerComposer {
    
    private final LLMService llmService;
    
    public String compose(String query, List<DocumentChunk> chunks, String serviceName) {
        System.out.println("[ANSWER COMPOSER DEBUG] Number of chunks received: " + chunks.size());
        
        if (chunks.isEmpty()) {
            return "No relevant documentation found for this query.";
        }
        
        StringBuilder context = new StringBuilder();
        int charBudget = 8000;
        
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            String content = chunk.getContent();
            if (content == null || content.isBlank()) {
                System.out.println("[ANSWER COMPOSER DEBUG] Chunk " + i + " has null/blank content, skipping");
                continue;
            }
            System.out.println("[ANSWER COMPOSER DEBUG] Chunk " + i + " length: " + content.length() + 
                             ", section: " + chunk.getSection() + 
                             ", type: " + chunk.getChunkType());
            System.out.println("[ANSWER COMPOSER DEBUG] Content preview: " + 
                             (content.length() > 150 ? content.substring(0, 150) + "..." : content));
            
            if (context.length() + content.length() > charBudget) {
                System.out.println("[ANSWER COMPOSER DEBUG] Char budget reached, stopping at chunk " + i);
                break;
            }
            if (context.length() > 0) context.append("\n\n");
            context.append(content);
        }
        
        String contextStr = context.toString();
        System.out.println("[ANSWER COMPOSER DEBUG] Final context length: " + contextStr.length());
        
        try {
            return llmService.generateAnswer(serviceName, query, contextStr);
        } catch (Exception e) {
            log.error("LLM failed, returning raw context", e);
            return "Based on the documentation:\n\n" + contextStr;
        }
    }
}
