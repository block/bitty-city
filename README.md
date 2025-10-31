# Bitty-City

<img src="https://www.herecomesbitcoin.org/assets/HereComesBitcoinAssets/Brrr-cat.png" alt="Brrr Cat Bitcoin" width="200">

This project prescribes the custodial product experience for bitcoin on-chain operations. It is used currently by Block for Square Bitcoin withdrawals.

## Building

Bitty City uses CashApp's [Hermit](https://cashapp.github.io/hermit/). Hermit ensures that your team, your contributors,
and your CI have the same consistent tooling. Here are the [installation instructions](https://cashapp.github.io/hermit/usage/get-started/#installing-hermit).

[Activate Hermit](https://cashapp.github.io/hermit/usage/get-started/#activating-an-environment) either
by [enabling the shell hooks](https://cashapp.github.io/hermit/usage/shell/) (one-time only, recommended) or manually
sourcing the env with `. ./bin/activate-hermit`.

Use gradle to run all tests:

```bash
bin/gradle build
```

## Project Structure

- `innie/` - Deposits module
- `outie/` - Withdrawals module

## Development

The project is set up with:
- Kotlin 2.0.21
- Java 11 target
- Kotest for testing
- Dokka for documentation
- Maven publishing support

## Modules

### Innie
Handles bitcoin deposit operations and related custodial product experience.

### Outie
Handles bitcoin withdrawal operations and related custodial product experience.

## Database Migrations

The project uses Flyway for database migrations. Migrations are located in the `outie-jooq-provider/src/main/resources/migrations` directory.

### Migration File Naming Convention

Migration files must follow the Flyway naming convention:
- Format: `V{version}__{description}.sql`
- Example: `V20250414.1414__create_withdrawals_table.sql`
- The version must start with an uppercase 'V'
- Use periods (not underscores) in the version number
- Double underscores between version and description
- Description uses underscores for spaces

### Available Gradle Tasks

The following Gradle tasks are available for database management:

```bash
# Apply migrations to local database
./bin/gradle :outie-jooq-provider:migrateLocal

# Show current migration status
./bin/gradle :outie-jooq-provider:migrationStatus

# Validate migration files (without executing)
./bin/gradle :outie-jooq-provider:validateMigrations

# Clean the database (CAUTION: removes all tables and data)
./bin/gradle :outie-jooq-provider:cleanDatabase
```

### Creating New Migrations

1. Create a new SQL file in the migrations directory following the naming convention
2. Write your SQL migration statements (create table, alter table, etc.)
3. Run the validateMigrations task to verify your migration file is valid
4. Run the migrateLocal task to apply your migration