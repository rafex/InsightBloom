package dev.rafex.insightbloom.users.domain.model;

/** Contenido de un archivo de texto del workspace de un alumno, mas el {@code mtime} con el
 *  que se leyo -- ver ReadWorkspaceFileUseCase / WriteWorkspaceFileUseCase (deteccion de
 *  conflicto: el editor manda de vuelta este mismo mtime al guardar). */
public record WorkspaceFileContent(String content, double mtime) {
}
