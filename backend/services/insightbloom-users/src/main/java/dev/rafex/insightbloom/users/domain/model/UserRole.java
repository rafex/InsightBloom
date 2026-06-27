package dev.rafex.insightbloom.users.domain.model;

import java.util.Set;
import java.util.stream.Collectors;

public enum UserRole {
    ADMIN, ORGANIZER, MODERATOR, GUEST, ATTENDEE;

    /** CSV de roles en minúscula para el campo "role" de la API, p.ej. "admin,organizer". */
    public static String toCsv(final Set<UserRole> roles) {
        return roles.stream().map(r -> r.name().toLowerCase()).collect(Collectors.joining(","));
    }
}
