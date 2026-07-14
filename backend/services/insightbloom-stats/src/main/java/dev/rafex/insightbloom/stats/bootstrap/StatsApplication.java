package dev.rafex.insightbloom.stats.bootstrap;

import dev.rafex.ether.http.jetty12.routing.JettyRouteRegistry;
import dev.rafex.ether.http.jetty12.JettyServerConfig;
import dev.rafex.ether.http.jetty12.JettyServerFactory;
import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.common.http.VersionHandler;
import dev.rafex.insightbloom.stats.adapters.inbound.http.handlers.*;
import dev.rafex.insightbloom.stats.adapters.outbound.sqlite.*;
import dev.rafex.insightbloom.stats.adapters.outbound.usersclient.HttpUsersClient;
import dev.rafex.insightbloom.stats.application.usecases.*;
import dev.rafex.insightbloom.stats.domain.services.RelevanceService;

public class StatsApplication {
    public static void main(final String[] args) throws Exception {
        final String dbPath = System.getenv().getOrDefault("DB_PATH", "stats.db");
        final String usersUrl = System.getenv().getOrDefault("USERS_URL", "http://insightbloom-users:8081");

        final var db = new DatabaseManager(dbPath);
        db.initialize();

        final var statsRepo = new SqliteWordStatsRepository(db);
        final var relevanceService = new RelevanceService();
        final var recalcUseCase = new RecalcStatsUseCase(statsRepo, relevanceService);
        final var getStatsUseCase = new GetStatsUseCase(statsRepo);
        final var usersPort = new HttpUsersClient(usersUrl);

        final var recalcHandler = new RecalcHandler(recalcUseCase);
        final var statsHandler = new StatsHandler(getStatsUseCase, usersPort);

        final var routes = new JettyRouteRegistry();
        routes.add("/internal/recalc/*", recalcHandler);
        routes.add("/api/v1/conferences/*", statsHandler);
        routes.add("/version", new VersionHandler("insightbloom-stats"));

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
