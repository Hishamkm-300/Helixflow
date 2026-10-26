package com.helixflow.modules.clinical.dto;

import com.helixflow.modules.clinical.entity.ClinicalNote;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalNoteResponseDto {
    private Long id;
    private Long visitId;
    private String visitNumber;
    private Long authorId;
    private String authorName;
    private String rawText;
    private String noteType;
    private String status;
    private Instant createdAt;
    private Instant signedAt;
    private Long signedById;
    private String signedByName;

    public static ClinicalNoteResponseDto fromEntity(ClinicalNote note) {
        if (note == null) return null;
        return ClinicalNoteResponseDto.builder()
                .id(note.getId())
                .visitId(note.getVisit() != null ? note.getVisit().getId() : null)
                .visitNumber(note.getVisit() != null ? note.getVisit().getVisitNumber() : null)
                .authorId(note.getAuthor() != null ? note.getAuthor().getId() : null)
                .authorName(note.getAuthor() != null ? note.getAuthor().getFullName() : null)
                .rawText(note.getRawText())
                .noteType(note.getNoteType())
                .status(note.getStatus())
                .createdAt(note.getCreatedAt())
                .signedAt(note.getSignedAt())
                .signedById(note.getSignedBy() != null ? note.getSignedBy().getId() : null)
                .signedByName(note.getSignedBy() != null ? note.getSignedBy().getFullName() : null)
                .build();
    }
}
