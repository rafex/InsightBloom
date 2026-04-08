package dev.rafex.insightbloom.users.bootstrap;

import dev.rafex.insightbloom.users.adapters.inbound.http.HttpServer;
import dev.rafex.insightbloom.users.adapters.inbound.http.handlers.*;
import dev.rafex.insightbloom.users.adapters.outbound.sqlite.*;
import dev.rafex.insightbloom.users.application.usecases.*;
import dev.rafex.insightbloom.users.domain.services.*;

public class UsersApplication {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"));
        String dbPath = System.getenv().getOrDefault("DB_PATH", "users.db");

        // Infrastructure
        DatabaseManager db = new DatabaseManager(dbPath);
        db.initialize();

        // Repositories
        var userRepo = new SqliteUserRepository(db);
        var guestRepo = new SqliteGuestUserRepository(db);
        var tokenRepo = new SqliteTokenRepository(db);
        var conferenceRepo = new SqliteConferenceRepository(db);

        // Domain services
        var tokenService = new TokenService(tokenRepo);
        var friendlyIdService = new FriendlyIdService(conferenceRepo);

        // Use cases
        var loginUseCase = new LoginUseCase(userRepo, tokenService);
        var createGuestUseCase = new CreateGuestUseCase(guestRepo, conferenceRepo, tokenService);
        var validateTokenUseCase = new ValidateTokenUseCase(tokenService, userRepo, guestRepo);
        var createConferenceUseCase = new CreateConferenceUseCase(conferenceRepo, friendlyIdService);
        var getConferenceUseCase = new GetConferenceUseCase(conferenceRepo);

        // Handlers
        var authHandler = new AuthHandler(loginUseCase, createGuestUseCase, validateTokenUseCase);
        var conferenceHandler = new ConferenceHandler(createConferenceUseCase, getConferenceUseCase, validateTokenUseCase);
        var healthHandler = new HealthHandler();

        // Server
        HttpServer server = new HttpServer(port, authHandler, conferenceHandler, healthHandler);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.stop(); } catch (Exception e) { e.printStackTrace(); }
        }));
        server.start();
        server.join();
    }
}
