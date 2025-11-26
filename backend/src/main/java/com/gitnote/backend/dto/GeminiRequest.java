package com.gitnote.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class GeminiRequest {
    private List<Content> contents;

    @Data
    public static class Content { private List<Part> parts; }

    @Data
    public static class Part {
        private String text;
        public Part(String text) { this.text = text; }
    }

    public static GeminiRequest create(String text) {
        Part part = new Part(text);
        Content content = new Content();
        content.setParts(List.of(part));
        GeminiRequest request = new GeminiRequest();
        request.setContents(List.of(content));
        return request;
    }
}