package dev.glisseo.izar.compiler;

/** Shared schema/operation text for compiler tests. */
final class Fixtures {

    private Fixtures() {}

    static final String BOOK_SCHEMA =
            """
            type Query {
              book: Book
            }

            type Book {
              title: String!
              pageCount: Int
              author: Author
            }

            type Author {
              name: String!
            }
            """;

    static final String GET_BOOK_OPERATION =
            """
            query GetBook {
              book {
                title
                pageCount
                author {
                  name
                }
              }
            }
            """;

    static final String BOOK_SCHEMA_WITH_SUBSCRIPTION =
            """
            type Query {
              book: Book
            }

            type Subscription {
              bookChanged: Book!
            }

            type Book {
              title: String!
            }
            """;

    static final String WATCH_BOOK_OPERATION =
            """
            subscription WatchBook {
              bookChanged {
                title
              }
            }
            """;

    /**
     * A schema with a mutation, an enum, and nested/list-shaped input objects: the surface issue
     * 02's variable builders need to cover in one place. {@code BookInput.title} is required with
     * no default, {@code pageCount} is optional with no default, {@code genre} is optional with a
     * schema default, {@code tags} is a nullable list of a nullable scalar, and {@code coAuthors}
     * is a nullable list of a non-null nested input object.
     */
    static final String BOOK_SCHEMA_WITH_MUTATION =
            """
            type Query {
              book: Book
            }

            type Mutation {
              createBook(input: BookInput!): Book
              setFeatureFlag(enabled: Boolean!): Boolean!
            }

            type Book {
              title: String!
              pageCount: Int
              author: Author
            }

            type Author {
              name: String!
            }

            enum Genre {
              FICTION
              NONFICTION
            }

            input BookInput {
              title: String!
              pageCount: Int
              genre: Genre = FICTION
              tags: [String]
              coAuthors: [AuthorInput!]
            }

            input AuthorInput {
              name: String!
            }
            """;

    static final String CREATE_BOOK_OPERATION =
            """
            mutation CreateBook($input: BookInput!) {
              createBook(input: $input) {
                title
                pageCount
              }
            }
            """;

    /**
     * {@code $enabled} is a non-null variable with a usable operation default: leaving it unset
     * must not fail, unlike {@code CreateBook}'s {@code $input}.
     */
    static final String SET_FEATURE_FLAG_OPERATION =
            """
            mutation SetFeatureFlag($enabled: Boolean! = true) {
              setFeatureFlag(enabled: $enabled)
            }
            """;

    /** {@code title} is aliased twice, at two different nesting depths, in one operation. */
    static final String GET_BOOK_WITH_ALIASES_OPERATION =
            """
            query GetBookWithAlias {
              b: book {
                bookTitle: title
                pageCount
              }
            }
            """;

    static final String BOOK_FIELDS_FRAGMENT =
            """
            fragment BookFields on Book {
              title
              pageCount
            }
            """;

    /** References {@link #BOOK_FIELDS_FRAGMENT} by name; also selects a field outside it. */
    static final String GET_BOOK_WITH_NAMED_FRAGMENT_OPERATION =
            """
            query GetBookWithFragment {
              book {
                ...BookFields
                author {
                  name
                }
              }
            }
            """;

    /**
     * {@code title} is selected once directly and once through an inline fragment: an overlapping
     * selection that must merge into one generated field, not two.
     */
    static final String GET_BOOK_WITH_INLINE_FRAGMENT_OPERATION =
            """
            query GetBookWithInlineFragment {
              book {
                title
                ... on Book {
                  title
                  pageCount
                }
              }
            }
            """;

