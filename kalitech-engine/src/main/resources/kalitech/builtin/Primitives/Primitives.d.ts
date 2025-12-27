// Author: Calista Verner

/**
 * Kalitech builtin primitives (TypeScript declarations).
 *
 * Works for:
 * - require("@builtin/Primitives")(K) -> PrimitivesApi
 * - globalThis.primitives -> PrimitivesApi (when exposeGlobals=true)
 */

export type Vec3 =
    | [number, number, number]
    | { x: number; y: number; z: number };

export type Vec4 =
    | [number, number, number, number]
    | { x: number; y: number; z: number; w: number };

export type MaterialParams = Record<string, unknown>;

export interface MaterialCfg {
    def: string;
    params?: MaterialParams;
}

export interface PhysicsDampingCfg {
    linear?: number;
    angular?: number;
}

export type ColliderType =
    | "box"
    | "sphere"
    | "capsule"
    | "cylinder"
    | "mesh"
    | "dynamicMesh";

export interface ColliderCfg {
    type: ColliderType;
    halfExtents?: [number, number, number] | Vec3;
    radius?: number;
    height?: number;
}

export interface PhysicsCfg {
    enabled?: boolean;
    mass?: number;
    kinematic?: boolean;
    lockRotation?: boolean;
    friction?: number;
    restitution?: number;
    damping?: PhysicsDampingCfg;
    collider?: ColliderCfg | Record<string, unknown>;
}

export type PrimitiveType = "box" | "sphere" | "cylinder" | "capsule";

export interface PrimitiveCfgBase {
    type?: PrimitiveType | string;
    name?: string;

    pos?: Vec3;
    rot?: Vec3 | Vec4;
    scale?: Vec3 | number;

    attach?: boolean;

    material?: MaterialCfg | unknown;
    physics?: PhysicsCfg;

    [key: string]: unknown;
}

export interface BoxCfg extends PrimitiveCfgBase {
    type?: "box";
    size?: number;
    hx?: number;
    hy?: number;
    hz?: number;
}

export interface SphereCfg extends PrimitiveCfgBase {
    type?: "sphere";
    radius?: number;
    zSamples?: number;
    radialSamples?: number;
}

export interface CylinderCfg extends PrimitiveCfgBase {
    type?: "cylinder";
    radius?: number;
    height?: number;
    axisSamples?: number;
    radialSamples?: number;
    closed?: boolean;
}

export interface CapsuleCfg extends PrimitiveCfgBase {
    type?: "capsule";
    radius?: number;
    height?: number;
    axisSamples?: number;
    radialSamples?: number;
    zSamples?: number;
}

/**
 * Minimal SurfaceHandle shape used by JS.
 * Your real handle may expose more members; keep it permissive.
 */
export type IdLike = number | (() => number);
export type StrLike = string | (() => string);

export interface SurfaceHandle {
    id?: IdLike;
    kind?: StrLike;
    [key: string]: unknown;
}

export interface PrimitivesApi {
    create(cfg: PrimitiveCfgBase): SurfaceHandle;

    box(cfg?: BoxCfg): SurfaceHandle;
    cube(cfg?: BoxCfg): SurfaceHandle;
    sphere(cfg?: SphereCfg): SurfaceHandle;
    cylinder(cfg?: CylinderCfg): SurfaceHandle;
    capsule(cfg?: CapsuleCfg): SurfaceHandle;

    many(list: PrimitiveCfgBase[]): SurfaceHandle[];

    unshadedColor(rgba?: [number, number, number, number]): MaterialCfg;

    physics(
        mass?: number,
        opts?: {
            enabled?: boolean;
            lockRotation?: boolean;
            kinematic?: boolean;
            friction?: number;
            restitution?: number;
            damping?: PhysicsDampingCfg;
            collider?: ColliderCfg | Record<string, unknown>;
            [key: string]: unknown;
        }
    ): PhysicsCfg;
}

/**
 * Builtin module id in Kalitech runtime.
 * Example: const prim = require("@builtin/Primitives")(__kalitech);
 */
declare const _factory: (K: any) => PrimitivesApi;
export default _factory;

/**
 * Global, provided by bootstrap when exposeGlobals=true.
 */
declare global {
    const primitives: PrimitivesApi;
}