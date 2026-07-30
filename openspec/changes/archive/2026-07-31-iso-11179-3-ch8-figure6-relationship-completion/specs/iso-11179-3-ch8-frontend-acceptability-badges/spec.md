## ADDED Requirements

### Requirement: Acceptability is visible in relationship tables
Each relationship table in `MetadataDetailView` SHALL render an `Acceptability` badge per row using the color semantics: `PREFERRED`=success, `ADMITTED`=info, `DEPRECATED`/`SUPERSEDED`/`OBSOLETE`=danger.

#### Scenario: User views a relationship table with multiple acceptability ratings
- **WHEN** the relationship table contains rows with `PREFERRED` and `DEPRECATED` ratings
- **THEN** the `PREFERRED` row shows a green badge and the `DEPRECATED` row shows a red badge

### Requirement: Preferred designation is highlighted in list and header
The `BrowserView` list and the `MetadataDetailView` header SHALL display a `PREFERRED` badge for the first preferred designation of each item.

#### Scenario: User browses the metadata dictionary
- **WHEN** a metadata item has a designation with `Acceptability.PREFERRED`
- **THEN** the list row and detail header show a 「首选」 badge next to the item name
