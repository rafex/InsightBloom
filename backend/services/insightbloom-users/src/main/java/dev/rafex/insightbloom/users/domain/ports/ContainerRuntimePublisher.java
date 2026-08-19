package dev.rafex.insightbloom.users.domain.ports;

import dev.rafex.insightbloom.users.domain.model.ContainerBuildResult;

/** Ejecuta builds fuera de users, en el runtime aislado de publicación de contenedores. */
public interface ContainerRuntimePublisher {
    ContainerBuildResult buildAndRun(String containerfileContent, int hostPort, int containerPort);
}
