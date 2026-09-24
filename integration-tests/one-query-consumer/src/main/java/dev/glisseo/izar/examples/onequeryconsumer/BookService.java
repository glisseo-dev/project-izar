package dev.glisseo.izar.examples.onequeryconsumer;

import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import dev.glisseo.izar.generated.books.GetBookQuery;
import org.springframework.stereotype.Service;

@Service
public class BookService {

    private final SynchronousGraphQlOperations operations;
    private final BookMapper mapper;

    BookService(SynchronousGraphQlOperations operations, BookMapper mapper) {
        this.operations = operations;
        this.mapper = mapper;
    }

    /**
     * Fetches the book through the generated {@code GetBookQuery} and maps it into this
     * application's own model. This service wants a clean response only, so it fails fast on any
     * GraphQL error via {@link dev.glisseo.izar.client.GraphQlResult#assertNoErrors()} rather than
     * inspecting partial data itself.
     */
    public BookSummary fetchBook() {
        GetBookQuery.Data data = operations.execute(new GetBookQuery()).assertNoErrors();
        GetBookQuery.Book book = data.book();
        if (book == null) {
            throw new IllegalStateException("No book returned.");
        }
        return mapper.toBookSummary(book);
    }
}
