package dev.glisseo.izar.examples.onequeryconsumer;

/** This application's own service model: what {@code BookService} hands its callers. */
public record BookSummary(String title, Integer pageCount, String authorName) {}
