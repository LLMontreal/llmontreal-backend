package br.com.montreal.ai.llmontreal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class LlmontrealApplication {

	public static void main(String[] args) {
		SpringApplication.run(LlmontrealApplication.class, args);
	}

}
