package dev.rafex.insightbloom.moderation.bootstrap;
import dev.rafex.insightbloom.moderation.adapters.inbound.http.HttpServer;
import dev.rafex.insightbloom.moderation.adapters.inbound.http.handlers.*;
import dev.rafex.insightbloom.moderation.adapters.outbound.queryclient.HttpQueryPort;
import dev.rafex.insightbloom.moderation.adapters.outbound.sqlite.*;
import dev.rafex.insightbloom.moderation.application.usecases.*;
import dev.rafex.insightbloom.moderation.domain.services.AutoCensureService;
public class ModerationApplication {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT","8084"));
        String dbPath = System.getenv().getOrDefault("DB_PATH","moderation.db");
        String queryUrl = System.getenv().getOrDefault("QUERY_SERVICE_URL","http://localhost:8083");
        DatabaseManager db = new DatabaseManager(dbPath);
        db.initialize();
        var blockedTermRepo = new SqliteBlockedTermRepository(db);
        var wordRepo = new SqliteModerationWordRepository(db);
        var messageRepo = new SqliteModerationMessageRepository(db);
        var queryPort = new HttpQueryPort(queryUrl);
        var autoCensureService = new AutoCensureService(blockedTermRepo);
        var evaluateUseCase = new EvaluateCensureUseCase(autoCensureService);
        var listUseCase = new ListModerationUseCase(wordRepo, messageRepo);
        var censorWordUseCase = new CensorWordUseCase(wordRepo);
        var restoreWordUseCase = new RestoreWordUseCase(wordRepo);
        var editWordUseCase = new EditWordUseCase(wordRepo);
        var censorMessageUseCase = new CensorMessageUseCase(messageRepo, queryPort);
        var restoreMessageUseCase = new RestoreMessageUseCase(messageRepo, queryPort);
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
