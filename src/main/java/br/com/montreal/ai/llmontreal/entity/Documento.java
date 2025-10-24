package br.com.montreal.ai.llmontreal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "documentos")
@Getter
@Setter
@Builder
@NoArgsConstructor
public class Documento {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String status;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(nullable = false)
    private String filename;
    
    @Column(name = "filetype")
    private String filetype;
    
    @Column(columnDefinition = "TEXT")
    private String summary;

}
