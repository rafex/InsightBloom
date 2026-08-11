package dev.rafex.insightbloom.users.domain.model;

/** Sugerencia de herramienta en un timestamp del video on-demand de una conferencia. */
public record OnDemandCuePoint(int atSeconds, String label, String toolPath) {}
