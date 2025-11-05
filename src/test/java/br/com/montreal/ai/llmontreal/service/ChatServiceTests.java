package br.com.montreal.ai.llmontreal.service;

import br.com.montreal.ai.llmontreal.entity.ChatMessage;
import br.com.montreal.ai.llmontreal.entity.ChatSession;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.Author;
import br.com.montreal.ai.llmontreal.repository.ChatSessionRepository;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService Unit Tests")
public class ChatServiceTests {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private ChatService chatService;

    private Document mockDocument;
    private ChatSession mockSession;

    private static final String MODEL = "deepseek-r1:1.5b";
    private static final Long DOC_ID = 1L;
    private static final Long SESSION_ID = 1L;

    @BeforeEach
    void setUp() {
        mockDocument = new Document();
        mockDocument.setId(1L);

        mockSession = ChatSession.builder()
                .id(DOC_ID)
                .document(mockDocument)
                .isActive(true)
                .context(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("Should return existing ChatSession when document has one")
    void getOrCreateSession_WhenSessionExists_ShouldReturnExistingSession() {
        mockDocument.setChatSession(mockSession);
        when(chatSessionRepository.findById(mockSession.getId())).thenReturn(Optional.of(mockSession));

        ChatSession result = chatService.getOrCreateSession(MODEL, mockDocument);

        assertNotNull(result);
        assertEquals(mockSession.getId(), result.getId());
        assertEquals(mockDocument, result.getDocument());

        verify(chatSessionRepository, times(1)).findById(anyLong());
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should create ChatSession when no session exists")
    void getOrCreateSession_WhenSessionNotExists_ShouldCreateSession() {
        mockDocument.setChatSession(null);
        Document updatedDoc = Document.builder()
                .id(DOC_ID)
                .chatSession(mockSession)
                .build();
        when(documentRepository.save(any())).thenReturn(updatedDoc);

        ChatSession result = chatService.getOrCreateSession(MODEL, mockDocument);

        assertNotNull(result);
        assertEquals(mockSession, result);

        verify(chatSessionRepository, never()).findById(any());
        verify(documentRepository, times(1)).save(any(Document.class));
    }

    @Test
    @DisplayName("Should create ChatSession with correct attributes")
    void createChatSession_WhenCalled_ShouldCreateSessionCorrectly() {
        Document savedDocument = Document.builder()
                .id(DOC_ID)
                .chatSession(mockSession)
                .build();
        when(documentRepository.save(any())).thenReturn(savedDocument);

        ChatSession result = chatService.createChatSession(MODEL, mockDocument);

        assertNotNull(result);
        assertTrue(result.getIsActive());
        assertEquals(mockDocument, result.getDocument());

        verify(documentRepository, times(1)).save(any(Document.class));
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when ChatSession not found by id")
    void getOrCreateSession_WhenSessionNotFound_ShouldThrowException() {
        mockDocument.setChatSession(mockSession);
        when(chatSessionRepository.findById(mockSession.getId())).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> chatService.getOrCreateSession(MODEL, mockDocument)
        );

        assertEquals("Chat Session not found by id: " + SESSION_ID, exception.getMessage());

        verify(chatSessionRepository, times(1)).findById(anyLong());
    }

    @Test
    @DisplayName("Should add message to context successfully")
    void addMessageToContext_WhenValidData_ShouldAddMessage() {
        String messageContent = "Test message";
        Author author = Author.USER;
        when(chatSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(mockSession));
        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(mockSession);

        ChatMessage result = chatService.addMessageToContext(SESSION_ID, messageContent, author);

        assertNotNull(result);
        assertEquals(mockSession, result.getChatSession());

        verify(chatSessionRepository, times(1)).findById(SESSION_ID);
        verify(chatSessionRepository, times(1)).save(any(ChatSession.class));
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when adding message to non-existent session")
    void addMessageToContext_WhenSessionNotFound_ShouldThrowException() {
        String messageContent = "Test message";
        Author author = Author.USER;
        when(chatSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> chatService.addMessageToContext(SESSION_ID, messageContent, author)
        );

        assertEquals("Chat Session not found by id: " + SESSION_ID, exception.getMessage());
        verify(chatSessionRepository, times(1)).findById(SESSION_ID);
        verify(chatSessionRepository, never()).save(any());
    }

}
