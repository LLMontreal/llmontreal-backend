# LLMontreal - Spring Boot API

API desenvolvida durante a Sprint Surpresa do programa Acelera Maker, oferecido pela Montreal.

## Sobre o Projeto
Este repositório contém a API Spring Boot do projeto LLMontreal, uma plataforma de upload de arquivos com assistência de Inteligência Artificial.
O sistema permite que os usuários façam upload de seus documentos, que terão o texto extraído e será gerado um resumo a partir deste conteúdo.
Com o documento processado, é disponibilizado um chatbot para que o usuário tire dúvidas relacionadas ao documento.

## Tecnologias Utilizadas
- Spring Boot
- PostgreSQL
- Flyway
- Docker
- Tesseract
- Apache Tika
- Ollama
- Spring Boot WebFlux
- Kafka

## Rodando o Projeto
Siga essas instruções para rodar a API do LLMontreal em seu ambiente local:

### Pré-requisitos
Para rodar o projeto, certifique que os seguintes softwares estão instalados:
- **JDK 21 ou superior**
- **Maven**: gerenciador de dependências
- **Docker**: serviços de infraestrutura
- **Ollama**: execução local de modelos de IA (LLM)

### Configuração do Ambiente
1. **Clone o Repositório**
```
git clone git@github.com:ro77en/llmontreal-backend.git
cd llmontreal-backend
```
2. Arquivos de Variáveis de Ambiente
Na raíz do projeto, crie um arquivo `.env` com as variáveis descritas no `.env.example`
Você pode copiar e colar este conteúdo no seu `.env`:
```
# Variáveis do Banco de Dados (PostgreSQL)
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
DB_HOST_PORT=5432

# Variáveis do Kafka
# Gere um ID único para o cluster (ex: com 'uuidgen')
KAFKA_CLUSTER_ID=AbCdeFGhIjKlMnOPqRs1w
KAFKA_HOST_PORT=9095
```
Você pode gerar o UUID para o cluster Kafka usando docker, com o comando:
```
docker run --rm apache/kafka:4.1.0 /opt/kafka/bin/kafka-storage.sh random-uuid
```
3. Confiruação do Ollama
- **Instal e execute o Ollama** em sua máquina
- **Baixe um modelo de IA** que será usado para resumos e chat com o comando:
```
ollama pull deepseek-r1:1.5b
```
Certifique-se que o Ollama está em execução antes de iniciar a aplicação Spring Boot.
Por padrão, o Ollama estará escutando na porta `11434`, você pode acessar `localhost:11434` para verificar.

## Executando a Aplicação
Siga estes passos na ordem para iniciar os componentes do sistema.

1. Iniciar a Infraestrutura (Postgres & Kafka)
Na raíz do projeto, execute o Docker Compose para iniciar os containers do Postgres e do Kafka:
```
docker compose up -d
```
- O PostgreSQL estará acessível em `localhost:5432` (ou a porta definida em `DB_HOST_PORT`).
- O Kafka estará acessível em `localhost:9095` (ou a porta definida em `KAFKA_HOST_PORT`).

2. Executar a API Spring Boot
Você pode executar a aplicação de duas maneiras:
- Pela IDE **(Recomendado)**: após abrir o projeto em sua IDE de preferência, localize e execute a classe principal `LlmontrealApplication.java`
- Pelo Maven: usando o seguinte comando:
```
mvn spring-boot:run
```

## Verificação
Após a inicialização bem sucedida, a aplicação estará disponível:
- API: `localhost:8080`
- Banco de Dados: `localhost:5432`
- Kafka: `localhost:9095`
- Ollama: `localhost:11434`