    /**
     * {@code title} (schema non-null) carries {@code @skip}; {@code author} (already schema
     * nullable) carries {@code @include}. Both directive arguments are variables, so which
     * outcome occurs is a runtime, not compile-time, fact.
     */
    static final String GET_BOOK_WITH_CONDITIONAL_SELECTIONS_OPERATION =
            """
            query GetBookConditional($skipTitle: Boolean!, $includeAuthor: Boolean!) {
              book {
                title @skip(if: $skipTitle)
                pageCount
                author @include(if: $includeAuthor) {
                  name
                }
              }
            }
            """;

    /**
     * {@code title} is reachable both unconditionally (via the top-level selection) and
     * conditionally (via the {@code @include}d inline fragment): the unconditional path
     * guarantees presence, so {@code title} must stay non-null despite the conditional fragment.
     */
    static final String GET_BOOK_WITH_PARTIALLY_CONDITIONAL_FIELD_OPERATION =
            """
            query GetBookPartiallyConditional($includeExtra: Boolean!) {
              book {
                title
                ... on Book @include(if: $includeExtra) {
                  title
                  pageCount
                }
              }
            }
            """;

    /**
     * {@code books} is a non-null list of non-null books; {@code tags} is a nullable list of a
     * nullable scalar; {@code ratings} is a nullable list of nullable lists of a nullable scalar:
     * covers a plain list, a nested list, and container/element nullability independent of each
     * other.
     */
    static final String BOOK_SCHEMA_WITH_LISTS =
            """
            type Query {
              books: [Book!]!
            }

            type Book {
              title: String!
              tags: [String]
              ratings: [[Int]]
            }
            """;

    static final String GET_BOOKS_OPERATION =
            """
            query GetBooks {
              books {
                title
                tags
                ratings
              }
            }
            """;

    static final String AUTHOR_FIELDS_FRAGMENT =
            """
            fragment AuthorFields on Author {
              name
            }
            """;

    /** {@code genre} is a non-null output enum: the surface issue 05's enum evolution covers. */
    static final String BOOK_SCHEMA_WITH_OUTPUT_ENUM =
            """
            type Query {
              book: Book
            }

            type Book {
              title: String!
              genre: Genre!
            }

            enum Genre {
              FICTION
              NONFICTION
            }
            """;

    static final String GET_BOOK_GENRE_OPERATION =
            """
            query GetBookGenre {
              book {
                title
                genre
              }
            }
            """;

    /** {@code genre} is nullable here, unlike {@link #BOOK_SCHEMA_WITH_OUTPUT_ENUM}'s. */
    static final String BOOK_SCHEMA_WITH_NULLABLE_OUTPUT_ENUM =
            """
            type Query {
              book: Book
            }

            type Book {
              title: String!
              genre: Genre
            }

            enum Genre {
              FICTION
              NONFICTION
            }
            """;

    static final String GET_BOOK_NULLABLE_GENRE_OPERATION =
            """
            query GetBookNullableGenre {
              book {
                title
                genre
              }
            }
            """;

    /**
     * {@code SearchResult} is a union of two otherwise-unrelated object types. The operation
     * selects {@code __typename} directly, so the compiler must not inject a duplicate
     * discriminator.
     */
    static final String SEARCH_RESULT_SCHEMA =
            """
            type Query {
              search: [SearchResult!]!
            }

            union SearchResult = Book | Movie

            type Book {
              title: String!
            }

            type Movie {
              title: String!
              director: String!
            }
            """;

    static final String SEARCH_OPERATION =
            """
            query Search {
              search {
                __typename
                ... on Book {
                  title
                }
                ... on Movie {
                  title
                  director
                }
              }
            }
            """;

    /**
     * {@code Media} is an interface with a shared field ({@code title}) and two implementors with
     * type-specific fields. The operation selects no {@code __typename} itself, so the compiler
     * must inject one to dispatch decoding.
     */
    static final String MEDIA_SCHEMA =
            """
            type Query {
              featured: Media
            }

            interface Media {
              title: String!
            }

            type Book implements Media {
              title: String!
              pageCount: Int
            }

            type Movie implements Media {
              title: String!
              director: String!
            }
            """;

