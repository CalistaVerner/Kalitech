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

/**
 * Full override shape (explicit).
 */
export interface MaterialOverridesFull {
    params?: MaterialParams | null;
    scales?: MaterialScales | null;
}

/**
 * Short override shape:
 * M.get("box", { Color:[1,0,0,1], Roughness:0.5 })
 * Treated as params overrides.
 */
export type MaterialOverridesShort = MaterialParams;

/**
 * Overrides accepted by the registry:
 * - null/undefined
 * - { params?, scales? } (full form)
 * - { ...params } (short form, treated as params)
 */
export type MaterialOverrides = MaterialOverridesFull | MaterialOverridesShort | null | undefined;

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

/**
 * Preset function returned by M.preset(...):
 * - callable -> returns HostMaterial
 * - .handle() -> returns MaterialHandle
 * - carries some metadata for debugging/tooling
 */
export interface MaterialPresetFn {
    (overrides?: MaterialOverrides): HostMaterial;
    handle: (overrides?: MaterialOverrides) => MaterialHandle;

    /** Name of base material (best-effort) */
    name?: string;

    /** Original preset overrides (best-effort) */
    overrides?: MaterialOverridesFull | null;
}

/**
 * Optional runtime configuration for registry behavior.
 */
export interface MaterialsRegistryConfig {
    /**
     * Enable caching for override-created materials/handles.
     * Default: true
     */
    overrideCache?: boolean;

    /**
     * Max number of override cache entries (simple FIFO).
     * Default: 256
     */
    overrideCacheMax?: number;
}

export interface MaterialsRegistryApi {
    /**
     * Returns a cached MaterialHandle for a named material definition.
     * With overrides: may be cached depending on configure({overrideCache...}).
     */
    getHandle(name: string, overrides?: MaterialOverrides): MaterialHandle;

    /**
     * Returns a cached host Material for a named material definition.
     * With overrides: may be cached depending on configure({overrideCache...}).
     */
    getMaterial(name: string, overrides?: MaterialOverrides): HostMaterial;

    /**
     * Sugar: default getter, returns HostMaterial.
     * Alias of getMaterial(name, overrides).
     */
    get(name: string, overrides?: MaterialOverrides): HostMaterial;

    /**
     * Sugar: returns MaterialHandle.
     * Alias of getHandle(name, overrides).
     */
    handle(name: string, overrides?: MaterialOverrides): MaterialHandle;

    /**
     * Create reusable material preset (callable factory).
     *
     * Example:
     * const RedBox = M.preset("box", { Color:[1,0,0,1] });
     * geom.setMaterial(RedBox());
     * const h = RedBox.handle();
     */
    preset(name: string, overrides?: MaterialOverrides): MaterialPresetFn;

    /**
     * Convenience for params-only overrides:
     * M.params("box", { Color:[...] })
     */
    params(name: string, params?: MaterialParams | null): HostMaterial;

    /**
     * Tune registry behavior (optional).
     */
    configure(cfg: MaterialsRegistryConfig): this;

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