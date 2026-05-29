package com.nexus.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * Asynchronously ingests a document by saving it to temp storage, parsing it with Apache Tika,
     * chunking the content recursively, generating text embeddings, and storing them in pgvector.
     *
     * Runs on a Virtual Thread carrier pool (JDK 21) as defined in AsyncConfig.
     *
     * @param file   The uploaded multi-part file.
     * @param userId The user requesting document upload (enforces multi-tenancy).
     */
    @Async
    public void processDocumentAsync(MultipartFile file, UUID userId) {
        String originalFilename = file.getOriginalFilename();
        log.info("Parsing: Starting parsing of file '{}' for user {}", originalFilename, userId);
        
        Path tempFile = null;
        try {
            // Save upload stream to a temporary location
            tempFile = Files.createTempFile("nexus-upload-", "-" + originalFilename);
            file.transferTo(tempFile.toFile());

            // 1. Parsing
            DocumentParser parser = new ApacheTikaDocumentParser();
            Document document = FileSystemDocumentLoader.loadDocument(tempFile, parser);
            log.info("Parsing: Successfully parsed document '{}' using Apache Tika", originalFilename);

            // 2. Chunking
            log.info("Chunking: Splitting document '{}' into chunks", originalFilename);
            DocumentSplitter splitter = DocumentSplitters.recursive(800, 120);
            List<TextSegment> segments = splitter.split(document);
            log.info("Chunking: Document '{}' split into {} chunks", originalFilename, segments.size());

            // Enrich each chunk's metadata with user_id, document_name, and chunk_index.
            // The pgvector database trigger trg_sync_semantic_memory_fields will map these fields
            // to direct columns to support Row Level Security (RLS) policies.
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                segment.metadata().put("user_id", userId.toString());
                segment.metadata().put("document_name", originalFilename);
                segment.metadata().put("chunk_index", i);
            }

            // 3. Embedding
            log.info("Embedding: Generating embeddings for {} chunks using text-embedding-004", segments.size());
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            log.info("Embedding: Generated {} embeddings for file '{}'", embeddings.size(), originalFilename);

            // 4. Stored
            log.info("Stored: Saving {} embeddings to pgvector store (semantic_memory table)", embeddings.size());
            embeddingStore.addAll(embeddings, segments);
            log.info("Stored: Successfully stored {} embeddings for file '{}'", embeddings.size(), originalFilename);

        } catch (Exception e) {
            log.error("Error during document ingestion for file '{}': {}", originalFilename, e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    log.debug("Temporary file {} cleaned up successfully", tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file {}: {}", tempFile, e.getMessage());
                }
            }
        }
    }
}