    static final String GET_FEATURED_MEDIA_OPERATION =
            """
            query GetFeaturedMedia {
              featured {
                title
                ... on Book {
                  pageCount
                }
                ... on Movie {
                  director
                }
              }
            }
            """;

    /** No type-conditioned fragment: {@code featured} must decode as a plain shared-fields type. */
    static final String GET_FEATURED_MEDIA_TITLE_ONLY_OPERATION =
            """
            query GetFeaturedMediaTitleOnly {
              featured {
                title
              }
            }
            """;

    /**
     * Aliases {@code title} to the response key {@code __typename}, occupying the compiler's
     * preferred discriminator key: it must fall back to a different one rather than colliding.
     */
    static final String GET_FEATURED_MEDIA_WITH_TYPENAME_ALIAS_OPERATION =
            """
            query GetFeaturedMediaWithTypenameAlias {
              featured {
                __typename: title
                ... on Book {
                  pageCount
                }
                ... on Movie {
                  director
                }
              }
            }
            """;

    /**
     * Two custom scalars ({@code DateTime}, mapped to {@code java.time.Instant}; {@code Money},
     * mapped to {@code java.math.BigDecimal}), reached through a plain optional field ({@code
     * publishedAt}), a nullable list of a nullable scalar ({@code notableDates}), a nested output
     * object ({@code edition.releasedAt}), and a nested input object ({@code
     * BookInput.edition.releasedAt}): the surface issue 06's acceptance criteria ask for.
     */
    static final String BOOK_SCHEMA_WITH_SCALARS =
            """
            scalar DateTime
            scalar Money

            type Query {
              book: Book
            }

            type Mutation {
              createBook(input: BookInput!): Book
            }

            type Book {
              title: String!
              publishedAt: DateTime
              price: Money
              notableDates: [DateTime]
              edition: Edition
            }

            type Edition {
              releasedAt: DateTime!
            }

            input BookInput {
              title: String!
              publishedAt: DateTime
              price: Money
              notableDates: [DateTime]
              edition: EditionInput
            }

            input EditionInput {
              releasedAt: DateTime!
            }
            """;

    static final String GET_BOOK_WITH_SCALARS_OPERATION =
            """
            query GetBookWithScalars {
              book {
                title
                publishedAt
                price
                notableDates
                edition {
                  releasedAt
                }
              }
            }
            """;

    static final String CREATE_BOOK_WITH_SCALARS_OPERATION =
            """
            mutation CreateBookWithScalars($input: BookInput!) {
              createBook(input: $input) {
                title
              }
            }
            """;

    /**
     * {@code CategoryInput} is directly self-referential through a nullable list of itself, the
     * common tree-shaped case (category trees, comment threads) issue 31's acceptance criteria
     * ask for. {@code NodeAInput} and {@code NodeBInput} are mutually recursive through a
     * nullable {@code partner} field on each side, so a finite value is always constructible
     * despite neither type being buildable in isolation.
     */
    static final String RECURSIVE_INPUT_SCHEMA =
            """
            type Query {
              category: Category
            }

            type Mutation {
              saveCategory(input: CategoryInput!): Category
              linkNodes(a: NodeAInput!, b: NodeBInput!): Boolean!
            }

            type Category {
              name: String!
            }

            input CategoryInput {
              name: String!
              children: [CategoryInput!]
            }

            input NodeAInput {
              label: String!
              partner: NodeBInput
            }

            input NodeBInput {
              label: String!
              partner: NodeAInput
            }
            """;

    static final String SAVE_CATEGORY_OPERATION =
            """
            mutation SaveCategory($input: CategoryInput!) {
              saveCategory(input: $input) {
                name
              }
            }
            """;

    static final String LINK_NODES_OPERATION =
            """
            mutation LinkNodes($a: NodeAInput!, $b: NodeBInput!) {
              linkNodes(a: $a, b: $b)
            }
            """;
}
