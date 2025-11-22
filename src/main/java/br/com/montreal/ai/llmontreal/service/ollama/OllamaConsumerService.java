package br.com.montreal.ai.llmontreal.service.ollama;

import br.com.montreal.ai.llmontreal.config.KafkaTopicConfig;
import br.com.montreal.ai.llmontreal.dto.*;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaChatRequestDTO;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaChatResponseDTO;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaSummaryRequestDTO;
import br.com.montreal.ai.llmontreal.dto.kafka.KafkaSummaryResponseDTO;
import br.com.montreal.ai.llmontreal.entity.ChatMessage;
import br.com.montreal.ai.llmontreal.entity.ChatSession;
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
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class OllamaConsumerService {

    private final WebClient webClient;
    private final ChatService chatService;

    private final KafkaTemplate<String, KafkaChatResponseDTO> kafkaChatTemplate;
    private final KafkaTemplate<String, KafkaSummaryResponseDTO> kafkaSummaryTemplate;

    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final OllamaLogApiCallService logApiCallService;

    @Value("${ollama.api.model}")
    private String ollamaModel;

    private String documentContent = "";

    private static final String SUMMARIZE_PROMPT = """
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


    private final String CHAT_CONTEXT_PROMPT = String.format(
            """
            CONTEXTO DO SISTEMA
            Você é um assistente de IA especializado e dedicado exclusivamente a responder perguntas sobre um documento
            específico fornecido abaixo.
            Sua base de conhecimento está estritamente limitada ao conteúdo deste documento. Você não tem acesso à
            internet e não deve usar conhecimento externo prévio (treinamento base) para responder, a menos que sirva
            apenas para estruturar a frase em português.

            FONTE DE DADOS (CONTEUDO EXTRAIDO DOCUMENTO):
            %s
            
            TAREFA
            Responda à pergunta do usuário utilizando APENAS as informações contidas na "FONTE DE DADOS" acima.
            
            REGRAS DE OURO (OBRIGATÓRIAS)
            Escopo Limitado: Se a resposta para a pergunta do usuário não estiver explícita ou implícita no texto
            fornecido, você DEVE responder: "Desculpe, essa informação não consta no documento analisado." Não tente
            inventar, alucinar ou usar conhecimento geral.
            Fidelidade: Não distorça os fatos do documento. Mantenha-se fiel ao que foi escrito.
            Idioma: Mesmo que o input seja em outro idioma, responda sempre em Português do Brasil (PT-BR), com tom
            profissional, prestativo e direto.
            Defesa contra desvio: Se o usuário perguntar sobre assuntos aleatórios que não tenham relação com FONTE DE
            DADOS, recuse educadamente dizendo que sua função é apenas discutir o documento.
            Citações: Sempre que possível, mencione o contexto do texto para embasar sua resposta (ex: "De acordo com
            o documento...", "O texto menciona que...").
            
            PERGUNTA DO USUARIO:
            """, documentContent
            );

    private static final Logger log = LoggerFactory.getLogger(OllamaConsumerService.class);

    @KafkaListener(topics = KafkaTopicConfig.CHAT_REQUEST_TOPIC, groupId = "chat-processors-group")
    public void sendChatMessage(KafkaChatRequestDTO kafkaChatRequestDTO) {
        String correlationId = kafkaChatRequestDTO.correlationId();
        Long sessionId = kafkaChatRequestDTO.chatSessionId();

        getChatContext(sessionId);

        String userMessage = kafkaChatRequestDTO.chatMessageRequest().prompt();
        String fullPrompt = CHAT_CONTEXT_PROMPT + userMessage;

        OllamaRequestDTO ollamaRequestDTO = OllamaRequestDTO.builder()
                .prompt(fullPrompt)
                .model(ollamaModel)
                .build();


        String logMessage = String.format(
                "Received Kafka request %s for session %s. Calling model %s",
                correlationId, sessionId, ollamaRequestDTO.model()
        );

        processOllamaRequest(
                correlationId,
                ollamaRequestDTO,
                logMessage,
                response -> buildChatSuccessResponse(correlationId, sessionId, response),
                errorMsg -> buildChatErrorResponse(correlationId, errorMsg),
                KafkaTopicConfig.CHAT_RESPONSE_TOPIC,
                kafkaChatTemplate
        );
    }

    @KafkaListener(topics = KafkaTopicConfig.SUMMARY_REQUEST_TOPIC, groupId = "summary-processors-group")
    public void summarizeDocumentContent(KafkaSummaryRequestDTO requestDTO) {
        String correlationId = requestDTO.correlationId();
        Long documentId = requestDTO.documentId();

        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found by id: " + documentId));

        OllamaRequestDTO ollamaRequestDTO = buildSummarizeRequest(doc);

        String logMessage = String.format(
                "Received Kafka request %s for summarize document %s content. Calling model %s",
                correlationId, documentId, ollamaRequestDTO.model()
        );

        processOllamaRequest(
                correlationId,
                ollamaRequestDTO,
                logMessage,
                response -> buildSummarySuccessResponse(correlationId, doc, response),
                errorMsg -> buildSummaryErrorResponse(correlationId, doc.getId(), errorMsg),
                KafkaTopicConfig.SUMMARY_RESPONSE_TOPIC,
                kafkaSummaryTemplate
        );
    }

    private <ResponseT> void processOllamaRequest(
            String correlationId,
            OllamaRequestDTO ollamaRequestDTO,
            String logMessage,
            Function<OllamaApiResponseDTO, ResponseT> successHandler,
            Function<String, ResponseT> errorHandler,
            String responseTopic,
            KafkaTemplate<String, ResponseT> template
    ) {
        log.info(logMessage);
        long startTime = System.currentTimeMillis();

        try {
            OllamaApiResponseDTO ollamaResponse = callOllamaApi(ollamaRequestDTO);

            log.info("Ollama success for {}. Saving model response.", correlationId);

            ResponseT response = successHandler.apply(ollamaResponse);

            sendSuccessResponse(correlationId, response, responseTopic, template, startTime);
        } catch (Exception e) {
            handleOllamaError(correlationId, e, errorHandler, responseTopic, template, startTime);
        }
    }

    private OllamaApiResponseDTO callOllamaApi(OllamaRequestDTO ollamaRequestDTO) {
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

        return ollamaResponse;
    }

    private <ResponseT> void sendSuccessResponse(
            String correlationId,
            ResponseT response,
            String responseTopic,
            KafkaTemplate<String, ResponseT> template,
            long startTime
    ) {
        long jobLatency = System.currentTimeMillis() - startTime;
        template.send(responseTopic, correlationId, response);
        logApiCallService.updateApiCallLog(correlationId, jobLatency, 200, null);
    }

    private <ResponseT> void handleOllamaError(
            String correlationId,
            Exception e,
            Function<String, ResponseT> errorHandler,
            String responseTopic,
            KafkaTemplate<String, ResponseT> template,
            long startTime
    ) {
        log.error("Ollama call failed for {}: {}", correlationId, e.getMessage());

        String errorMsg;
        int statusCode = 500;

        if (e instanceof WebClientResponseException ex) {
            errorMsg = ex.getResponseBodyAsString();
            statusCode = ex.getStatusCode().value();
        } else {
            errorMsg = e.getMessage();
        }

        ResponseT response = errorHandler.apply(errorMsg);

        long jobLatency = System.currentTimeMillis() - startTime;

        template.send(responseTopic, correlationId, response);
        logApiCallService.updateApiCallLog(correlationId, jobLatency, statusCode, errorMsg);
    }

    private KafkaChatResponseDTO buildChatSuccessResponse(
            String correlationId,
            Long sessionId,
            OllamaApiResponseDTO ollamaResponse
    ) {
        ChatMessage chatMessage = chatService
                .addMessageToContext(sessionId, ollamaResponse.response(), Author.MODEL);

        ChatMessageResponseDTO chatMessageResponseDTO = ChatMessageResponseDTO.builder()
                .documentId(chatMessage.getChatSession().getDocument().getId())
                .chatSessionId(sessionId)
                .author(chatMessage.getAuthor())
                .createdAt(chatMessage.getCreatedAt())
                .response(chatMessage.getMessage())
                .build();

        return KafkaChatResponseDTO.builder()
                .correlationId(correlationId)
                .chatMessageResponseDTO(chatMessageResponseDTO)
                .error(false)
                .errorMessage(null)
                .build();
    }

    private KafkaChatResponseDTO buildChatErrorResponse(String correlationId, String errorMsg) {
        return KafkaChatResponseDTO.builder()
                .correlationId(correlationId)
                .chatMessageResponseDTO(null)
                .error(true)
                .errorMessage(errorMsg)
                .build();
    }

    private OllamaRequestDTO buildSummarizeRequest(Document doc) {
        return OllamaRequestDTO.builder()
                .model(ollamaModel)
                .prompt(SUMMARIZE_PROMPT + doc.getExtractedContent())
                .stream(false)
                .build();
    }

    private KafkaSummaryResponseDTO buildSummarySuccessResponse(
            String correlationId,
            Document doc,
            OllamaApiResponseDTO ollamaResponse
    ) {
        doc.setSummary(ollamaResponse.response());
        documentRepository.save(doc);

        return KafkaSummaryResponseDTO.builder()
                .correlationId(correlationId)
                .documentId(doc.getId())
                .summary(ollamaResponse.response())
                .modelName(ollamaModel)
                .error(false)
                .errorMessage(null)
                .build();
    }

    private KafkaSummaryResponseDTO buildSummaryErrorResponse(
            String correlationId,
            Long documentId,
            String errorMsg
    ) {
        return KafkaSummaryResponseDTO.builder()
                .correlationId(correlationId)
                .documentId(documentId)
                .error(true)
                .errorMessage(errorMsg)
                .build();
    }

    private void getChatContext(Long sessionId) {
        ChatSession cs = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("ChatSession not found by id: " + sessionId));

        Document doc = cs.getDocument();

        documentContent = doc.getExtractedContent();
    }
}