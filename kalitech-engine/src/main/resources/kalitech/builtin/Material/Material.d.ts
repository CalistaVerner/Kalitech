// Author: Calista Verner

/**
 * Kalitech builtin materials registry (TypeScript declarations).
 *
 * Works for:
 * - require("@builtin/MaterialsRegistry")(K) -> MaterialsRegistryApi
 * - globalThis.materials -> MaterialsRegistryApi (when exposeGlobals=true)
 * - globalThis.M -> MaterialsRegistryApi (alias)
 */

export type MaterialParams = Record<string, unknown>;
export type MaterialScales = Record<string, unknown>;

export interface MaterialCfg {
    def: string;
    params?: MaterialParams;
    scales?: MaterialScales;
}

export interface MaterialOverrides {
    params?: MaterialParams;
    scales?: MaterialScales;
}

/**
 * Host material handle returned by engine.material().create(cfg).
 * Keep permissive to match host object shape across versions.
 */
export interface MaterialHandle {
    id?: number | (() => number);
    __material?: () => unknown;
    [key: string]: unknown;
}

/**
 * Host material object (jME Material).
 * In JS you usually treat it as opaque host object.
 */
export type HostMaterial = unknown;

export interface MaterialsRegistryApi {
    /**
     * Returns a cached MaterialHandle for a named material definition.
     * If overrides are provided, caching is bypassed.
     */
    getHandle(name: string, overrides?: MaterialOverrides | null): MaterialHandle;

    /**
     * Returns a cached host Material for a named material definition.
     * If overrides are provided, caching is bypassed.
     */
    getMaterial(name: string, overrides?: MaterialOverrides | null): HostMaterial;

    /**
     * Clears caches and forces reloading of materials JSON on next access.
     */
    reload(): boolean;

    /**
     * Returns names of known material definitions.
     */
    keys(): string[];
}

/**
 * Builtin module factory.
 */
declare const _factory: (K: any) => MaterialsRegistryApi;
export default _factory;

/**
 * Globals provided by bootstrap when exposeGlobals=true.
 */
declare global {
    const materials: MaterialsRegistryApi;
    const M: MaterialsRegistryApi;
}