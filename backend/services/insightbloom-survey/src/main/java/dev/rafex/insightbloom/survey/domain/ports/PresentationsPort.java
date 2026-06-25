package dev.rafex.insightbloom.survey.domain.ports;

import java.util.Optional;

public interface PresentationsPort {
    Optional<String> fetchMarkdown(String conferenceId);
}
