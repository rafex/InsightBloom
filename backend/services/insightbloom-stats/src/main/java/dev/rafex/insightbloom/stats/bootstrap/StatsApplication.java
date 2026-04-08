package dev.rafex.insightbloom.stats.bootstrap;
import dev.rafex.insightbloom.stats.adapters.inbound.http.HttpServer;
import dev.rafex.insightbloom.stats.adapters.inbound.http.handlers.*;
import dev.rafex.insightbloom.stats.adapters.outbound.sqlite.*;
import dev.rafex.insightbloom.stats.application.usecases.*;
import dev.rafex.insightbloom.stats.domain.services.RelevanceService;
public class StatsApplication {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT","8085"));
        String dbPath = System.getenv().getOrDefault("DB_PATH","stats.db");
        var db = new DatabaseManager(dbPath);
        db.initialize();
        var statsRepo = new SqliteWordStatsRepository(db);
        var relevanceService = new RelevanceService();
        var recalcUseCase = new RecalcStatsUseCase(statsRepo, relevanceService);
        var getStatsUseCase = new GetStatsUseCase(statsRepo);
        var recalcHandler = new RecalcHandler(recalcUseCase);
        var statsHandler = new StatsHandler(getStatsUseCase);
        var healthHandler = new HealthHandler();
        var server = new HttpServer(port, recalcHandler, statsHandler, healthHandler);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { server.stop(); } catch (Exception e) { e.printStackTrace(); } }));
        server.start();
        server.join();
    }
}
