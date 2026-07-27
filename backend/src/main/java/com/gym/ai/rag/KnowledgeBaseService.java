package com.gym.ai.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private EmbeddingStore<TextSegment> embeddingStore;
    private EmbeddingModel embeddingModel;

    @PostConstruct
    public void init() {
        embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        embeddingStore = new InMemoryEmbeddingStore<>();
        try {
            File dir = new ClassPathResource("knowledge").getFile();
            File[] files = dir.listFiles((d, name) -> name.endsWith(".txt"));
            if (files == null || files.length == 0) {
                log.warn("knowledge 目录未找到 .txt 文件");
                return;
            }
            log.info("RAG 知识库开始加载，共发现 {} 个 .txt 文件", files.length);
            StringBuilder allText = new StringBuilder();
            for (File f : files) {
                try {
                    byte[] raw = Files.readAllBytes(f.toPath());
                    if (raw.length >= 3 && raw[0] == (byte)0xEF && raw[1] == (byte)0xBB && raw[2] == (byte)0xBF) {
                        byte[] tmp = new byte[raw.length - 3];
                        System.arraycopy(raw, 3, tmp, 0, raw.length - 3);
                        raw = tmp;
                        log.info("  文件 {} 已去除 BOM 头", f.getName());
                    }
                    String text = new String(raw, StandardCharsets.UTF_8);
                    allText.append("\n[").append(f.getName()).append("]\n").append(text).append("\n");
                    log.info("  已加载: {} ({} bytes)", f.getName(), raw.length);
                } catch (Exception ex) {
                    log.warn("  跳过文件 {}: {}", f.getName(), ex.getMessage());
                }
            }
            String fullText = allText.toString();
            int chunkSize = 300;
            int overlap = 30;
            List<TextSegment> segments = new ArrayList<>();
            for (int i = 0; i < fullText.length(); i += (chunkSize - overlap)) {
                int end = Math.min(fullText.length(), i + chunkSize);
                if (end - i < 10) break;
                String chunk = fullText.substring(i, end);
                segments.add(TextSegment.from(chunk));
                if (end == fullText.length()) break;
            }
            for (TextSegment segment : segments) {
                embeddingStore.add(embeddingModel.embed(segment).content(), segment);
            }
            log.info("RAG 知识库加载完成: {} 个文件, {} 个片段", files.length, segments.size());
        } catch (Exception e) {
            log.error("RAG 知识库加载失败", e);
        }
    }

    public String searchRelevant(String query) {
        if (embeddingStore == null || embeddingModel == null) {
            return "知识库暂未加载。";
        }
        try {
            var searchRequest = dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                    .queryEmbedding(embeddingModel.embed(query).content())
                    .maxResults(3)
                    .minScore(0.5)
                    .build();
            var searchResult = embeddingStore.search(searchRequest);
            var relevant = searchResult.matches();
            log.info("RAG 检索: query=\"{}\", 结果数={}", query, relevant.size());
            if (relevant.isEmpty()) {
                return "未找到相关知识。";
            }
            StringBuilder sb = new StringBuilder("【健身知识库检索结果】\n");
            for (var match : relevant) {
                sb.append("- ").append(match.embedded().text()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("RAG 检索失败", e);
            return "知识库检索失败：" + e.getMessage();
        }
    }
}