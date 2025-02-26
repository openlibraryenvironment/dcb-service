package org.olf.dcb.core.model;

/** Host ILS adapters must infer from local rules and procedures what the canonical loan policy is with respect to consortial lending */
public enum DerivedLoanPolicy {
  NO_LEND,
  UNKNOWN,
  SHORT_LOAN,
  REFERENCE_ONLY,
  LOCAL_ONLY,
  GENERAL
}

