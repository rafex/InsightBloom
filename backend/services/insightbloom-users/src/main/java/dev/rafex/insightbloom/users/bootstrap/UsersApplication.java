package dev.rafex.insightbloom.users.bootstrap;

import dev.rafex.ether.http.jetty12.routing.JettyRouteRegistry;
import dev.rafex.ether.http.jetty12.JettyServerConfig;
import dev.rafex.ether.http.jetty12.JettyServerFactory;
import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.users.adapters.inbound.http.handlers.*;
import dev.rafex.insightbloom.users.adapters.outbound.sqlite.*;
import dev.rafex.insightbloom.users.application.usecases.*;
import dev.rafex.insightbloom.users.domain.services.*;

public class UsersApplication {
    public static void main(final String[] args) throws Exception {
        final String dbPath = System.getenv().getOrDefault("DB_PATH", "users.db");

        // Infrastructure
        final var db = new DatabaseManager(dbPath);
        db.initialize();

        // Repositories
        final var userRepo = new SqliteUserRepository(db);
        final var guestRepo = new SqliteGuestUserRepository(db);
        final var tokenRepo = new SqliteTokenRepository(db);
        final var conferenceRepo = new SqliteConferenceRepository(db);

        // Domain services
        final var tokenService = new TokenService(tokenRepo);
        final var friendlyIdService = new FriendlyIdService(conferenceRepo);

        // Use cases
        final var loginUseCase = new LoginUseCase(userRepo, tokenService);
        final var createGuestUseCase = new CreateGuestUseCase(guestRepo, conferenceRepo, tokenService);
        final var validateTokenUseCase = new ValidateTokenUseCase(tokenService, userRepo, guestRepo);
        final var createConferenceUseCase = new CreateConferenceUseCase(conferenceRepo, friendlyIdService);
        final var getConferenceUseCase = new GetConferenceUseCase(conferenceRepo);

        // Handlers
        final var authHandler = new AuthHandler(loginUseCase, createGuestUseCase, validateTokenUseCase);
        final var conferenceHandler = new ConferenceHandler(createConferenceUseCase, getConferenceUseCase, validateTokenUseCase);

        // Route registry
        final var routes = new JettyRouteRegistry();
        routes.add("/api/v1/auth/*", authHandler);
        routes.add("/api/v1/conferences/*", conferenceHandler);

        // Server
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
