ALTER TABLE naming_convention ADD semantic_rule NVARCHAR(MAX) NULL;
ALTER TABLE naming_convention ADD syntactic_rule NVARCHAR(MAX) NULL;
ALTER TABLE naming_convention ADD lexical_rule NVARCHAR(MAX) NULL;
ALTER TABLE naming_convention ADD rules_json NVARCHAR(MAX) NULL;