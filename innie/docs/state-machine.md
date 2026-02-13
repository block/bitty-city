```mermaid
stateDiagram-v2
[*] --> New
CheckingEligibility --> CheckingDepositRisk
CheckingEligibility --> PendingReversal
CheckingDepositRisk --> PendingReversal
CheckingDepositRisk --> Settled
CheckingReversalRisk --> PendingReversal
CheckingReversalRisk --> AwaitingReversalPendingConfirmation
CheckingSanctions --> CheckingReversalRisk
CheckingSanctions --> PendingReversal
CheckingSanctions --> CollectingSanctionsInfo
PendingReversal --> CheckingSanctions
CollectingSanctionsInfo --> PendingReversal
CollectingSanctionsInfo --> Sanctioned
CollectingSanctionsInfo --> AwaitingReversalPendingConfirmation
CollectingSanctionsInfo --> AwaitingSanctionsDecision
CreatingDepositTransaction --> AwaitingDepositConfirmation
Evicted --> CheckingEligibility
Evicted --> Voided
New --> CreatingDepositTransaction
AwaitingDepositConfirmation --> CheckingEligibility
AwaitingDepositConfirmation --> Evicted
AwaitingDepositConfirmation --> Voided
AwaitingReversalConfirmation --> PendingReversal
AwaitingReversalConfirmation --> Reversed
AwaitingReversalPendingConfirmation --> PendingReversal
AwaitingReversalPendingConfirmation --> AwaitingReversalConfirmation
AwaitingSanctionsDecision --> CheckingReversalRisk
AwaitingSanctionsDecision --> PendingReversal
AwaitingSanctionsDecision --> Sanctioned
```