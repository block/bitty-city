```mermaid
stateDiagram-v2
    [*] --> CollectingInfo
    CheckingEligibility --> Failed
    CheckingEligibility --> HoldingSubmission
    CheckingRisk --> CheckingTravelRule
    CheckingRisk --> CollectingScamWarningDecision
    CheckingRisk --> Failed
    CheckingSanctions --> CheckingRisk
    CheckingSanctions --> CollectingSanctionsInfo
    CheckingSanctions --> Failed
    CheckingTravelRule --> CheckingEligibility
    CheckingTravelRule --> CollectingSelfAttestation
    CheckingTravelRule --> Failed
    CollectingInfo --> CheckingSanctions
    CollectingInfo --> Failed
    CollectingSanctionsInfo --> CheckingEligibility
    CollectingSanctionsInfo --> Sanctioned
    CollectingSanctionsInfo --> WaitingForSanctionsHeldDecision
    CollectingScamWarningDecision --> CheckingTravelRule
    CollectingScamWarningDecision --> Failed
    CollectingSelfAttestation --> CheckingEligibility
    CollectingSelfAttestation --> Failed
    HoldingSubmission --> Failed
    HoldingSubmission --> SubmittingOnChain
    SubmittingOnChain --> Failed
    SubmittingOnChain --> WaitingForPendingConfirmationStatus
    WaitingForConfirmedOnChainStatus --> ConfirmedComplete
    WaitingForConfirmedOnChainStatus --> Failed
    WaitingForPendingConfirmationStatus --> ConfirmedComplete
    WaitingForPendingConfirmationStatus --> Failed
    WaitingForPendingConfirmationStatus --> WaitingForConfirmedOnChainStatus
    WaitingForSanctionsHeldDecision --> CheckingEligibility
    WaitingForSanctionsHeldDecision --> Failed
    WaitingForSanctionsHeldDecision --> Sanctioned
```