## ADDED Requirements

### Requirement: TypeScript build passes
Running `npm run build` in `metadata-registry-ui` SHALL complete with zero TypeScript errors.

#### Scenario: CI runs the build
- **WHEN** the build command is executed
- **THEN** `vue-tsc --noEmit` reports no errors and `vite build` succeeds

### Requirement: Existing table prop type errors are fixed
All `*List.vue` files that use `el-table-column prop="..."` with TS1127/TS1005 errors SHALL be fixed by using the `("x.y" as any)` pattern or equivalent typed syntax.

#### Scenario: Build includes DataElementConceptList.vue
- **WHEN** `vue-tsc` processes `src/views/data-element-concept/DataElementConceptList.vue`
- **THEN** no “Invalid character” or “: expected” errors are reported

### Requirement: Auth user type is complete
`src/stores/auth.ts` SHALL use a user type that includes all fields returned by the backend (e.g., `emergencyLogin`, `forceChangePassword`, `lastLoginAt`, `sysRole`, `isRegistrar`).

#### Scenario: Build processes auth.ts
- **WHEN** `vue-tsc` processes `src/stores/auth.ts`
- **THEN** no property-missing errors are reported
