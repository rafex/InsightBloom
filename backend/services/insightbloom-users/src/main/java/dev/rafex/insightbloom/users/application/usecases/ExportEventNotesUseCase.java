package dev.rafex.insightbloom.users.application.usecases;

import dev.rafex.insightbloom.users.domain.ports.EtherpadPort;

import java.util.Optional;

/** Lee las notas del pad que corresponde al usuario, sin exponer la API de Etherpad. */
public class ExportEventNotesUseCase {
    public record NotesExport(String text, String html, boolean individual) {}

    private final GetOrCreateEventPadUseCase getOrCreateEventPadUseCase;
    private final EtherpadPort etherpadPort;

    public ExportEventNotesUseCase(final GetOrCreateEventPadUseCase getOrCreateEventPadUseCase,
                                    final EtherpadPort etherpadPort) {
        this.getOrCreateEventPadUseCase = getOrCreateEventPadUseCase;
        this.etherpadPort = etherpadPort;
    }

    public Optional<NotesExport> execute(final String conferenceUuid, final String userUuid) {
        return getOrCreateEventPadUseCase.execute(conferenceUuid, userUuid).map(pad -> {
            final EtherpadPort.PadContent content = etherpadPort.readPad(pad.padId());
            return new NotesExport(content.text(), content.html(),
                    pad.padId().contains("--private--"));
        });
    }
}
