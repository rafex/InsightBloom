package dev.rafex.insightbloom.query.bootstrap;
import dev.rafex.insightbloom.query.adapters.inbound.http.HttpServer;
import dev.rafex.insightbloom.query.adapters.inbound.http.handlers.*;
import dev.rafex.insightbloom.query.adapters.outbound.sqlite.*;
import dev.rafex.insightbloom.query.application.usecases.*;
public class QueryApplication {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT","8083"));
        String dbPath = System.getenv().getOrDefault("DB_PATH","query.db");
        var db = new DatabaseManager(dbPath);
        db.initialize();
        var cloudRepo = new SqliteCloudWordRepository(db);
        var timelineRepo = new SqliteWordTimelineRepository(db);
        var getCloudUseCase = new GetCloudUseCase(cloudRepo);
        var getTimelineUseCase = new GetTimelineUseCase(timelineRepo);
        var updateUseCase = new UpdateCloudUseCase(cloudRepo, timelineRepo);
        var setMessageVisibilityUseCase = new SetMessageVisibilityUseCase(timelineRepo);
        var cloudHandler = new CloudHandler(getCloudUseCase);
        var timelineHandler = new TimelineHandler(getTimelineUseCase);
        var updateHandler = new UpdateHandler(updateUseCase);
        var messageVisibilityHandler = new MessageVisibilityHandler(setMessageVisibilityUseCase);
        var healthHandler = new HealthHandler();
        var server = new HttpServer(port, cloudHandler, timelineHandler, updateHandler, messageVisibilityHandler, healthHandler);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { server.stop(); } catch (Exception e) { e.printStackTrace(); } }));
        server.start();
        server.join();
    }
}
