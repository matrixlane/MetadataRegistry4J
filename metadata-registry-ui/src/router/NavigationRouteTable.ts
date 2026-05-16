/**
 * NavigationRouteTable
 *
 * A centralized route registry that decouples sidebar menu items from
 * hard-coded route paths.  It provides:
 *  - Registration of known routes with metadata (title, icon, roles, status)
 *  - Name-based resolution with automatic fallback to the PlaceholderPage
 *    when a route is registered but not yet implemented
 *  - Role-based filtering for sidebar menu generation
 *
 * Inspired by the trio review decision to introduce a route table (方案 B)
 * instead of scattering route strings throughout templates.
 */

import type { Component } from 'vue';
import { markRaw } from 'vue';

// ── Types ──────────────────────────────────────────────────────────

export type RouteStatus = 'implemented' | 'planned' | 'deprecated';

export interface RouteRegistration {
  /** Unique route key used in menu items and NavigationRouteTable lookups */
  key: string;
  /** Vue Router path (e.g. '/value-domains') */
  path: string;
  /** Display name (shown in sidebar & placeholder page) */
  title: string;
  /** Implementation status */
  status: RouteStatus;
  /** Minimum role required to see this route (undefined = everyone) */
  minRole?: 'ADMIN' | 'REGISTRAR' | 'VIEWER';
  /** Raw import of the Vue component (lazy-loaded via defineAsyncComponent) */
  component?: () => Promise<{ default: Component }>;
  /** Icon component reference (Element Plus icon) */
  icon?: Component;
}

// ── The Registry ───────────────────────────────────────────────────

class NavigationRouteTableClass {
  private registry = new Map<string, RouteRegistration>();

  /**
   * Register one or more routes.  Calling register twice with the same key
   * overwrites the previous registration (enables gradual migration).
   */
  register(...entries: RouteRegistration[]): void {
    for (const entry of entries) {
      this.registry.set(entry.key, entry);
    }
  }

  /**
   * Look up a registration by key.  Returns `undefined` when the key is
   * completely unknown (the caller should treat this as a 404 / broken menu).
   */
  get(key: string): RouteRegistration | undefined {
    return this.registry.get(key);
  }

  /**
   * Return all registered routes, optionally filtered by role.
   */
  getAll(minRole?: 'ADMIN' | 'REGISTRAR' | 'VIEWER'): RouteRegistration[] {
    const entries = Array.from(this.registry.values());
    if (!minRole) return entries;
    const roleOrder: Array<'ADMIN' | 'REGISTRAR' | 'VIEWER'> = [
      'VIEWER',
      'REGISTRAR',
      'ADMIN',
    ];
    const userLevel = roleOrder.indexOf(minRole);
    return entries.filter((r) => {
      if (!r.minRole) return true;
      const requiredLevel = roleOrder.indexOf(r.minRole);
      return userLevel >= requiredLevel;
    });
  }

  /**
   * Check if a given route key is registered as `implemented`.
   */
  isImplemented(key: string): boolean {
    const entry = this.registry.get(key);
    return entry?.status === 'implemented';
  }

  /**
   * Resolve a route key to the appropriate component.
   *
   * - implemented routes → the actual (cached) async component
   * - planned routes     → PlaceholderPage
   * - unknown keys       → undefined (caller handles 404)
   */
  resolveComponent(key: string): (() => Promise<{ default: Component }>) | undefined {
    const entry = this.registry.get(key);
    if (!entry) return undefined;
    if (entry.status === 'implemented' && entry.component) {
      return entry.component;
    }
    // planned / deprecated → show placeholder
    return () =>
      import('../views/placeholder/PlaceholderPage.vue').then((m) => ({
        default: m.default,
      }));
  }

  /**
   * Number of registered entries.
   */
  get size(): number {
    return this.registry.size;
  }

  /** Clear all (useful in tests) */
  _clear(): void {
    this.registry.clear();
  }
}

/** Singleton instance shared across the app */
export const routeTable = new NavigationRouteTableClass();

// ── Helper: build an item record for el-menu from a registration ──

import { h } from 'vue';

/**
 * Create a Vue Router route config object (for `children` array) for a
 * registered route key.  If the route is `planned` the component resolves to
 * PlaceholderPage automatically.
 */
export function buildRouteConfig(
  key: string,
): { path: string; name: string; component: () => Promise<{ default: Component }>; meta: Record<string, unknown> } | null {
  const reg = routeTable.get(key);
  if (!reg) return null;
  const component = routeTable.resolveComponent(key);
  if (!component) return null;
  return {
    path: reg.path.replace(/^\//, ''),
    name: reg.key,
    component,
    meta: { title: reg.title, minRole: reg.minRole, routeKey: key },
  };
}
