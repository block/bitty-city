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

- `common/` - Shared models and utilities
- `outie/` - Withdrawals module
- `innie/` - Deposits module (WIP)

## Modules

### Common
Shared models and utilities used across modules.

### Innie
Handles bitcoin deposit operations and related custodial product experience.

### Outie
Handles bitcoin withdrawal operations and related custodial product experience. See the [state machine diagram](outie/docs/state-machine.md) for details on the withdrawal flow.
