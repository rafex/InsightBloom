package dev.rafex.insightbloom.ingest.bootstrap;
import dev.rafex.insightbloom.ingest.adapters.inbound.http.HttpServer;
import dev.rafex.insightbloom.ingest.adapters.inbound.http.handlers.*;
import dev.rafex.insightbloom.ingest.adapters.outbound.sqlite.*;
import dev.rafex.insightbloom.ingest.adapters.outbound.moderationclient.HttpModerationClient;
import dev.rafex.insightbloom.ingest.adapters.outbound.statsclient.HttpStatsClient;
import dev.rafex.insightbloom.ingest.adapters.outbound.queryclient.HttpQueryClient;
import dev.rafex.insightbloom.ingest.adapters.outbound.usersclient.HttpUsersClient;
import dev.rafex.insightbloom.ingest.application.usecases.*;
import dev.rafex.insightbloom.ingest.domain.services.*;
public class IngestApplication {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT","8082"));
        String dbPath = System.getenv().getOrDefault("DB_PATH","ingest.db");
        String usersUrl = System.getenv().getOrDefault("USERS_URL","http://localhost:8081");
        String moderationUrl = System.getenv().getOrDefault("MODERATION_URL","http://localhost:8084");
        String statsUrl = System.getenv().getOrDefault("STATS_URL","http://localhost:8085");
        String queryUrl = System.getenv().getOrDefault("QUERY_URL","http://localhost:8083");
        var db = new DatabaseManager(dbPath);
        db.initialize();
        var messageRepo = new SqliteMessageRepository(db);
        var usersPort = new HttpUsersClient(usersUrl);
        var moderationPort = new HttpModerationClient(moderationUrl);
        var statsPort = new HttpStatsClient(statsUrl);
        var queryPort = new HttpQueryClient(queryUrl);
        var normService = new WordNormalizationService();
        var intentService = new IntentClassificationService();
        var ingestUseCase = new IngestMessageUseCase(messageRepo, moderationPort, statsPort, queryPort, normService, intentService);
        var getMessageUseCase = new GetMessageUseCase(messageRepo);
        var ingestHandler = new IngestHandler(ingestUseCase, getMessageUseCase, usersPort);
        var healthHandler = new HealthHandler();
        var server = new HttpServer(port, ingestHandler, healthHandler);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { server.stop(); } catch (Exception e) { e.printStackTrace(); } }));
        server.start();
        server.join();
    }
}
