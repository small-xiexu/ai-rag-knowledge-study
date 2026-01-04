package com.xbk.xfg.dev.tech;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author xiexu
 */
@SpringBootApplication(exclude = {
    org.springframework.ai.autoconfigure.vertexai.gemini.VertexAiGeminiAutoConfiguration.class
})
@Configurable
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

}
