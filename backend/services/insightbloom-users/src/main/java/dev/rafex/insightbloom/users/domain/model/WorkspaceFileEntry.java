package dev.rafex.insightbloom.users.domain.model;

/**
 * Una entrada del arbol de archivos del workspace de un alumno -- ver
 * ListWorkspaceFilesUseCase / SandboxOrchestrator#listWorkspaceFiles. {@code path} es SIEMPRE
 * relativo a la raiz del workspace del alumno (nunca un path absoluto real del filesystem del
 * Pod), {@code mtime} son segundos Unix con fraccion (igual formato que {@code os.stat().
 * st_mtime} en Python, ver sandbox_file_api.py -- se manda de vuelta tal cual al guardar, para
 * la deteccion de conflictos de escritura).
 */
public record WorkspaceFileEntry(String path, boolean isDirectory, double mtime, long sizeBytes) {
}
