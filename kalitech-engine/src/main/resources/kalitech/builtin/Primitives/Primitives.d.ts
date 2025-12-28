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
    [key: string]: unknown;
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

/**
 * Wrapper returned by Primitives.js (Proxy) that adds physics sugar.
 * NOTE: All methods are best-effort (can no-op if body isn't linked yet),
 * but typings expose them for IDE/TS help.
 */
export interface PrimitiveHandle extends SurfaceHandle {
    /**
     * Markers used by wrapper.
     */
    __isPrimitiveWrapper?: true;
    __surface?: SurfaceHandle;

    /**
     * Physics sugar (provided by wrapper).
     */
    applyImpulse?(v: Vec3): void;
    applyCentralForce?(v: Vec3): void;

    /**
     * Getter/setter pattern:
     *  - g.velocity() -> Vec3 | undefined
     *  - g.velocity(v) -> void
     */
    velocity?(): Vec3 | undefined;
    velocity?(v: Vec3): void;

    position?(): Vec3 | undefined;
    position?(p: Vec3): void;

    teleport?(p: Vec3): void;

    lockRotation?(lock: boolean): void;
}

/**
 * Fluent builder interface.
 * Produces final cfg and can create PrimitiveHandle.
 */
export interface PrimitiveBuilder<TCfg extends PrimitiveCfgBase = PrimitiveCfgBase> {
    name(v: string): this;

    pos(x: number, y: number, z: number): this;
    pos(v: Vec3): this;

    rot(v: Vec3 | Vec4): this;
    scale(v: Vec3 | number): this;

    material(m: MaterialCfg | unknown): this;

    /**
     * Physics sugar. Writes to cfg.physics.
     */
    physics(mass?: number, opts?: Omit<PhysicsCfg, "mass"> & Record<string, unknown>): this;
    mass(v: number): this;
    lockRotation(v: boolean): this;
    kinematic(v: boolean): this;

    /**
     * Common geometry sugar.
     * Some fields may be ignored depending on primitive type.
     */
    size(v: number): this;
    radius(v: number): this;
    height(v: number): this;

    attach(v?: boolean): this;

    /**
     * Expose assembled cfg (copy).
     */
    cfg(): TCfg;

    /**
     * Finalize: call primitives.create(cfg)
     */
    create(): PrimitiveHandle;
}

export interface PrimitivesApi {
    // Declarative API (existing)
    create(cfg: PrimitiveCfgBase): PrimitiveHandle;

    box(cfg?: BoxCfg): PrimitiveHandle;
    cube(cfg?: BoxCfg): PrimitiveHandle;
    sphere(cfg?: SphereCfg): PrimitiveHandle;
    cylinder(cfg?: CylinderCfg): PrimitiveHandle;
    capsule(cfg?: CapsuleCfg): PrimitiveHandle;

    many(list: PrimitiveCfgBase[]): PrimitiveHandle[];

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

    // NEW: fluent builder API
    builder<TCfg extends PrimitiveCfgBase = PrimitiveCfgBase>(type: PrimitiveType | string): PrimitiveBuilder<TCfg>;

    /**
     * Sugar aliases (if you add them in Primitives.js export):
     */
    box$(): PrimitiveBuilder<BoxCfg>;
    cube$(): PrimitiveBuilder<BoxCfg>;
    sphere$(): PrimitiveBuilder<SphereCfg>;
    cylinder$(): PrimitiveBuilder<CylinderCfg>;
    capsule$(): PrimitiveBuilder<CapsuleCfg>;
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