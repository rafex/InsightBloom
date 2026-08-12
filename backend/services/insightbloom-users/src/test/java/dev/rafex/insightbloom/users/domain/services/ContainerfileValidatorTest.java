package dev.rafex.insightbloom.users.domain.services;

import dev.rafex.insightbloom.users.application.usecases.ResolveImagePolicyUseCase;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ContainerfileValidatorTest {
    private static ResolveImagePolicyUseCase.Resolution resolution(final Set<String> allowed, final Set<String> blocked) {
        return new ResolveImagePolicyUseCase.Resolution(allowed, blocked);
    }

    private static final ResolveImagePolicyUseCase.Resolution NO_RESTRICTIONS = resolution(Set.of(), Set.of());

    @Test
    void acceptsSimpleDockerHubImageWithImplicitRegistry() {
        final var result = ContainerfileValidator.validate("FROM python:3.12-slim\n", NO_RESTRICTIONS);
        assertTrue(result.valid());
        assertEquals(1, result.images().size());
        assertEquals("docker.io/python:3.12-slim", result.images().get(0));
    }

    @Test
    void acceptsExplicitDockerIoAndGhcrRegistries() {
        final var result = ContainerfileValidator.validate(
                "FROM docker.io/library/node:20\nFROM ghcr.io/rafex/base:latest AS final\n", NO_RESTRICTIONS);
        assertTrue(result.valid());
        assertEquals(2, result.images().size());
    }

    @Test
    void rejectsDisallowedRegistry() {
        final var result = ContainerfileValidator.validate("FROM quay.io/something:latest\n", NO_RESTRICTIONS);
        assertFalse(result.valid());
        assertEquals("containerfile_registry_not_allowed", result.errorCode());
    }

    @Test
    void rejectsPrivateRegistryHost() {
        final var result = ContainerfileValidator.validate("FROM registry.internal.example/team/app:1\n", NO_RESTRICTIONS);
        assertFalse(result.valid());
        assertEquals("containerfile_registry_not_allowed", result.errorCode());
    }

    @Test
    void rejectsUnresolvedBuildArgVariable() {
        final var result = ContainerfileValidator.validate("FROM ${BASE_IMAGE}\n", NO_RESTRICTIONS);
        assertFalse(result.valid());
        assertEquals("containerfile_dynamic_from", result.errorCode());
    }

    @Test
    void rejectsShellStyleVariable() {
        final var result = ContainerfileValidator.validate("FROM $BASE:latest\n", NO_RESTRICTIONS);
        assertFalse(result.valid());
        assertEquals("containerfile_dynamic_from", result.errorCode());
    }

    @Test
    void supportsMultiStageAndSkipsStageAliasReferences() {
        final String containerfile = """
                FROM golang:1.22 AS builder
                RUN go build -o app
                FROM alpine:3.19
                COPY --from=builder /app /app
                """;
        final var result = ContainerfileValidator.validate(containerfile, NO_RESTRICTIONS);
        assertTrue(result.valid());
        assertEquals(2, result.images().size());
    }

    @Test
    void treatsFromReferencingEarlierStageAsNotARealImage() {
        final String containerfile = """
                FROM node:20 AS builder
                FROM builder
                RUN echo hi
                """;
        final var result = ContainerfileValidator.validate(containerfile, NO_RESTRICTIONS);
        assertTrue(result.valid());
        assertEquals(1, result.images().size());
    }

    @Test
    void rejectsImageBlockedGlobally() {
        final var policy = resolution(Set.of(), Set.of("python"));
        final var result = ContainerfileValidator.validate("FROM python:3.12\n", policy);
        assertFalse(result.valid());
        assertEquals("containerfile_image_not_allowed", result.errorCode());
    }

    @Test
    void rejectsImageNotInAllowlistWhenAllowlistIsSet() {
        final var policy = resolution(Set.of("node", "golang"), Set.of());
        final var result = ContainerfileValidator.validate("FROM python:3.12\n", policy);
        assertFalse(result.valid());
        assertEquals("containerfile_image_not_allowed", result.errorCode());
    }

    @Test
    void acceptsImageInAllowlist() {
        final var policy = resolution(Set.of("node", "golang"), Set.of());
        final var result = ContainerfileValidator.validate("FROM node:20-alpine\n", policy);
        assertTrue(result.valid());
    }

    @Test
    void blockedAlwaysWinsEvenIfAllowed() {
        final var policy = resolution(Set.of("python"), Set.of("python"));
        final var result = ContainerfileValidator.validate("FROM python:3.12\n", policy);
        assertFalse(result.valid());
        assertEquals("containerfile_image_not_allowed", result.errorCode());
    }

    @Test
    void rejectsEmptyContainerfile() {
        final var result = ContainerfileValidator.validate("", NO_RESTRICTIONS);
        assertFalse(result.valid());
        assertEquals("containerfile_empty", result.errorCode());
    }

    @Test
    void rejectsContainerfileWithNoFromInstruction() {
        final var result = ContainerfileValidator.validate("RUN echo hola\nCOPY . .\n", NO_RESTRICTIONS);
        assertFalse(result.valid());
        assertEquals("containerfile_no_from", result.errorCode());
    }

    @Test
    void ignoresCommentedOutFromLines() {
        final String containerfile = "# FROM quay.io/should-be-ignored:latest\nFROM python:3.12\n";
        final var result = ContainerfileValidator.validate(containerfile, NO_RESTRICTIONS);
        assertTrue(result.valid());
        assertEquals(1, result.images().size());
    }
}
