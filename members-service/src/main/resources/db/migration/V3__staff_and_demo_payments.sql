ALTER TABLE member_accounts DROP CONSTRAINT member_accounts_role_check;
ALTER TABLE member_accounts ADD CHECK (role IN ('MEMBER', 'ADMIN', 'TRAINER'));

CREATE TABLE demo_payment_methods (
    member_id UUID PRIMARY KEY REFERENCES member_accounts(id)
);

CREATE TABLE demo_payments (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES member_accounts(id),
    membership_id UUID NOT NULL UNIQUE REFERENCES member_memberships(id),
    method VARCHAR(16) NOT NULL CHECK (method IN ('CARD', 'SBP')),
    amount_rub INTEGER NOT NULL CHECK (amount_rub >= 0),
    paid_at TIMESTAMPTZ NOT NULL
);
