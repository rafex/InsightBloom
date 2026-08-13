package dev.rafex.insightbloom.users.domain.model;

/** Configuración independiente de una herramienta de lienzo habilitada en un evento. */
public record CanvasConfig(CanvasTool tool, CanvasAudienceMode audienceMode) {}
