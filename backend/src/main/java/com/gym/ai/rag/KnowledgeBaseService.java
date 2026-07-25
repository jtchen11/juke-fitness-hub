package com.gym.ai.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeBaseService {

    private EmbeddingStore<TextSegment> embeddingStore;
    private EmbeddingModel embeddingModel;

    @PostConstruct
    public void init() throws IOException {
        embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        embeddingStore = new InMemoryEmbeddingStore<>();

        try {
            Path docPath = new ClassPathResource("knowledge/fitness_knowledge.txt").getFile().toPath();
            // 读取文本内容（手动方式，兼容所有版本）
            String fullText = Files.readString(docPath, StandardCharsets.UTF_8);

            // 手动分割：按每 200 字符一段，重叠 20 字符
            int chunkSize = 200;
            int overlap = 20;
            List<TextSegment> segments = new ArrayList<>();
            for (int i = 0; i < fullText.length(); i += (chunkSize - overlap)) {
                int end = Math.min(fullText.length(), i + chunkSize);
                String chunk = fullText.substring(i, end);
                segments.add(TextSegment.from(chunk));
                if (end == fullText.length()) break;
            }

            // 存入向量库
            for (TextSegment segment : segments) {
                embeddingStore.add(embeddingModel.embed(segment).content(), segment);
            }
            System.out.println("✅ RAG 知识库加载成功，共 " + segments.size() + " 个片段");

        } catch (Exception e) {
            System.err.println("❌ RAG 知识库加载失败: " + e.getMessage());
        }
    }

    public String searchRelevant(String query) {
        if (embeddingStore == null || embeddingModel == null) {
            return "知识库暂未加载。";
        }
        try {
            var searchRequest = dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
    .queryEmbedding(embeddingModel.embed(query).content())
    .maxResults(2)
    .build();
            var searchResult = embeddingStore.search(searchRequest);
            var relevant = searchResult.matches();
            if (relevant.isEmpty()) {
                return "未找到相关知识。";
            }
            StringBuilder sb = new StringBuilder("【健身知识库检索结果】\n");
            for (var match : relevant) {
                sb.append("- ").append(match.embedded().text()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "知识库检索失败：" + e.getMessage();
        }
    }
}