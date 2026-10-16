# Kbeauty AI ERP

A prototype inventory and sales application for a small cosmetics distributor.
Built to explore basic business workflows and a local AI interface for viewing
business data.

## What it does

- Manage products, customers, inventory, and sales orders through a REST API.
- Confirm, ship, or cancel orders. Shipping reduces stock.
- View low-stock products and sales reports, including sales by brand.
- Ask for tables, charts, metrics, or stock forms through a simple web page.
- Save stock changes explicitly and download tables as CSV files.

The backend supplies report values from PostgreSQL. AI helps select supported
views; it does not query the database directly. Some requests use explicit
matching to keep report filters predictable.

## Stack

Java 21, Spring Boot, Spring Data JPA, PostgreSQL, Thymeleaf.
Spring AI connects to Ollama using `qwen3:4b`. No cloud API key is needed.

## Run locally

You need Java 21, Docker Compose, and Ollama. Maven is included through the wrapper.

1. Start PostgreSQL:

   ```sh
   docker compose up -d
   ```

   It runs on port `5433`, with database `kbeauty_erp` and local development
   credentials `postgres` / `postgres`.

2. Start Ollama and download the model if it is not already installed:

   ```sh
   ollama pull qwen3:4b
   ```

3. Start the application with AI enabled:

   ```sh
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=ai
   ```

Open [the application](http://localhost:8080/) or
[Swagger UI](http://localhost:8080/swagger-ui/index.html).

To run only the ERP API without Ollama, use `./mvnw spring-boot:run`.
The AI endpoints require the `ai` profile. Ollama defaults to
`http://localhost:11434`; `OLLAMA_BASE_URL` and `OLLAMA_MODEL` can override it.

## Try the demo

Fictional sample data is included so you can try the interface without creating
products, customers, stock records, and orders by hand.

```sh
docker compose --profile demo up -d postgres-demo
./mvnw spring-boot:run -Dspring-boot.run.profiles=ai,demo
```

Start Ollama with `qwen3:4b` installed as described above. The demo uses a separate
PostgreSQL database on port `5434`; the normal database on `5433` is left alone.
Run one application instance at a time, since both use port `8080`.

On an empty demo database, startup creates:

- Eight products across two fictional brands: Example Beauty and Sample Seoul.
- Four customers, including one inactive customer.
- Inventory with five low-stock products, including one with zero stock.
- Twelve orders: eight shipped, two confirmed, one created, and one cancelled.

The data is loaded in one transaction. Startup skips loading when records already
exist, so restarts preserve your changes. Feel free to add products, customers,
and orders through Swagger UI, or change stock through the generated forms.
Use the IDs returned by the API for questions about a specific record.

Without the `demo` profile, no sample data is added. A fresh regular database
starts empty.

## Example requests

- "Show low stock products as a table."
- "Show low stock products as a bar chart."
- "How many low-stock products are there? Show a metric."
- "Create a restock form for low-stock products."
- "Show Example Beauty sales as a table."

For the last example, use a brand you have added to the database. Brand sales
reports use shipped orders across all dates, with amounts in TRY. Stock forms
set the total stock balance, rather than adding a quantity to the current balance.

## Web interface

The workspace uses basic Thymeleaf templates and standard HTML forms, with no
custom CSS or JavaScript. Submitting a question reloads the page with a table,
metric, chart using HTML meters, or stock forms. CSV downloads run on the server.
The current result is kept in the browser session for export; it is not passed
back to the model as conversation history. Creating a new view replaces it.
An older tab will ask you to regenerate its view before saving or downloading.
Swagger UI is a separate, third-party API explorer and includes its own assets.

## Current limits

This is a prototype, not a complete ERP or accounting system.

- Requests are independent; there is no conversation history.
- Reports support a fixed set of fields and filters. Brand sales requests use
  limited wording, and date filtering is not implemented.
- The local model can misinterpret questions. Check results against the underlying
  records before relying on them.
- There is no authentication, stock adjustment history, or automated backup setup.
- Hibernate updates the database schema automatically for local development.

## Tests

With PostgreSQL running:

```sh
./mvnw verify
```

Tests cover order rules, inventory changes, reports, and AI endpoint behavior.
Database tests roll back their test data. AI tests use a simulated model and do
not require Ollama.

CSV export and page rendering are covered by the Java tests.

## License

[MIT](LICENSE).
