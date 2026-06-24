package dev.rafex.insightbloom.moderation.bootstrap;

import dev.rafex.ether.http.jetty12.routing.JettyRouteRegistry;
import dev.rafex.ether.http.jetty12.JettyServerConfig;
import dev.rafex.ether.http.jetty12.JettyServerFactory;
import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.moderation.adapters.inbound.http.handlers.*;
import dev.rafex.insightbloom.moderation.adapters.outbound.queryclient.HttpQueryPort;
import dev.rafex.insightbloom.moderation.adapters.outbound.sqlite.*;
import dev.rafex.insightbloom.moderation.application.usecases.*;
import dev.rafex.insightbloom.moderation.domain.services.AutoCensureService;

public class ModerationApplication {
    public static void main(final String[] args) throws Exception {
        final String dbPath = System.getenv().getOrDefault("DB_PATH", "moderation.db");
        final String queryUrl = System.getenv().getOrDefault("QUERY_SERVICE_URL", "http://localhost:8083");

        final var db = new DatabaseManager(dbPath);
        db.initialize();

        final var blockedTermRepo = new SqliteBlockedTermRepository(db);
        final var wordRepo = new SqliteModerationWordRepository(db);
        final var messageRepo = new SqliteModerationMessageRepository(db);
        final var queryPort = new HttpQueryPort(queryUrl);
        final var autoCensureService = new AutoCensureService(blockedTermRepo);

        final var evaluateUseCase = new EvaluateCensureUseCase(autoCensureService, wordRepo, messageRepo);
        final var listUseCase = new ListModerationUseCase(wordRepo, messageRepo);
        final var censorWordUseCase = new CensorWordUseCase(wordRepo, queryPort);
        final var restoreWordUseCase = new RestoreWordUseCase(wordRepo, queryPort);
        final var editWordUseCase = new EditWordUseCase(wordRepo);
        final var deleteWordUseCase = new DeleteWordUseCase(wordRepo);
        final var censorMessageUseCase = new CensorMessageUseCase(messageRepo, queryPort);
        final var restoreMessageUseCase = new RestoreMessageUseCase(messageRepo, queryPort);
        final var editMessageUseCase = new EditMessageUseCase(messageRepo);
        final var deleteMessageUseCase = new DeleteMessageUseCase(messageRepo);

        final var conferenceModerationHandler = new ConferenceModerationHandler(
                listUseCase, censorWordUseCase, restoreWordUseCase, editWordUseCase, deleteWordUseCase,
                censorMessageUseCase, restoreMessageUseCase, editMessageUseCase, deleteMessageUseCase);
        final var evaluateHandler = new InternalEvaluateHandler(evaluateUseCase);

        final var routes = new JettyRouteRegistry();
        routes.add("/api/v1/conferences/*", conferenceModerationHandler);
        routes.add("/internal/evaluate/*", evaluateHandler);

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
