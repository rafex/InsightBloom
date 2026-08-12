package dev.rafex.insightbloom.users.domain.services;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache en memoria de los ZIPs ya armados por StartWorkspaceZipJobUseCase, mientras esperan a
 * que el usuario los descargue (hasta el TTL de 2hs, ver WorkspaceZipJob). Vive solo en este
 * proceso -- igual que el resto del estado en memoria de este servicio (ej.
 * NotificationStreamRegistry) -- no sobrevive un reinicio ni se comparte entre pods; aceptable
 * porque el tamaño máximo de un zip ya está acotado (ver sandbox_file_api.py: MAX_ZIP_BYTES) y el
 * volumen de jobs concurrentes es bajo. Si el pod se reinicia con un job READY todavía sin
 * descargar, el download devuelve "zip_unavailable" y el usuario debe generar uno nuevo.
 */
public final class WorkspaceZipCache {
    private final ConcurrentHashMap<String, byte[]> bytesByJobUuid = new ConcurrentHashMap<>();

    public void put(final String jobUuid, final byte[] zipBytes) {
        bytesByJobUuid.put(jobUuid, zipBytes);
    }

    public byte[] get(final String jobUuid) {
        return bytesByJobUuid.get(jobUuid);
    }

    public void remove(final String jobUuid) {
        bytesByJobUuid.remove(jobUuid);
    }
}
