package dev.rafex.insightbloom.survey.bootstrap;

import dev.rafex.ether.http.jetty12.routing.JettyRouteRegistry;
import dev.rafex.ether.http.jetty12.JettyServerConfig;
import dev.rafex.ether.http.jetty12.JettyServerFactory;
import dev.rafex.ether.json.JacksonJsonCodec;
import dev.rafex.ether.json.JsonUtils;
import dev.rafex.insightbloom.survey.adapters.inbound.http.handlers.SurveyHandler;
import dev.rafex.insightbloom.survey.adapters.outbound.llm.GroqLlmClient;
import dev.rafex.insightbloom.survey.adapters.outbound.presentations.HttpPresentationsClient;
import dev.rafex.insightbloom.survey.adapters.outbound.sqlite.DatabaseManager;
import dev.rafex.insightbloom.survey.adapters.outbound.sqlite.SqliteSurveyQuestionRepository;
import dev.rafex.insightbloom.survey.adapters.outbound.sqlite.SqliteSurveyResponseRepository;
import dev.rafex.insightbloom.survey.adapters.outbound.usersclient.HttpUsersClient;
import dev.rafex.insightbloom.survey.application.usecases.CreateQuestionUseCase;
import dev.rafex.insightbloom.survey.application.usecases.DeactivateQuestionUseCase;
import dev.rafex.insightbloom.survey.application.usecases.DeleteConferenceDataUseCase;
import dev.rafex.insightbloom.survey.application.usecases.GetResultsUseCase;
import dev.rafex.insightbloom.survey.application.usecases.ImproveQuestionUseCase;
import dev.rafex.insightbloom.survey.application.usecases.ListQuestionsUseCase;
import dev.rafex.insightbloom.survey.application.usecases.PurgeResponsesUseCase;
import dev.rafex.insightbloom.survey.application.usecases.SubmitResponsesUseCase;
import dev.rafex.insightbloom.survey.application.usecases.SuggestQuestionsUseCase;
import dev.rafex.insightbloom.survey.application.usecases.UpdateQuestionUseCase;

import java.util.List;

public class SurveyApplication {
    public static void main(final String[] args) throws Exception {
        final String dbPath = System.getenv().getOrDefault("DB_PATH", "survey.db");
        final String llmBaseUrl = System.getenv().getOrDefault("LLM_PROVIDER_BASE_URL", "https://api.groq.com/openai/v1");
        final String llmApiKey = System.getenv().getOrDefault("LLM_PROVIDER_API_KEY", "");
        final String llmModel = System.getenv().getOrDefault("LLM_PROVIDER_MODEL", "openai/gpt-oss-120b");
        final String presentationsBaseUrl = System.getenv().getOrDefault(
                "PRESENTATIONS_BASE_URL", "http://insightbloom-presentations:8091");
        final String usersBaseUrl = System.getenv().getOrDefault("USERS_URL", "http://insightbloom-users:8081");

        final var db = new DatabaseManager(dbPath);
        db.initialize();

        final var questionRepo = new SqliteSurveyQuestionRepository(db);
        final var responseRepo = new SqliteSurveyResponseRepository(db);
        final var llm = new GroqLlmClient(llmBaseUrl, llmApiKey, llmModel, JsonUtils.codec());
        final var presentationsClient = new HttpPresentationsClient(presentationsBaseUrl);
        final var usersPort = new HttpUsersClient(usersBaseUrl);

        final var createQuestionUseCase = new CreateQuestionUseCase(questionRepo);
        final var listQuestionsUseCase = new ListQuestionsUseCase(questionRepo);
        final var deactivateQuestionUseCase = new DeactivateQuestionUseCase(questionRepo);
        final var submitResponsesUseCase = new SubmitResponsesUseCase(questionRepo, responseRepo, llm);
        final var getResultsUseCase = new GetResultsUseCase(questionRepo, responseRepo, usersPort);
        final var suggestQuestionsUseCase = new SuggestQuestionsUseCase(llm, presentationsClient, JsonUtils.codec());
        final var updateQuestionUseCase = new UpdateQuestionUseCase(questionRepo);
        final var purgeResponsesUseCase = new PurgeResponsesUseCase(responseRepo);
        final var deleteConferenceDataUseCase = new DeleteConferenceDataUseCase(questionRepo, responseRepo);
        final var improveQuestionUseCase = new ImproveQuestionUseCase(llm, presentationsClient, JsonUtils.codec());

        final var surveyHandler = new SurveyHandler(
                createQuestionUseCase, listQuestionsUseCase, deactivateQuestionUseCase,
                submitResponsesUseCase, getResultsUseCase, suggestQuestionsUseCase, updateQuestionUseCase,
                purgeResponsesUseCase, deleteConferenceDataUseCase, improveQuestionUseCase, usersPort);

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
