package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.entity.ChatMessage;
import br.com.montreal.ai.llmontreal.entity.ChatSession;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.Author;
import br.com.montreal.ai.llmontreal.repository.ChatSessionRepository;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final DocumentRepository documentRepository;

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public ChatSession getOrCreateSession(String model, Document doc) {
        ChatSession cs = doc.getChatSession();

        if (cs == null) {
            return createChatSession(model, doc);
        }

        return chatSessionRepository.findById(cs.getId())
                .orElseThrow(() -> new EntityNotFoundException("Chat Session not found by id: " + cs.getId()));
    }

    public ChatSession createChatSession(String model, Document doc) {
        log.info("Creating chat session with {}...", model);

        ChatSession cs = ChatSession.builder()
                .isActive(true)
                .document(doc)
                .build();

        doc.setChatSession(cs);

        Document updatedDoc = documentRepository.save(doc);
        return updatedDoc.getChatSession();
    }

    public ChatMessage addMessageToContext(Long chatSessionId, String content, Author author) {
        ChatSession cs = chatSessionRepository.findById(chatSessionId)
                .orElseThrow(() -> new EntityNotFoundException("Chat Session not found by id: " + chatSessionId));

        ChatMessage chatMessage = ChatMessage.builder()
                .author(author)
                .createdAt(LocalDateTime.now())
                .message(content)
                .build();

        cs.addMessage(chatMessage);
        chatSessionRepository.save(cs);

        return chatMessage;
    }

}

