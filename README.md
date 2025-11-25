# 📄 LLMontreal - Spring Boot API

> API desenvolvida durante a Sprint Surpresa do programa Acelera Maker, oferecido pela Montreal.

## 📋 Índice

- [Sobre o Projeto](#-sobre-o-projeto)
- [Arquitetura e Fluxo de Extração](#-arquitetura-e-fluxo-de-extração)
- [Tecnologias Utilizadas](#-tecnologias-utilizadas)
- [Funcionalidades](#-funcionalidades)
- [Estrutura do Projeto](#-estrutura-do-projeto)
- [Pré-requisitos](#-pré-requisitos)
- [Configuração do Ambiente](#-configuração-do-ambiente)
- [Executando a Aplicação](#-executando-a-aplicação)
- [Endpoints da API](#-endpoints-da-api)
- [Variáveis de Ambiente](#-variáveis-de-ambiente)

---

## 🎯 Sobre o Projeto

O **LLMontreal** é uma plataforma inteligente de processamento de documentos que combina extração de texto, OCR (Reconhecimento Óptico de Caracteres) e Inteligência Artificial para proporcionar uma experiência completa de análise documental.

### O que o sistema faz?

1. **Upload de Documentos**: Aceita diversos formatos (PDF, DOCX, imagens, arquivos ZIP)
2. **Extração Inteligente de Texto**: Utiliza Apache Tika para documentos estruturados e Tesseract OCR para imagens
3. **Geração de Resumos**: Cria resumos automáticos usando modelos de IA local (Ollama)
4. **Chatbot Contextual**: Permite fazer perguntas sobre o conteúdo do documento processado

---

## 🏗️ Arquitetura e Fluxo de Extração

### Diagrama do Fluxo de Processamento de Documentos

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          UPLOAD DE ARQUIVO                                  │
│                     (PDF, DOCX, TXT, IMG, ZIP)                              │
└────────────────────────────────┬────────────────────────────────────────────┘
                                 │
                                 ▼
                    ┌────────────────────────┐
                    │  DocumentController    │
                    │   POST /documents      │
                    └───────────┬────────────┘
                                │
                                ▼
                    ┌────────────────────────┐
                    │   DocumentService      │
                    │  - Valida arquivo      │
                    │  - Detecta tipo        │
                    └───────────┬────────────┘
                                │
                ┌───────────────┴───────────────┐
                │                               │
                ▼                               ▼
    ┌───────────────────┐         ┌──────────────────────┐
    │  Arquivo ZIP?     │         │  Arquivo Individual  │
    │       SIM         │         │        NÃO           │
    └─────────┬─────────┘         └──────────┬───────────┘
              │                               │
              ▼                               │
    ┌───────────────────┐                    │
    │ZipProcessingService│                   │
    │ - Extrai entries  │                    │
    │ - Cria múltiplos  │                    │
    │   documentos      │                    │
    └─────────┬─────────┘                    │
              │                               │
              └───────────────┬───────────────┘
                              │
                              ▼
                ┌─────────────────────────────┐
                │    Salva no PostgreSQL      │
                │  Status: PENDING            │
                └──────────────┬──────────────┘
                              │
                              ▼
                ┌─────────────────────────────┐
                │ DocumentExtractionService   │
                │  extractContentSync()       │
                │  Status: PROCESSING         │
                └──────────────┬──────────────┘
                              │
                ┌─────────────┴─────────────┐
                │                           │
                ▼                           ▼
    ┌───────────────────────┐   ┌──────────────────────┐
    │ TikaContentExtractor  │   │TesseractContentExtractor│
    │                       │   │                      │
    │ Suporta:              │   │ Suporta:             │
    │ • PDF                 │   │ • JPEG/JPG           │
    │ • DOCX                │   │ • PNG                │
    │ • DOC                 │   │ • TIFF/TIF           │
    │ • TXT                 │   │ • BMP                │
    │                       │   │ • GIF                │
    └───────────┬───────────┘   └──────────┬───────────┘
                │                           │
                └───────────┬───────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │  Texto Extraído           │
                │  Salvo no Banco           │
                │  Campo: extractedContent  │
                └────────────┬──────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │  OllamaProducerService    │
                │  Envia para Kafka         │
                │  Topic: summary-requests  │
                └────────────┬──────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │  Apache Kafka             │
                │  (Message Broker)         │
                └────────────┬──────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │  OllamaConsumerService    │
                │  @KafkaListener           │
                │  Consome mensagem         │
                └────────────┬──────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │  Ollama API               │
                │  (LLM Local)              │
                │  Modelo: deepseek-r1:1.5b │
                │  Gera resumo do texto     │
                └────────────┬──────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │  Resumo Gerado            │
                │  Enviado via Kafka        │
                │  Topic: summary-responses │
                └────────────┬──────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │  ResponseListenerService  │
                │  Processa resposta        │
                │  Salva resumo no banco    │
                │  Status: COMPLETED        │
                └────────────┬──────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │  DOCUMENTO PROCESSADO     │
                │  ✓ Texto extraído         │
                │  ✓ Resumo gerado          │
                │  ✓ Pronto para chat       │
                └───────────────────────────┘
```

### Fluxo de Chat com Documento

```
┌─────────────────────────────────────────────────────────────────┐
│                    USUÁRIO FAZ PERGUNTA                         │
│                 POST /chat/{documentId}                         │
└────────────────────────────┬────────────────────────────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │   ChatController          │
                │   Recebe pergunta         │
                └────────────┬──────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │   ChatService             │
                │   - Busca/cria sessão     │
                │   - Adiciona mensagem     │
                └────────────┬──────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │  OllamaProducerService    │
                │  Envia para Kafka         │
                │  Topic: chat-requests     │
                └────────────┬──────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │  OllamaConsumerService    │
                │  - Monta contexto         │
                │  - Inclui conteúdo doc    │
                │  - Envia para Ollama      │
                └────────────┬──────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │  Ollama API               │
                │  Responde baseado no doc  │
                └────────────┬──────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │  Resposta enviada via     │
                │  Kafka: chat-responses    │
                └────────────┬──────────────┘
                            │
                            ▼
                ┌───────────────────────────┐
                │  ResponseListenerService  │
                │  Salva no histórico       │
                │  Retorna ao usuário       │
                └───────────────────────────┘
```

### Componentes Principais

#### 1. **Extração de Conteúdo** (Strategy Pattern)
- **ContentExtractor** (Interface): Define o contrato para extratores
- **TikaContentExtractor**: Processa documentos estruturados (PDF, DOCX, TXT)
- **TesseractContentExtractor**: Realiza OCR em imagens

#### 2. **Processamento Assíncrono com Kafka**
- **Topics**:
  - `summary-requests`: Solicitações de resumo
  - `summary-responses`: Resumos gerados
  - `chat-requests`: Perguntas do usuário
  - `chat-responses`: Respostas do chatbot

#### 3. **Integração com IA**
- **Ollama**: Execução local de LLMs
- **Modelo**: deepseek-r1:1.5b
- **WebClient**: Comunicação reativa com API Ollama

---

## 🚀 Tecnologias Utilizadas

### Backend
- **Spring Boot 3.5.7** - Framework principal
- **Java 21** - Linguagem de programação
- **Spring Data JPA** - Persistência de dados
- **Spring WebFlux** - Comunicação reativa
- **Spring Kafka** - Mensageria assíncrona

### Banco de Dados
- **PostgreSQL 16** - Banco de dados relacional
- **Flyway** - Versionamento de schema

### Extração de Texto
- **Apache Tika 3.2.3** - Extração de texto de documentos
- **Tesseract OCR (Tess4j 5.16.0)** - Reconhecimento óptico de caracteres

### Inteligência Artificial
- **Ollama** - Execução local de modelos LLM
- **Spring AI 1.0.3** - Integração com modelos de IA

### Infraestrutura
- **Docker & Docker Compose** - Containerização
- **Apache Kafka 4.1.0** - Message broker
- **Maven** - Gerenciamento de dependências

### Outras Bibliotecas
- **Lombok** - Redução de boilerplate
- **H2 Database** - Testes
- **JUnit & Mockito** - Testes unitários

---

## ✨ Funcionalidades

### 📤 Upload e Processamento
- ✅ Upload de arquivos individuais (até 25MB)
- ✅ Upload de arquivos ZIP com múltiplos documentos
- ✅ Validação de tipos de arquivo suportados
- ✅ Extração automática de texto
- ✅ OCR para imagens e PDFs escaneados
- ✅ Geração automática de resumos

### 💬 Chatbot Inteligente
- ✅ Perguntas e respostas baseadas no documento
- ✅ Contexto mantido por sessão
- ✅ Histórico de conversas
- ✅ Respostas em português do Brasil

### 📊 Gerenciamento
- ✅ Listagem paginada de documentos
- ✅ Filtro por status (PENDING, PROCESSING, COMPLETED, FAILED)
- ✅ Visualização de conteúdo extraído
- ✅ Visualização de resumo
- ✅ Regeneração de resumos

### 🔍 Monitoramento
- ✅ Logs detalhados de operações
- ✅ Registro de chamadas à API Ollama
- ✅ Métricas de latência
- ✅ Rastreamento por correlation ID

---

## 📁 Estrutura do Projeto

```
llmontreal-backend/
├── src/
│   ├── main/
│   │   ├── java/br/com/montreal/ai/llmontreal/
│   │   │   ├── config/              # Configurações (Kafka, WebClient, Async)
│   │   │   ├── controller/          # Endpoints REST
│   │   │   ├── dto/                 # Data Transfer Objects
│   │   │   ├── entity/              # Entidades JPA
│   │   │   ├── event/               # Eventos de aplicação
│   │   │   ├── exception/           # Exceções customizadas
│   │   │   ├── listener/            # Event listeners
│   │   │   ├── repository/          # Repositórios JPA
│   │   │   ├── service/             # Lógica de negócio
│   │   │   │   ├── extraction/      # Extratores de conteúdo
│   │   │   │   └── ollama/          # Serviços Ollama/Kafka
│   │   │   └── LlmontrealApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── db/migration/        # Scripts Flyway
│   │       └── tessdata/            # Dados de treinamento Tesseract
│   └── test/                        # Testes unitários e integração
├── docker-compose.yml               # Infraestrutura (Postgres, Kafka)
├── Dockerfile                       # Build da aplicação
├── pom.xml                          # Dependências Maven
└── README.md
```

---

## 📋 Pré-requisitos

Certifique-se de ter os seguintes softwares instalados:

- ☑️ **JDK 21 ou superior** - [Download](https://www.oracle.com/java/technologies/downloads/)
- ☑️ **Maven 3.9+** - [Download](https://maven.apache.org/download.cgi)
- ☑️ **Docker & Docker Compose** - [Download](https://www.docker.com/products/docker-desktop/)
- ☑️ **Ollama** - [Download](https://ollama.ai/)

---

## ⚙️ Configuração do Ambiente

### 1. Clone o Repositório

```bash
git clone git@github.com:ro77en/llmontreal-backend.git
cd llmontreal-backend
```

### 2. Configure as Variáveis de Ambiente

Crie um arquivo `.env` na raiz do projeto:

```env
# Variáveis do Banco de Dados (PostgreSQL)
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
DB_HOST_PORT=5432

# Variáveis do Kafka
KAFKA_CLUSTER_ID=AbCdeFGhIjKlMnOPqRs1w
KAFKA_HOST_PORT=9095
```

**Gerar UUID para Kafka:**
```bash
docker run --rm apache/kafka:4.1.0 /opt/kafka/bin/kafka-storage.sh random-uuid
```

### 3. Configure o Ollama

#### Instalar e Iniciar o Ollama
```bash
# Baixe e instale o Ollama
# Visite: https://ollama.ai/

# Verifique se está rodando
curl http://localhost:11434
```

#### Baixar o Modelo de IA
```bash
ollama pull deepseek-r1:1.5b
```

### 4. Adicione os Dados do Tesseract (Opcional)

Os arquivos de treinamento do Tesseract já estão incluídos em `src/main/resources/tessdata/`:
- `por.traineddata` (Português)
- `eng.traineddata` (Inglês)

Se necessário, baixe outros idiomas de: [tessdata](https://github.com/tesseract-ocr/tessdata)

---

## 🚀 Executando a Aplicação

### Passo 1: Iniciar a Infraestrutura

Inicie o PostgreSQL e o Kafka usando Docker Compose:

```bash
docker compose up -d
```

**Verificar containers:**
```bash
docker ps
```

Você deve ver:
- `llmontreal_db` (PostgreSQL) - Porta 5432
- `llmontreal_kafka` (Kafka) - Porta 9095

### Passo 2: Executar a API Spring Boot

#### Opção A: Via IDE (Recomendado)
1. Abra o projeto na sua IDE (IntelliJ IDEA, Eclipse, VS Code)
2. Localize a classe `LlmontrealApplication.java`
3. Execute como aplicação Java

#### Opção B: Via Maven
```bash
mvn spring-boot:run
```

#### Opção C: Via JAR
```bash
mvn clean package -DskipTests
java -jar target/llmontreal-0.0.1-SNAPSHOT.jar
```

### Passo 3: Verificar Inicialização

Após a inicialização bem-sucedida, você verá:

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.5.7)

...
INFO - Started LlmontrealApplication in X.XXX seconds
```

**Serviços disponíveis:**
- 🌐 API: `http://localhost:8080`
- 🗄️ PostgreSQL: `localhost:5432`
- 📨 Kafka: `localhost:9095`
- 🤖 Ollama: `http://localhost:11434`

---

## 📡 Endpoints da API

### Documentos

#### Upload de Arquivo
```http
POST /documents
Content-Type: multipart/form-data

file: <arquivo>
```

**Tipos suportados:**
- PDF (`.pdf`)
- Word (`.docx`, `.doc`)
- Texto (`.txt`)
- Imagens (`.jpg`, `.jpeg`, `.png`)
- ZIP (`.zip`)

**Resposta:**
```json
{
  "id": 1,
  "fileName": "documento.pdf",
  "fileType": "application/pdf",
  "status": "COMPLETED",
  "summary": "Resumo do documento...",
  "createdAt": "2025-11-25T10:30:00",
  "updatedAt": "2025-11-25T10:31:00"
}
```

#### Listar Documentos
```http
GET /documents?page=0&size=10&status=COMPLETED
```

#### Obter Conteúdo Extraído
```http
GET /documents/{id}/content
```

#### Obter Resumo
```http
GET /documents/{id}/summary
```

#### Regenerar Resumo
```http
POST /documents/{id}/summary/regenerate
```

### Chat

#### Enviar Mensagem
```http
POST /chat/{documentId}
Content-Type: application/json

{
  "prompt": "Qual é o assunto principal deste documento?",
  "model": "deepseek-r1:1.5b"
}
```

**Resposta:**
```json
{
  "documentId": 1,
  "chatSessionId": 1,
  "author": "MODEL",
  "response": "O documento trata sobre...",
  "createdAt": "2025-11-25T10:35:00"
}
```

### Status dos Documentos

- `PENDING`: Aguardando processamento
- `PROCESSING`: Em processamento
- `COMPLETED`: Processado com sucesso
- `FAILED`: Falha no processamento

---

## 🔧 Variáveis de Ambiente

### application.properties

```properties
# Banco de Dados
spring.datasource.url=jdbc:postgresql://localhost:5432/llmontreal
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}

# Kafka
spring.kafka.bootstrap-servers=localhost:${KAFKA_HOST_PORT}

# Ollama
ollama.api.base-url=http://localhost:11434
ollama.api.model=deepseek-r1:1.5b

# Tesseract
tesseract.language=por+eng
tesseract.page-segmentation-mode=3
tesseract.oem-mode=3

# Upload
spring.servlet.multipart.max-file-size=25MB
spring.servlet.multipart.max-request-size=25MB
```

---

## 🧪 Executando Testes

```bash
# Todos os testes
mvn test

# Testes específicos
mvn test -Dtest=DocumentServiceTests

# Com cobertura
mvn clean test jacoco:report
```

---

## 🐳 Docker

### Build da Imagem
```bash
docker build -t llmontreal-backend .
```

### Executar Container
```bash
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  llmontreal-backend
```

---

## 📝 Logs e Monitoramento

Os logs da aplicação incluem:
- ✅ Requisições HTTP (via `ApiLoggingFilter`)
- ✅ Operações de extração de texto
- ✅ Chamadas à API Ollama
- ✅ Mensagens Kafka
- ✅ Erros e exceções

**Visualizar logs:**
```bash
# Logs da aplicação
tail -f logs/spring.log

# Logs do Docker
docker compose logs -f
```

---

## 🤝 Contribuindo

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/AmazingFeature`)
3. Commit suas mudanças (`git commit -m 'Add some AmazingFeature'`)
4. Push para a branch (`git push origin feature/AmazingFeature`)
5. Abra um Pull Request

---

## 📄 Licença

Este projeto foi desenvolvido durante o programa Acelera Maker da Montreal.
