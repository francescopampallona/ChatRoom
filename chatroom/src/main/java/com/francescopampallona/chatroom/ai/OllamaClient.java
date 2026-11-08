package com.francescopampallona.chatroom.ai;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local Ollama HTTP API; it performs no network requests during application startup. */
@Component
public class OllamaClient {

    private static final Duration READINESS_TIMEOUT = Duration.ofSeconds(2);
    private static final long READINESS_CACHE_NANOS = Duration.ofSeconds(10).toNanos();
    private static final int MAX_RESPONSE_CHARS = 1_000_000;
    private static final int MAX_MESSAGE_CHARS = 2_000;
    private static final Pattern AGENT_SPEAKER_HEADER = Pattern.compile(
            "(?m)^\\h*(?:\\[Agente AI (?:Aria|Bruno|Mira)\\]|(?:Aria|Bruno|Mira):)\\h*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?][\"'»”’)]*(?=\\s|$)");

    private final AiProperties properties;
    private final HttpClient httpClient;
    private final JsonMapper jsonMapper = new JsonMapper();
    private volatile Readiness cachedReadiness;
    private volatile long readinessExpiresAt;

    public OllamaClient(AiProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(READINESS_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public record ChatMessage(String role, String content) { }

    public record Readiness(boolean ready, String error) { }

    public static class OllamaException extends RuntimeException {
        public OllamaException(String message) {
            super(message);
        }

        public OllamaException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Requests one complete assistant message; retries are controlled by the coordinator. */
    public String generate(String systemPrompt, List<ChatMessage> messages) {
        if (!properties.isEnabled()) {
            throw new OllamaException("Gli agenti AI sono disabilitati.");
        }
        List<ChatMessage> prompt = new ArrayList<>();
        prompt.add(new ChatMessage("system", systemPrompt));
        prompt.addAll(messages);

        try {
            String body = jsonMapper.writeValueAsString(Map.of(
                    "model", properties.getModel(),
                    "stream", false,
                    "messages", prompt,
                    "options", Map.of("temperature", 0.8, "num_predict", 180, "num_ctx", 4096)
            ));
            HttpRequest request = HttpRequest.newBuilder(endpoint("/api/chat"))
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new OllamaException("Il modello AI configurato non e disponibile. Scaricalo in Ollama.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OllamaException("Ollama non riesce a generare una risposta. Riprova tra poco.");
            }
            JsonNode result = readJson(response.body());
            if (result.hasNonNull("error")) {
                throw new OllamaException("Ollama ha segnalato un errore durante la generazione. Riprova tra poco.");
            }
            JsonNode contentNode = result.path("message").path("content");
            if (!contentNode.isTextual() || contentNode.asText().isBlank()) {
                throw new OllamaException("Il modello AI ha restituito una risposta vuota. Riprova.");
            }
            String content = singleAgentMessage(contentNode.asText());
            if ("length".equals(result.path("done_reason").asText())) {
                content = removeIncompleteTail(content);
            }
            if (content.isBlank()) {
                throw new OllamaException("Il modello AI ha restituito una risposta vuota. Riprova.");
            }
            return content.length() > MAX_MESSAGE_CHARS
                    ? content.substring(0, MAX_MESSAGE_CHARS).strip()
                    : content;
        } catch (HttpTimeoutException ex) {
            throw new OllamaException("Il modello AI sta impiegando troppo tempo. Riprova tra poco.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OllamaException("Generazione AI interrotta.", ex);
        } catch (IOException ex) {
            throw new OllamaException("Ollama non e raggiungibile. Verifica che il servizio locale sia avviato.", ex);
        } catch (OllamaException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new OllamaException("La risposta o la configurazione di Ollama non e valida.", ex);
        }
    }

    /** A short, cached probe; failures remain status information instead of breaking chat. */
    public Readiness readiness() {
        if (!properties.isEnabled()) {
            return new Readiness(false, "Gli agenti AI sono disabilitati.");
        }
        Readiness cached = cachedReadiness;
        if (cached != null && System.nanoTime() < readinessExpiresAt) {
            return cached;
        }
        synchronized (this) {
            if (cachedReadiness != null && System.nanoTime() < readinessExpiresAt) {
                return cachedReadiness;
            }
            Readiness result = probeReadiness();
            cachedReadiness = result;
            readinessExpiresAt = System.nanoTime() + READINESS_CACHE_NANOS;
            return result;
        }
    }

    private Readiness probeReadiness() {
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint("/api/tags"))
                    .timeout(READINESS_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new Readiness(false, "Ollama non e pronto. Riprova tra poco.");
            }
            JsonNode models = readJson(response.body()).path("models");
            if (!models.isArray()) {
                return new Readiness(false, "La risposta del servizio Ollama non e valida.");
            }
            String expected = normalizedModel(properties.getModel());
            for (JsonNode model : models) {
                if (expected.equals(normalizedModel(model.path("name").asText("")))
                        || expected.equals(normalizedModel(model.path("model").asText("")))) {
                    return new Readiness(true, null);
                }
            }
            return new Readiness(false, "Il modello AI configurato non e ancora disponibile. Attendi il download in Ollama.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Readiness(false, "Verifica del servizio AI interrotta.");
        } catch (IOException | RuntimeException ex) {
            return new Readiness(false, "Ollama non e raggiungibile. Verifica che il servizio locale sia avviato.");
        }
    }

    private URI endpoint(String path) {
        String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
        URI uri = URI.create(baseUrl + path);
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
            throw new IllegalArgumentException("Invalid Ollama base URL");
        }
        return uri;
    }

    private JsonNode readJson(String body) {
        if (body.length() > MAX_RESPONSE_CHARS) {
            throw new OllamaException("La risposta del servizio Ollama e troppo grande.");
        }
        return jsonMapper.readTree(body);
    }

    private String normalizedModel(String name) {
        return name.endsWith(":latest") ? name.substring(0, name.length() - ":latest".length()) : name;
    }

    private String singleAgentMessage(String generated) {
        String content = generated.strip();
        Matcher leading = AGENT_SPEAKER_HEADER.matcher(content);
        if (leading.lookingAt()) {
            content = content.substring(leading.end()).stripLeading();
        }
        // Only an unquoted speaker label at the start of a line opens another turn.
        // Inline mentions, quoted text and Markdown blockquotes remain ordinary content.
        Matcher nextSpeaker = AGENT_SPEAKER_HEADER.matcher(content);
        if (nextSpeaker.find()) {
            content = content.substring(0, nextSpeaker.start());
        }
        return content.strip();
    }

    private String removeIncompleteTail(String content) {
        Matcher sentences = SENTENCE_END.matcher(content);
        int lastCompleteEnd = -1;
        while (sentences.find()) {
            lastCompleteEnd = sentences.end();
        }
        // A token-limited response may still be useful without punctuation; never discard it.
        return lastCompleteEnd > 0 ? content.substring(0, lastCompleteEnd).stripTrailing() : content;
    }
}
