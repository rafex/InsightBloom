package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.application.usecases.ResolveImagePolicyUseCase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsea un Containerfile/Dockerfile de texto y valida cada instrucción {@code FROM} contra la
 * política de imágenes resuelta (ver {@link ResolveImagePolicyUseCase}) ANTES de que
 * PublishContainerUseCase toque el pod -- ningún build arranca si algo acá rechaza. No ejecuta
 * nada, es parseo + validación puro.
 *
 * Reglas (confirmadas con el usuario):
 * - Solo se permiten imágenes de {@code docker.io} o {@code ghcr.io} -- cualquier otro registro
 *   se rechaza siempre, sin importar la whitelist (regla fija de la plataforma).
 * - Una referencia dinámica ({@code FROM ${BASE_IMAGE}} o {@code FROM $BASE}) se rechaza -- no se
 *   puede validar una variable sin resolver, y permitirla sería un bypass directo de la whitelist.
 * - Multi-stage: un {@code FROM} que referencia el alias de un stage anterior (ej.
 *   {@code FROM builder}) no es una imagen real y se salta.
 * - El nombre de imagen (sin tag) se valida contra la whitelist/blacklist por prefijo simple (ver
 *   {@link ResolveImagePolicyUseCase.Resolution#isAllowed}).
 */
public final class ContainerfileValidator {
    private static final Pattern FROM_LINE = Pattern.compile(
            "^\\s*FROM\\s+(?:--\\S+\\s+)*(\\S+)(?:\\s+AS\\s+(\\S+))?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> ALLOWED_REGISTRIES = Set.of("docker.io", "ghcr.io");

    private ContainerfileValidator() { }

    public record ValidationResult(boolean valid, List<String> images, String errorCode, String errorDetail) {
        public static ValidationResult ok(final List<String> images) {
            return new ValidationResult(true, images, null, null);
        }

        public static ValidationResult reject(final String code, final String detail) {
            return new ValidationResult(false, List.of(), code, detail);
        }
    }

    public static ValidationResult validate(final String containerfileContent,
                                             final ResolveImagePolicyUseCase.Resolution policy) {
        if (containerfileContent == null || containerfileContent.isBlank()) {
            return ValidationResult.reject("containerfile_empty", "El Containerfile está vacío");
        }
        final Set<String> stageNames = new HashSet<>();
        final List<String> validatedImages = new ArrayList<>();

        for (final String rawLine : containerfileContent.split("\n", -1)) {
            final String line = stripComment(rawLine).strip();
            if (line.isEmpty()) continue;
            final Matcher matcher = FROM_LINE.matcher(line);
            if (!matcher.matches()) continue;

            final String imageRef = matcher.group(1);
            final String stageName = matcher.group(2);

            if (stageNames.contains(imageRef.toLowerCase())) {
                if (stageName != null) stageNames.add(stageName.toLowerCase());
                continue;
            }
            if (imageRef.contains("$")) {
                return ValidationResult.reject("containerfile_dynamic_from",
                        "FROM " + imageRef + " usa una variable sin resolver; no se puede validar contra la política de imágenes");
            }

            final ParsedImage parsed = parseImageRef(imageRef);
            if (parsed == null) {
                return ValidationResult.reject("containerfile_invalid_from", "No se pudo interpretar: FROM " + imageRef);
            }
            if (!ALLOWED_REGISTRIES.contains(parsed.registry())) {
                return ValidationResult.reject("containerfile_registry_not_allowed",
                        "Registro no permitido: " + parsed.registry() + " (solo docker.io/ghcr.io)");
            }
            if (!policy.isAllowed(parsed.name())) {
                return ValidationResult.reject("containerfile_image_not_allowed",
                        "Imagen no permitida por la política de este evento: " + parsed.name());
            }
            validatedImages.add(parsed.registry() + "/" + parsed.name() + (parsed.tag() != null ? ":" + parsed.tag() : ""));
            if (stageName != null) stageNames.add(stageName.toLowerCase());
        }

        if (validatedImages.isEmpty()) {
            return ValidationResult.reject("containerfile_no_from", "El Containerfile no tiene ninguna instrucción FROM válida");
        }
        return ValidationResult.ok(validatedImages);
    }

    private static String stripComment(final String line) {
        final int idx = line.indexOf('#');
        return idx >= 0 ? line.substring(0, idx) : line;
    }

    private record ParsedImage(String registry, String name, String tag) { }

    /** {@code null} si la referencia queda vacía tras parsear (entrada mal formada). */
    private static ParsedImage parseImageRef(final String ref) {
        final String withoutDigest = ref.contains("@") ? ref.substring(0, ref.indexOf('@')) : ref;

        final String registry;
        final String rest;
        final int firstSlash = withoutDigest.indexOf('/');
        final String firstSegment = firstSlash > 0 ? withoutDigest.substring(0, firstSlash) : "";
        final boolean hasExplicitRegistry = firstSlash > 0
                && (firstSegment.contains(".") || firstSegment.contains(":") || "localhost".equals(firstSegment));
        if (hasExplicitRegistry) {
            registry = firstSegment.toLowerCase();
            rest = withoutDigest.substring(firstSlash + 1);
        } else {
            registry = "docker.io";
            rest = withoutDigest;
        }

        final int lastSlash = rest.lastIndexOf('/');
        final int tagColon = rest.lastIndexOf(':');
        final String name;
        final String tag;
        if (tagColon > lastSlash) {
            name = rest.substring(0, tagColon);
            tag = rest.substring(tagColon + 1);
        } else {
            name = rest;
            tag = null;
        }
        if (name.isBlank()) return null;
        return new ParsedImage(registry, name, tag);
    }
}
