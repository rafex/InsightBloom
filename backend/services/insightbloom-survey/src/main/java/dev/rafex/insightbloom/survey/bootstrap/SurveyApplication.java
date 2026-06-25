package dev.rafex.insightbloom.survey.bootstrap;

import dev.rafex.ether.http.jetty12.routing.JettyRouteRegistry;
import dev.rafex.ether.http.jetty12.JettyServerConfig;
import dev.rafex.ether.http.jetty12.JettyServerFactory;
import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.insightbloom.survey.adapters.inbound.http.handlers.SurveyHandler;
import dev.rafex.insightbloom.survey.adapters.outbound.sqlite.DatabaseManager;
import dev.rafex.insightbloom.survey.adapters.outbound.sqlite.SqliteSurveyQuestionRepository;
import dev.rafex.insightbloom.survey.adapters.outbound.sqlite.SqliteSurveyResponseRepository;
import dev.rafex.insightbloom.survey.application.usecases.CreateQuestionUseCase;
import dev.rafex.insightbloom.survey.application.usecases.DeactivateQuestionUseCase;
import dev.rafex.insightbloom.survey.application.usecases.GetResultsUseCase;
import dev.rafex.insightbloom.survey.application.usecases.ListQuestionsUseCase;
import dev.rafex.insightbloom.survey.application.usecases.SubmitResponsesUseCase;

import java.util.List;

public class SurveyApplication {
    public static void main(final String[] args) throws Exception {
        final String dbPath = System.getenv().getOrDefault("DB_PATH", "survey.db");

        final var db = new DatabaseManager(dbPath);
        db.initialize();

        final var questionRepo = new SqliteSurveyQuestionRepository(db);
        final var responseRepo = new SqliteSurveyResponseRepository(db);

        final var createQuestionUseCase = new CreateQuestionUseCase(questionRepo);
        final var listQuestionsUseCase = new ListQuestionsUseCase(questionRepo);
        final var deactivateQuestionUseCase = new DeactivateQuestionUseCase(questionRepo);
        final var submitResponsesUseCase = new SubmitResponsesUseCase(questionRepo, responseRepo);
        final var getResultsUseCase = new GetResultsUseCase(questionRepo, responseRepo);

        final var surveyHandler = new SurveyHandler(
                createQuestionUseCase, listQuestionsUseCase, deactivateQuestionUseCase,
                submitResponsesUseCase, getResultsUseCase);

        final var routes = new JettyRouteRegistry();
        routes.add("/api/v1/conferences/*", surveyHandler);

        final var codec = JacksonJsonCodec.defaultCodec();
        final var config = JettyServerConfig.fromEnv();
        final var runner = JettyServerFactory.create(config, routes, codec, null, List.of(), List.of());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { runner.stop(); } catch (final Exception e) { e.printStackTrace(); }
        }));

        runner.start();
        runner.await();
    }
}
