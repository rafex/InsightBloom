package dev.rafex.insightbloom.stats.domain.model;
public enum WordIntent {
    PREGUNTA, IDEA, DUDA, INTERES;
    public double weight() {
        return switch (this) {
            case PREGUNTA -> 1.15;
            case IDEA -> 1.00;
            case DUDA -> 1.25;
            case INTERES -> 1.10;
        };
    }
}
