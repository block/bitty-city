# Bitty-City

<img src="https://www.herecomesbitcoin.org/assets/HereComesBitcoinAssets/Brrr-cat.png" alt="Brrr Cat Bitcoin" width="200">

This project prescribes the custodial product experience for bitcoin on-chain operations. It is 
used currently by [Block](https://block.xyz/) for [Square Bitcoin](https://squareup.com/us/en/bitcoin) withdrawals.

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

- `outie/` - Withdrawals module
- `outie-jooq-provider` - jOOQ bindings for the `outie` module
- `innie/` - Deposits module (WIP)

## Modules

### Innie
Handles bitcoin deposit operations and related custodial product experience.

### Outie
Handles bitcoin withdrawal operations and related custodial product experience.

### Outie-jOOQ-Provider
jOOQ bindings for the `outie` module.

#### Database Migrations

This module uses Flyway for database migrations. Migrations are located in the `outie-jooq-provider/src/main/resources/migrations` directory.

#### Migration File Naming Convention

Migration files must follow the Flyway naming convention:
- Format: `V{version}__{description}.sql`
- Example: `V20250414.1414__create_withdrawals_table.sql`
- The version must start with an uppercase 'V'
- Use periods (not underscores) in the version number
- Double underscores between version and description
- Description uses underscores for spaces
