package br.com.montreal.ai.llmontreal.service.ollama;

import br.com.montreal.ai.llmontreal.config.KafkaTopicConfig;
import br.com.montreal.ai.llmontreal.dto.*;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaChatRequestDTO;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaChatResponseDTO;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaSummaryRequestDTO;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaSummaryResponseDTO;
import br.com.montreal.ai.llmontreal.entity.ChatMessage;
import br.com.montreal.ai.llmontreal.entity.Document;
import br.com.montreal.ai.llmontreal.entity.enums.Author;
import br.com.montreal.ai.llmontreal.exception.OllamaException;
import br.com.montreal.ai.llmontreal.repository.ChatSessionRepository;
import br.com.montreal.ai.llmontreal.repository.DocumentRepository;
import br.com.montreal.ai.llmontreal.service.ChatService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OllamaConsumerService {

    private final WebClient webClient;
    private final ChatService chatService;

    private final KafkaTemplate<String, KafkaChatResponseDTO> kafkaChatTemplate;
    private final KafkaTemplate<String, KafkaSummaryResponseDTO> kafkaSummaryTemplate;

    private final ChatSessionRepository chatSessionRepository;
    private final DocumentRepository documentRepository;

    @Value("${ollama.api.model}")
    private String ollamaModel;

    private static final String summarizePrompt = """
            CONTEXTO
            Você é um assistente de IA especialista em comunicação e processamento de linguagem.
            Sua principal habilidade é destilar informações complexas em resumos claros, concisos e fáceis de entender.
            
            TAREFA
            Analise o texto fornecido abaixo. Seu objetivo é criar um resumo que capture a essência e os pontos
            principais do conteúdo. O resumo deve ser significativamente mais curto que o original e escrito em
            linguagem simples.
            
            REGRAS OBRIGATÓRIAS
            1.  Simplificar: Use linguagem direta e evite jargões ou termos técnicos, a menos que sejam absolutamente 
            essenciais. Se um termo técnico for mantido, explique-o brevemente.
            2.  Resumir Foque nas ideias centrais, argumentos principais e conclusões. Omita detalhes secundários, 
            xemplos repetitivos e informações redundantes.
            3.  Manter a Originalidade (Fidelidade) O resumo DEVE ser fiel ao significado e à intenção do texto
            original. Não adicione opiniões pessoais, interpretações ou informações que não estejam presentes no texto.
            4.  Idioma A sua resposta (o resumo) deve ser gerada obrigatoriamente em Português do Brasil.
            
            TEXTO PARA SER RESUMIDO:
            """;

    private static final Logger log = LoggerFactory.getLogger(OllamaConsumerService.class);

    @KafkaListener(topics = KafkaTopicConfig.CHAT_REQUEST_TOPIC, groupId = "ollama-processors-group")
    public void sendChatMessage(KafkaChatRequestDTO kafkaChatRequestDTO) {
        String correlationId = kafkaChatRequestDTO.correlationId();
        Long sessionId = kafkaChatRequestDTO.chatSessionId();
        OllamaRequestDTO ollamaRequestDTO = kafkaChatRequestDTO.chatMessageRequest();

        log.info("Received Kafka request {} for session {}. Calling model {}",
                correlationId, sessionId, ollamaRequestDTO.model());

        try {
            OllamaApiResponseDTO ollamaResponse = webClient.post()
                    .uri("/api/generate")
                    .body(Mono.just(ollamaRequestDTO), OllamaApiResponseDTO.class)
                    .retrieve()
                    .bodyToMono(OllamaApiResponseDTO.class)
                    .timeout(Duration.ofMinutes(2))
                    .block();

            if (ollamaResponse == null) {
                throw new OllamaException("Ollama error: response is null");
            }

            log.info("Ollama success for {}. Saving model response.", correlationId);

            ChatMessage chatMessage = chatService
                    .addMessageToContext(sessionId, ollamaResponse.response(), Author.MODEL);

            ChatMessageResponseDTO chatMessageResponseDTO = ChatMessageResponseDTO.builder()
                    .documentId(chatMessage.getChatSession().getDocument().getId())
                    .chatSessionId(sessionId)
                    .author(chatMessage.getAuthor())
                    .createdAt(chatMessage.getCreatedAt())
                    .response(chatMessage.getMessage())
                    .build();

            KafkaChatResponseDTO kafkaChatResponseDTO = KafkaChatResponseDTO.builder()
                    .correlationId(correlationId)
                    .chatMessageResponseDTO(chatMessageResponseDTO)
                    .error(false)
                    .errorMessage(null)
                    .build();

            kafkaChatTemplate.send(KafkaTopicConfig.CHAT_RESPONSE_TOPIC, correlationId, kafkaChatResponseDTO);
        } catch (Exception e) {
            log.error("Ollama call failed for {}: {}", correlationId, e.getMessage());

            String errorMsg;
            if (e instanceof WebClientResponseException ex) {
                errorMsg = ex.getResponseBodyAsString();
            } else {
                errorMsg = e.getMessage();
            }

            KafkaChatResponseDTO kafkaChatResponseDTO = KafkaChatResponseDTO.builder()
                    .correlationId(correlationId)
                    .chatMessageResponseDTO(null)
                    .error(true)
                    .errorMessage(errorMsg)
                    .build();

            kafkaChatTemplate.send(KafkaTopicConfig.CHAT_RESPONSE_TOPIC, correlationId, kafkaChatResponseDTO);

        }
    }

    @KafkaListener(topics = KafkaTopicConfig.SUMMARY_REQUEST_TOPIC, groupId = "ollama-processors-group")
    public void summarizeDocumentContent(KafkaSummaryRequestDTO requestDTO) {
        String correlationId = requestDTO.correlationId();
        Long documentId = requestDTO.documentId();

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found by id: " + documentId));

        OllamaRequestDTO ollamaRequestDTO = OllamaRequestDTO.builder()
                .model(ollamaModel)
                .prompt(summarizePrompt + doc.getExtractedContent())
                .stream(false)
                .build();

        log.info("Received Kafka request {} for summarize document {} content. Calling model {}",
                correlationId, documentId, ollamaRequestDTO.model());

        try {
            OllamaApiResponseDTO ollamaResponse = webClient.post()
                    .uri("/api/generate")
                    .body(Mono.just(ollamaRequestDTO), OllamaApiResponseDTO.class)
                    .retrieve()
                    .bodyToMono(OllamaApiResponseDTO.class)
                    .timeout(Duration.ofMinutes(2))
                    .block();

            if (ollamaResponse == null) {
                throw new OllamaException("Ollama error: response is null");
            }

            log.info("Ollama success for document {}. Saving summary response.", correlationId);

            doc.setSummary(ollamaResponse.response());
            documentRepository.save(doc);

            KafkaSummaryResponseDTO responseDTO = KafkaSummaryResponseDTO.builder()
                    .correlationId(correlationId)
                    .documentId(doc.getId())
                    .summary(ollamaResponse.response())
                    .modelName(ollamaModel)
                    .error(false)
                    .errorMessage(null)
                    .build();

            kafkaSummaryTemplate.send(KafkaTopicConfig.SUMMARY_RESPONSE_TOPIC, correlationId, responseDTO);
        } catch (Exception e) {
            log.error("Ollama call failed for summarize {}: {}", correlationId, e.getMessage());

            String errorMsg;
            if (e instanceof WebClientResponseException ex) {
                errorMsg = ex.getResponseBodyAsString();
            } else {
                errorMsg = e.getMessage();
            }

            KafkaSummaryResponseDTO responseDTO = KafkaSummaryResponseDTO.builder()
                    .correlationId(correlationId)
                    .documentId(doc.getId())
                    .error(true)
                    .errorMessage(errorMsg)
                    .build();

            kafkaSummaryTemplate.send(KafkaTopicConfig.SUMMARY_RESPONSE_TOPIC, correlationId, responseDTO);
        }

    }
}
