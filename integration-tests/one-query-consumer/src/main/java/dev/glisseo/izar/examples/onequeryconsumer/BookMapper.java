package dev.glisseo.izar.examples.onequeryconsumer;

import dev.glisseo.izar.generated.books.GetBookQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps the generated response record into this application's own service model. */
@Mapper(componentModel = "spring")
public interface BookMapper {

    @Mapping(target = "authorName", source = "author.name")
    BookSummary toBookSummary(GetBookQuery.Book book);
}
