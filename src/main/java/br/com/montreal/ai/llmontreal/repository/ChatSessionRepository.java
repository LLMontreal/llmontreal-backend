package br.com.montreal.ai.llmontreal.repository;

import br.com.montreal.ai.llmontreal.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
}
