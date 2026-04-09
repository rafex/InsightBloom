package dev.rafex.insightbloom.moderation.bootstrap;
import dev.rafex.insightbloom.moderation.adapters.inbound.http.HttpServer;
import dev.rafex.insightbloom.moderation.adapters.inbound.http.handlers.*;
import dev.rafex.insightbloom.moderation.adapters.outbound.sqlite.*;
import dev.rafex.insightbloom.moderation.application.usecases.*;
import dev.rafex.insightbloom.moderation.domain.services.AutoCensureService;
public class ModerationApplication {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT","8084"));
        String dbPath = System.getenv().getOrDefault("DB_PATH","moderation.db");
        DatabaseManager db = new DatabaseManager(dbPath);
        db.initialize();
        var blockedTermRepo = new SqliteBlockedTermRepository(db);
        var wordRepo = new SqliteModerationWordRepository(db);
        var messageRepo = new SqliteModerationMessageRepository(db);
        var autoCensureService = new AutoCensureService(blockedTermRepo);
        String queryUrl = System.getenv().getOrDefault("QUERY_URL", "http://localhost:8083");
        var queryPort = new dev.rafex.insightbloom.moderation.adapters.outbound.queryclient.HttpQueryPort(queryUrl);
        var evaluateUseCase = new EvaluateCensureUseCase(autoCensureService, wordRepo, messageRepo);
        var listUseCase = new ListModerationUseCase(wordRepo, messageRepo);
        var censorWordUseCase = new CensorWordUseCase(wordRepo, queryPort);
        var restoreWordUseCase = new RestoreWordUseCase(wordRepo, queryPort);
        var editWordUseCase = new EditWordUseCase(wordRepo);
        var censorMessageUseCase = new CensorMessageUseCase(messageRepo);
        var restoreMessageUseCase = new RestoreMessageUseCase(messageRepo);
        var editMessageUseCase = new EditMessageUseCase(messageRepo);
        var wordHandler = new ModerationWordHandler(listUseCase, censorWordUseCase, restoreWordUseCase, editWordUseCase);
        var messageHandler = new ModerationMessageHandler(listUseCase, censorMessageUseCase, restoreMessageUseCase, editMessageUseCase);
        var evaluateHandler = new InternalEvaluateHandler(evaluateUseCase);
        var healthHandler = new HealthHandler();
        HttpServer server = new HttpServer(port, wordHandler, messageHandler, evaluateHandler, healthHandler);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { server.stop(); } catch (Exception e) { e.printStackTrace(); } }));
        server.start();
        server.join();
    }
}
