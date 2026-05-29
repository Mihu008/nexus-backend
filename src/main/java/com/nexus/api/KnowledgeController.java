package com.nexus.api;

import com.nexus.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeController {

    private final DocumentIngestionService documentIngestionService;

    /**
     * Endpoint to upload a document to the agent's long-term semantic memory.
     * Starts processing asynchronously and returns a 202 Accepted immediately.
     *
     * @param file   The document file (PDF, TXT, DOCX, etc.).
     * @param userId The UUID of the uploading user.
     * @return 202 Accepted with a message.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadKnowledge(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") UUID userId) {
        
        log.info("Received request to upload knowledge file '{}' for user ID {}", file.getOriginalFilename(), userId);
        
        if (file.isEmpty()) {
            log.warn("Upload rejected: File is empty");
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file is empty"));
        }
        
        // Trigger non-blocking async pipeline
        documentIngestionService.processDocumentAsync(file, userId);
        
        // Return 202 Accepted immediately
        return ResponseEntity.accepted().body(Map.of(
                "message", "Document upload accepted. Processing started asynchronously.",
                "fileName", file.getOriginalFilename()
        ));
    }
}
