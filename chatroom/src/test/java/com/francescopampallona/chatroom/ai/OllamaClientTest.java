package com.francescopampallona.chatroom.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OllamaClientTest {

    private HttpServer server;
    private ExecutorService executor;
    private AiProperties properties;
    private OllamaClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
        properties = new AiProperties();
        properties.setEnabled(true);
        properties.setModel("llama3.2:3b");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        properties.setRequestTimeoutSeconds(2);
        client = new OllamaClient(properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    @Test
    void sendsOneNonStreamingChatRequestWithSystemPromptAndTrimsReply() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        server.createContext("/api/chat", exchange -> {
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"message":{"role":"assistant","content":"  Ciao, sono Aria.  "},"done":true}
                    """);
        });

        String reply = client.generate("Sei Aria, agente AI.", List.of(
                new OllamaClient.ChatMessage("user", "Marco: Ciao!"),
                new OllamaClient.ChatMessage("assistant", "Bruno: Benvenuto.")
        ));

        assertEquals("Ciao, sono Aria.", reply);
        assertEquals("POST", method.get());
        assertEquals("application/json", contentType.get());
        JsonNode body = new JsonMapper().readTree(requestBody.get());
        assertEquals("llama3.2:3b", body.path("model").asText());
        assertFalse(body.path("stream").asBoolean());
        assertEquals(3, body.path("messages").size());
        assertEquals("system", body.path("messages").get(0).path("role").asText());
        assertEquals("Sei Aria, agente AI.", body.path("messages").get(0).path("content").asText());
        assertEquals("Marco: Ciao!", body.path("messages").get(1).path("content").asText());
        assertEquals("assistant", body.path("messages").get(2).path("role").asText());
        assertEquals(180, body.path("options").path("num_predict").asInt());
        assertEquals(4096, body.path("options").path("num_ctx").asInt());
    }

    @Test
    void removesLeadingAgentLabelAndDiscardsLaterSimulatedSpeakerBlocks() {
        replyWith("""
                [Agente AI Aria] Proviamo un laboratorio di idee.

                [Agente AI Mira] Sono interessata a questa
                """);

        assertEquals("Proviamo un laboratorio di idee.", client.generate("system", List.of()));
    }

    @Test
    void handlesColonSpeakerLabelsAndWindowsLineEndings() {
        replyWith("Bruno: Verifichiamo prima i dati.\r\n\r\nMira: Ascoltiamo tutti.\r\nAria: Ho una proposta.");

        assertEquals("Verifichiamo prima i dati.", client.generate("system", List.of()));
    }

    @Test
    void preservesInlineMentionsQuotedSpeakerLinesAndMarkdownBlockquotes() {
        String content = """
                Valuto la proposta di Aria: proviamo insieme; il riferimento [Agente AI Mira] resta nel testo.
                "Bruno: prima controlliamo i dati."
                '[Agente AI Mira] Ascoltiamo il gruppo.'
                > [Agente AI Aria] Una citazione, non un nuovo turno.
                > Mira: Anche questa e una citazione.
                Possiamo partire da un piccolo esperimento.
                """.strip();
        replyWith(content);

        assertEquals(content, client.generate("system", List.of()));
    }

    @Test
    void preservesUnknownSpeakerLabels() {
        String content = """
                [Agente AI Luca] Questa etichetta non identifica uno degli agenti della stanza.
                Luca: Un altro riferimento da conservare.
                [Partecipante] Anche questo resta invariato.
                """.strip();
        replyWith(content);

        assertEquals(content, client.generate("system", List.of()));
    }

    @Test
    void rejectsRepliesContainingOnlyAKnownSpeakerHeader() {
        List<String> headers = List.of("[Agente AI Aria]", "Bruno:", "[Agente AI Mira]\n");
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/api/chat", exchange ->
                respond(exchange, 200, new JsonMapper().writeValueAsString(
                        java.util.Map.of("message", java.util.Map.of("content", headers.get(requests.getAndIncrement()))))));

        for (int index = 0; index < headers.size(); index++) {
            OllamaClient.OllamaException error = assertThrows(OllamaClient.OllamaException.class,
                    () -> client.generate("system", List.of()));
            assertTrue(error.getMessage().contains("vuota"));
        }
    }

    @Test
    void lengthLimitedReplyDropsTrailingFragmentAfterTheLastCompleteSentence() {
        String complete = "Partiamo da una scena concreta. La domanda e: \"Quale emozione vuoi raccontare?\"";
        replyWithReason(complete + " Potremmo anche esplorare l’idea di utilizzare le emozioni r", "length");

        assertEquals(complete, client.generate("system", List.of()));
    }

    @Test
    void lengthLimitedReplyWithoutACompleteSentencePreservesItsText() {
        String content = "Un esercizio da 2.5 minuti potrebbe aiutare a esplorare le emozioni";
        replyWithReason(content, "length");

        assertEquals(content, client.generate("system", List.of()));
    }

    @Test
    void normallyStoppedReplyPreservesItsTrailingFragment() {
        String content = "Partiamo da una scena concreta. La domanda e: \"Quale emozione vuoi raccontare?\""
                + " Potremmo anche esplorare l’idea di utilizzare le emozioni r";
        replyWithReason(content, "stop");

        assertEquals(content, client.generate("system", List.of()));
    }

    @Test
    void successfulReadinessDetectsModelAndCachesRequests() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/api/tags", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, "{\"models\":[{\"name\":\"llama3.2:3b\"}]}");
        });

        assertTrue(client.readiness().ready());
        assertNull(client.readiness().error());
        assertEquals(1, requests.get());
    }

    @Test
    void readinessAcceptsImplicitLatestTag() {
        properties.setModel("llama3.2");
        server.createContext("/api/tags", exchange ->
                respond(exchange, 200, "{\"models\":[{\"model\":\"llama3.2:latest\"}]}"));

        assertTrue(client.readiness().ready());
    }

    @Test
    void missingModelIsReportedAndFailureIsCached() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/api/tags", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, "{\"models\":[{\"name\":\"another:latest\"}]}");
        });

        assertFalse(client.readiness().ready());
        assertTrue(client.readiness().error().contains("download"));
        assertEquals(1, requests.get());
    }

    @Test
    void missingModelGenerationReturnsFriendlyErrorWithoutBackendDetails() {
        server.createContext("/api/chat", exchange ->
                respond(exchange, 404, "{\"error\":\"private internal model path\"}"));

        OllamaClient.OllamaException error = assertThrows(OllamaClient.OllamaException.class,
                () -> client.generate("system", List.of()));

        assertTrue(error.getMessage().contains("modello"));
        assertFalse(error.getMessage().contains("private"));
    }

    @Test
    void httpFailureDoesNotBecomeAnAgentMessage() {
        server.createContext("/api/chat", exchange -> respond(exchange, 500, "internal details"));

        assertThrows(OllamaClient.OllamaException.class, () -> client.generate("system", List.of()));
    }

    @Test
    void malformedOrEmptyReplyDoesNotBecomeAnAgentMessage() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/api/chat", exchange -> {
            String response = requests.getAndIncrement() == 0
                    ? "invalid-json"
                    : "{\"message\":{\"content\":\"  \"}}";
            respond(exchange, 200, response);
        });

        assertThrows(OllamaClient.OllamaException.class, () -> client.generate("system", List.of()));
        OllamaClient.OllamaException error = assertThrows(OllamaClient.OllamaException.class,
                () -> client.generate("system", List.of()));
        assertTrue(error.getMessage().contains("vuota"));
    }

    @Test
    void generationHonorsConfiguredTimeout() {
        properties.setRequestTimeoutSeconds(1);
        server.createContext("/api/chat", exchange -> {
            try {
                Thread.sleep(2_000);
                respond(exchange, 200, "{\"message\":{\"content\":\"Late\"}}");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        assertTimeout(Duration.ofSeconds(3), () -> {
            OllamaClient.OllamaException error = assertThrows(OllamaClient.OllamaException.class,
                    () -> client.generate("system", List.of()));
            assertTrue(error.getMessage().contains("troppo tempo"));
        });
    }

    @Test
    void unavailableServiceDoesNotThrowDuringReadiness() {
        server.stop(0);

        OllamaClient.Readiness readiness = assertDoesNotThrow(client::readiness);

        assertFalse(readiness.ready());
        assertTrue(readiness.error().contains("raggiungibile"));
    }

    @Test
    void slowReadinessReturnsWithinItsShortTimeout() {
        server.createContext("/api/tags", exchange -> {
            try {
                Thread.sleep(3_000);
                respond(exchange, 200, "{\"models\":[]}");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });

        assertTimeout(Duration.ofSeconds(4), () -> assertFalse(client.readiness().ready()));
    }

    @Test
    void capsLongGeneratedTextBeforeReturningIt() {
        server.createContext("/api/chat", exchange ->
                respond(exchange, 200, new JsonMapper().writeValueAsString(
                        java.util.Map.of("message", java.util.Map.of("content", "a".repeat(4_000))))));

        assertEquals(2_000, client.generate("system", List.of()).length());
    }

    @Test
    void disabledAiMakesNoHttpRequests() {
        properties.setEnabled(false);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/api/tags", exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, "{\"models\":[]}");
        });

        assertFalse(client.readiness().ready());
        assertThrows(OllamaClient.OllamaException.class, () -> client.generate("system", List.of()));
        assertEquals(0, requests.get());
    }

    private void replyWith(String content) {
        server.createContext("/api/chat", exchange ->
                respond(exchange, 200, new JsonMapper().writeValueAsString(
                        java.util.Map.of("message", java.util.Map.of("content", content)))));
    }

    private void replyWithReason(String content, String doneReason) {
        server.createContext("/api/chat", exchange ->
                respond(exchange, 200, new JsonMapper().writeValueAsString(
                        java.util.Map.of("message", java.util.Map.of("content", content),
                                "done", true, "done_reason", doneReason))));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
