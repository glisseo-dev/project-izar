package dev.glisseo.izar.integrationtests.bookstoreconsumer;

/** This application's own service model: what {@code BookService} hands its callers. */
public record BookSummary(String title, Integer pageCount, String authorName) {}
