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
        int charBudget = 8000;
        
        for (DocumentChunk chunk : chunks) {
            String content = chunk.getContent();
            if (context.length() + content.length() > charBudget) break;
            if (context.length() > 0) context.append("\n\n");
            context.append(content);
        }
        
        String contextStr = context.toString();
        
        try {
            return llmService.generateAnswer(serviceName, query, contextStr);
        } catch (Exception e) {
            log.error("LLM failed, returning raw context", e);
            return "Based on the documentation:\n\n" + contextStr;
        }
    }
}
