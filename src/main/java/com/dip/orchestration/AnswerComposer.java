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
        if (chunks.isEmpty()) {
            return "No relevant documentation found for this query.";
        }
        
        StringBuilder context = new StringBuilder();
        int maxChunks = Math.min(chunks.size(), 2);
        
        for (int i = 0; i < maxChunks; i++) {
            DocumentChunk chunk = chunks.get(i);
            context.append(chunk.getContent());
            if (i < maxChunks - 1) context.append("\n\n");
        }
        
        String contextStr = context.toString();
        if (contextStr.length() > 2000) {
            contextStr = contextStr.substring(0, 2000) + "...";
        }
        
        try {
            return llmService.generateAnswer(serviceName, query, contextStr);
        } catch (Exception e) {
            log.error("LLM failed, returning raw context", e);
            return "Based on the documentation:\n\n" + contextStr;
        }
    }
}
