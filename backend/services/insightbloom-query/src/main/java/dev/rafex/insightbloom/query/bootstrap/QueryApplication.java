package dev.rafex.insightbloom.query.bootstrap;

import dev.rafex.ether.http.jetty12.routing.JettyRouteRegistry;
import dev.rafex.ether.http.jetty12.JettyServerConfig;
import dev.rafex.ether.http.jetty12.JettyServerFactory;
import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.query.adapters.inbound.http.handlers.*;
import dev.rafex.insightbloom.query.adapters.outbound.sqlite.*;
import dev.rafex.insightbloom.query.application.usecases.*;

public class QueryApplication {
    public static void main(final String[] args) throws Exception {
        final String dbPath = System.getenv().getOrDefault("DB_PATH", "query.db");

        final var db = new DatabaseManager(dbPath);
        db.initialize();

        final var cloudRepo = new SqliteCloudWordRepository(db);
        final var timelineRepo = new SqliteWordTimelineRepository(db);

        final var getCloudUseCase = new GetCloudUseCase(cloudRepo);
        final var getTimelineUseCase = new GetTimelineUseCase(timelineRepo);
        final var updateUseCase = new UpdateCloudUseCase(cloudRepo, timelineRepo);
        final var setVisibilityUseCase = new SetVisibilityUseCase(cloudRepo);
        final var setMessageVisibilityUseCase = new SetMessageVisibilityUseCase(timelineRepo);

        final var conferenceQueryHandler = new ConferenceQueryHandler(getCloudUseCase, getTimelineUseCase);
        final var updateHandler = new UpdateHandler(updateUseCase);
        final var visibilityHandler = new VisibilityHandler(setVisibilityUseCase);
        final var messageVisibilityHandler = new MessageVisibilityHandler(setMessageVisibilityUseCase);

        final var routes = new JettyRouteRegistry();
        routes.add("/api/v1/conferences/*", conferenceQueryHandler);
        routes.add("/internal/update/*", updateHandler);
        routes.add("/internal/visibility/*", visibilityHandler);
        routes.add("/internal/message-visibility/*", messageVisibilityHandler);

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
