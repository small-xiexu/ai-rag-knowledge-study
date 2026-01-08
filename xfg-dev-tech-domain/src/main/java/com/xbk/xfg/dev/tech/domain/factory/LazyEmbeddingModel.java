package com.xbk.xfg.dev.tech.domain.factory;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * 延迟获取的 EmbeddingModel，用于支持运行时切换
 */
public class LazyEmbeddingModel implements EmbeddingModel {

    private final DynamicEmbeddingFactory factory;

    public LazyEmbeddingModel(DynamicEmbeddingFactory factory) {
        this.factory = factory;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return factory.getActiveEmbeddingModel().call(request);
    }

    @Override
    public float[] embed(Document document) {
        return factory.getActiveEmbeddingModel().embed(document);
    }
}
