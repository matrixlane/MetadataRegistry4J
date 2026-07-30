## ADDED Requirements

### Requirement: NamingConvention stores standard rule attributes
The `NamingConvention` entity SHALL persist the five standard attributes defined in ISO/IEC 11179-3:2023 8.4.4: `scopeRule` (1), `authorityRule` (0..1), `semanticRule` (0..1), `syntacticRule` (0..1), and `lexicalRule` (0..1).

#### Scenario: API creates a naming convention with standard attributes
- **WHEN** a client sends a `POST /api/v1/naming-conventions` request with `scopeRule`, `lexicalRule`, and `syntacticRule`
- **THEN** the entity is persisted with those values and returned in subsequent GET responses

### Requirement: Naming convention engine can execute rules defined in standard attributes
When `rulesJson` is empty or absent, the `NamingConventionEngine` SHALL parse the standard attributes into executable rules and validate submitted designations against them.

#### Scenario: Designation is validated against lexical rule only
- **WHEN** a naming convention has no `rulesJson` but has a `lexicalRule` requiring camelCase, and a designation violating that rule is submitted
- **THEN** the engine returns a validation error for the lexical rule
