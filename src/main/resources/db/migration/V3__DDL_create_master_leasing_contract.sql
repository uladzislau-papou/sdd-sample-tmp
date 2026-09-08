-- UC07 — CreateMasterLeasingContract.
--
-- Two tables plus an element collection, mirroring the JCM data model page's
-- MASTER_LEASING_CONTRACT and MLC_CONFIGURATION entities.
--
-- Column names follow the source, so the model stays recognisable to whoever wrote it, even
-- where the domain type is named differently: kuv_joint_liability is groupJointLiability in
-- code, because coding-style.definition.md § 4.3 forbids carrying a German abbreviation into
-- an identifier (PD-00).
--
-- Nullability follows PD-01, which is a decision of ours and not a sourced rule: the data
-- model page lists every field and states no optionality anywhere. Note the direction of the
-- risk — making an optional column NOT NULL later breaks existing rows, so PD-01 deliberately
-- errs toward fewer mandatory fields.
--
-- No unique constraint on employer_id: PD-05 permits an employer to hold several active
-- contracts. If that is overturned the rule needs a partial unique index here rather than an
-- aggregate check, because project.definition.md's last-write-wins non-goal means two
-- concurrent creations would both read "no existing contract" and both succeed.
--
-- No optimistic-locking version column, per project.definition.md's non-goals.

CREATE TABLE master_leasing_contract (
    id              VARCHAR(36)  NOT NULL,
    employer_id     VARCHAR(255) NOT NULL,
    lessor_id       VARCHAR(255) NOT NULL,
    partner_number  VARCHAR(255) NULL,
    owner           VARCHAR(255) NULL,
    status          VARCHAR(50)  NOT NULL,
    creation_time   TIMESTAMP    NOT NULL,
    activation_date TIMESTAMP    NOT NULL,
    mlc_config_id   VARCHAR(36)  NOT NULL,
    CONSTRAINT pk_master_leasing_contract PRIMARY KEY (id)
);

-- One row per terms version. The contract points at the current one through mlc_config_id,
-- which the source defines as "the current configuration version" — so superseding the terms
-- inserts a row here and repoints the contract, rather than updating in place.
CREATE TABLE mlc_configuration (
    id                            VARCHAR(36)    NOT NULL,
    version                       INT            NOT NULL,
    credit_limit                  NUMERIC(19, 4) NOT NULL,
    contract_type                 VARCHAR(255)   NOT NULL,
    currency                      VARCHAR(3)     NOT NULL,
    eligible_employees            INT            NOT NULL,
    sales_channel                 VARCHAR(255)   NULL,
    kuv_joint_liability           BOOLEAN        NOT NULL,
    return_quota_percentage       NUMERIC(5, 2)  NULL,
    early_claim_fee_percentage    NUMERIC(5, 2)  NULL,
    early_claim_window_months     INT            NULL,
    notice_period_rule            VARCHAR(255)   NULL,
    payment_terms                 VARCHAR(255)   NULL,
    price_range_min               NUMERIC(19, 4) NULL,
    price_range_max               NUMERIC(19, 4) NULL,
    calculation_basis             VARCHAR(255)   NULL,
    service_package_version       INT            NULL,
    categories_editable_in_portal BOOLEAN        NOT NULL,
    CONSTRAINT pk_mlc_configuration PRIMARY KEY (id)
);

-- servicePackageOptions is a list of tier names. A separate table rather than a delimited
-- column: a delimiter would add an invariant ("no tier name contains a comma") that is a
-- persistence concern leaking into the domain, and it cannot be partially forgotten the way a
-- split/join pair can.
CREATE TABLE mlc_configuration_service_package (
    mlc_configuration_id VARCHAR(36)  NOT NULL,
    service_package      VARCHAR(255) NOT NULL,
    CONSTRAINT fk_mlc_config_service_package FOREIGN KEY (mlc_configuration_id)
        REFERENCES mlc_configuration (id)
);

CREATE INDEX idx_mlc_config_service_package ON mlc_configuration_service_package (mlc_configuration_id);
CREATE INDEX idx_master_leasing_contract_employer ON master_leasing_contract (employer_id);
