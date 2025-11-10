package br.com.montreal.ai.llmontreal.entity;

import br.com.montreal.ai.llmontreal.entity.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Document {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "file_name", nullable = false)
    private String fileName;
    
    @Column(name = "file_type", nullable = false)
    private String fileType;
    
    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "file_data", nullable = false)
    private byte[] fileData;

    @OneToOne(cascade = CascadeType.ALL)
    private ChatSession chatSession;

    @Column(name = "extracted_content", columnDefinition = "TEXT")
    private String extractedContent;
}
