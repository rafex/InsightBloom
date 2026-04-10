package dev.rafex.insightbloom.ingest.bootstrap;

import dev.rafex.ether.http.jetty12.routing.JettyRouteRegistry;
import dev.rafex.ether.http.jetty12.JettyServerConfig;
import dev.rafex.ether.http.jetty12.JettyServerFactory;
import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.ingest.adapters.inbound.http.handlers.*;
import dev.rafex.insightbloom.ingest.adapters.outbound.moderationclient.HttpModerationClient;
import dev.rafex.insightbloom.ingest.adapters.outbound.queryclient.HttpQueryClient;
import dev.rafex.insightbloom.ingest.adapters.outbound.sqlite.*;
import dev.rafex.insightbloom.ingest.adapters.outbound.statsclient.HttpStatsClient;
import dev.rafex.insightbloom.ingest.adapters.outbound.usersclient.HttpUsersClient;
import dev.rafex.insightbloom.ingest.application.usecases.*;
import dev.rafex.insightbloom.ingest.domain.services.*;

public class IngestApplication {
    public static void main(final String[] args) throws Exception {
        final String dbPath = System.getenv().getOrDefault("DB_PATH", "ingest.db");
        final String usersUrl = System.getenv().getOrDefault("USERS_URL", "http://localhost:8081");
        final String moderationUrl = System.getenv().getOrDefault("MODERATION_URL", "http://localhost:8084");
        final String statsUrl = System.getenv().getOrDefault("STATS_URL", "http://localhost:8085");
        final String queryUrl = System.getenv().getOrDefault("QUERY_URL", "http://localhost:8083");

        final var db = new DatabaseManager(dbPath);
        db.initialize();

        final var messageRepo = new SqliteMessageRepository(db);
        final var usersPort = new HttpUsersClient(usersUrl);
        final var moderationPort = new HttpModerationClient(moderationUrl);
        final var statsPort = new HttpStatsClient(statsUrl);
        final var queryPort = new HttpQueryClient(queryUrl);
        final var normService = new WordNormalizationService();
        final var intentService = new IntentClassificationService();

        final var ingestUseCase = new IngestMessageUseCase(messageRepo, moderationPort, statsPort, queryPort, normService, intentService);
        final var getMessageUseCase = new GetMessageUseCase(messageRepo);

        final var ingestHandler = new IngestHandler(ingestUseCase, getMessageUseCase, usersPort);

        final var routes = new JettyRouteRegistry();
        routes.add("/api/v1", ingestHandler);

        final var codec = JacksonJsonCodec.defaultCodec();
        final var config = JettyServerConfig.fromEnv();
        final var runner = JettyServerFactory.create(config, routes, codec, null, java.util.List.of(), java.util.List.of());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { runner.stop(); } catch (final Exception e) { e.printStackTrace(); }
        }));

        runner.start();
        runner.await();
    }
}
