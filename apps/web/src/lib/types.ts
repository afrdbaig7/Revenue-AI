/** Typed API contracts for the RecoverAI dashboard (mirrors OpenAPI). */

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface DashboardSummary {
  revenueAtRiskMinor: number;
  revenueRecoveredMinor: number;
  incrementalRevenueMinor: number;
  recoveryRate: number;
  activeIncidents: number;
  unresolvedIncidents: number;
  recoveredIncidents: number;
  attemptsTotal: number;
  policyBlocks: number;
  lateAuthorizationPrevented: number;
  synthetic: boolean;
}

export interface IncidentRow {
  id: string;
  incidentType: string;
  status: string;
  amountMinor: number;
  currency: string;
  failureCategory: string | null;
  confidence: number | null;
  diagnosisLayer: string | null;
  selectedStrategy: string | null;
  attemptsCount: number;
  contactCount: number;
  recoveredAmountMinor: number;
  createdAt: string;
  nextActionAt: string | null;
  customerId: string | null;
  detectedAt?: string;
  diagnosedAt?: string;
  recoveredAt?: string;
  closedAt?: string;
  recoveryWindowEndsAt?: string | null;
  cancellationReason?: string | null;
  policyResult?: string | null;
  netRecoveredMinor?: number;
  interventionCostMinor?: number;
  paymentId?: string | null;
}

export interface Diagnosis {
  id: string;
  layer: "DETERMINISTIC" | "AI" | "HYBRID";
  failureCategory: string;
  confidence: number;
  source: string;
  evidence: string[];
  recommendedAction: string | null;
  modelVersion: string | null;
  promptVersion: string | null;
  createdAt: string;
}

export interface DecisionCandidate {
  strategy: string;
  probability: number;
  expectedValueMinor: number;
  expectedGrossMinor: number;
  interventionCostMinor: number;
  discountCostMinor: number;
  riskPenaltyMinor: number;
  frictionPenaltyMinor: number;
  timeToRecoveryHours: number;
  rationale: string;
}

export interface RecoveryDecision {
  id: string;
  incidentId: string;
  candidates: DecisionCandidate[];
  chosenStrategy: string;
  reason: string | null;
  confidence: number | null;
  rankingSource: string;
  modelVersion: string | null;
  promptVersion: string | null;
  policyResult: string | null;
  createdAt: string;
}

export interface RecoveryAction {
  id: string;
  incidentId: string;
  strategy: string;
  status: string;
  attemptNumber: number;
  scheduledFor: string;
  executedAt: string | null;
  idempotencyKey: string;
  providerReference: string | null;
  result: string | null;
  error: string | null;
  createdAt: string;
}

export interface IncidentCommunication {
  id: string;
  channel: string;
  status: string;
  simulated: boolean;
}

export interface AuditEvent {
  id: string;
  incidentId: string | null;
  entityType: string;
  entityId: string | null;
  actorType: string;
  actorId: string | null;
  eventType: string;
  timestamp: string;
  previousState: string | null;
  newState: string | null;
  correlationId?: string | null;
  metadata: Record<string, unknown> | null;
  decisionInputSnapshot?: Record<string, unknown> | null;
  decisionOutputSnapshot?: Record<string, unknown> | null;
}

export interface IncidentDetail {
  incident: IncidentRow;
  payment: unknown;
  diagnoses: Diagnosis[];
  decision: RecoveryDecision | null;
  actions: RecoveryAction[];
  attempts: unknown[];
  communications: IncidentCommunication[];
  approvals: unknown[];
}

export interface Experiment {
  id: string;
  name: string;
  description: string | null;
  seed: number;
  populationSize: number;
  status: string;
  results: ExperimentResults | null;
  createdAt: string;
  completedAt: string | null;
}

export interface ExperimentResults {
  synthetic: boolean;
  label: string;
  methodology: string;
  control: ArmResult;
  treatment: ArmResult;
  delta: {
    recoveryRatePoints: number;
    grossRecoveredMinor: number;
    netRecoveredMinor: number;
    incrementalRecoveredMinor: number;
    attemptsSaved: number;
    contactsSaved: number;
    unnecessaryContactsSaved: number;
  };
}

export interface ArmResult {
  arm: string;
  population: number;
  recoveredCount: number;
  recoveryRatePercent: number;
  recoveryRateLowerCI: number;
  recoveryRateUpperCI: number;
  grossRecoveredMinor: number;
  netRecoveredMinor: number;
  totalAttempts: number;
  avgAttempts: number;
  totalContacts: number;
  avgContacts: number;
  avgTimeToRecoveryHours: number;
  interventionCostMinor: number;
  policyBlocks: number;
  unnecessaryContacts: number;
}

export interface PolicySet {
  id: string;
  name: string;
  active: boolean;
  maxRetries: number;
  maxContactAttempts: number;
  maxDiscountPercent: number;
  recoveryWindowHours: number;
  minimumRecoverableAmount: number;
  contactCooldownHours: number;
  requireApprovalAboveAmount: number;
  allowWhatsApp: boolean;
  allowEmail: boolean;
  allowSms: boolean;
  allowDiscounts: boolean;
  allowPaymentLinks: boolean;
  allowDelayedRetry: boolean;
  version: number;
}

export interface Approval {
  id: string;
  incidentId: string;
  requestedBy: string | null;
  proposal: {
    strategy?: string;
    amountMinor?: number;
    failureCategory?: string;
    confidence?: number;
    lowConfidence?: boolean;
    requestedAt?: string;
  };
  status: string;
  decidedBy: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
  createdAt: string;
}

export interface IntegrationView {
  id: string;
  provider: string;
  mode: string;
  active: boolean;
  status: string;
  keyIdMasked: string | null;
  webhookSecretConfigured: boolean;
  modeLabel: string;
  createdAt: string;
}

export interface SystemHealth {
  webhookReceived: number;
  webhookPending: number;
  outboxPending: number;
  deadLetterQueue: number;
  aiService: { enabled: boolean; baseUrl: string; mode: string };
  provider: { name: string; mode: string; mock: boolean };
  eventDispatchMode: string;
  temporalEnabled: boolean;
  demoMode: boolean;
  metrics: Record<string, number>;
}

export interface TrendPoint {
  periodStart: string;
  metrics: {
    revenueAtRiskMinor?: number;
    revenueRecoveredMinor?: number;
    recoveredCount?: number;
  };
}
