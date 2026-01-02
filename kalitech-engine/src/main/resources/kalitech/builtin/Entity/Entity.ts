// FILE: resources/kalitech/builtin/Entity.d.ts
// Author: Calista Verner

declare namespace KalitechEntity {
    // ------------------------------------
    // Shared primitives
    // ------------------------------------

    export type Vec3 =
        | [number, number, number]
        | { x: number; y: number; z: number }
        | { 0: number; 1: number; 2: number };

    export interface BuiltinMeta {
        name: string;
        globalName: "ENT";
        version: string;
        description?: string;
        engineMin?: string;
    }

    // ------------------------------------
    // Handles / refs (Graal-friendly)
    // ------------------------------------

    export type EntityRef =
        | number
        | { id?: number; entityId?: number }
        | unknown;

    export type SurfaceRef =
        | number
        | { id?: number; surfaceId?: number }
        | unknown;

    export type BodyRef =
        | number
        | { id?: number; bodyId?: number }
        | unknown;

    // ------------------------------------
    // Surface cfg (passed into engine.mesh().create)
    // We keep it flexible: depends on your Mesh builtin / engine surface system.
    // ------------------------------------

    export type SurfaceType = "capsule" | "box" | "sphere" | string;

    export interface SurfacePhysicsInlineCfg {
        mass?: number;
        lockRotation?: boolean;
        [k: string]: unknown;
    }

    export interface SurfaceCfg {
        type: SurfaceType;
        name?: string;

        // common dims (used by presets)
        radius?: number;
        height?: number;
        size?: number;

        pos?: Vec3;

        /**
         * ENT uses engine.mesh().create(cfg) and then may attach surface to entity.
         * This 'attach' is passed into mesh().create in your code; keep it optional.
         */
        attach?: boolean;

        /**
         * Inline physics hint for surface creation (engine-specific).
         * ENT itself doesn't use it directly beyond passing to mesh().create.
         */
        physics?: SurfacePhysicsInlineCfg;

        [k: string]: unknown;
    }

    // ------------------------------------
    // Body cfg (passed into engine.physics().body)
    // Note: ENT can derive collider from surface.type if collider missing.
    // ------------------------------------

    export interface DampingCfg {
        linear?: number;
        angular?: number;
    }

    export type ColliderType = "box" | "sphere" | "capsule" | "cylinder" | "mesh" | "dynamicMesh" | string;

    export interface ColliderCfg {
        type: ColliderType;
        // ENT sometimes uses "size" for box; host may accept halfExtents too
        size?: number;
        halfExtents?: Vec3;
        radius?: number;
        height?: number;
        [k: string]: unknown;
    }

    export interface BodyCfg {
        /**
         * Optional in ENT.create: if omitted and surface exists, ENT will set it to ctx.surface.
         */
        surface?: SurfaceRef;

        mass?: number;
        friction?: number;
        restitution?: number;
        damping?: DampingCfg;

        kinematic?: boolean;
        lockRotation?: boolean;

        collider?: ColliderCfg;

        [k: string]: unknown;
    }

    // ------------------------------------
    // Components
    // ------------------------------------

    export interface ComponentBuildContext {
        entityId: number;

        surface: unknown | null;
        body: unknown | null;

        surfaceId: number;
        bodyId: number;

        cfg: EntityCreateCfg;
    }

    export type ComponentValue = unknown;

    export type ComponentBuilder = (ctx: ComponentBuildContext) => ComponentValue;

    export type ComponentsMap = Record<string, ComponentValue | ComponentBuilder>;

    // ------------------------------------
    // Entity create cfg
    // ------------------------------------

    export interface EntityCreateCfg {
        name?: string;

        surface?: SurfaceCfg | null;
        body?: BodyCfg | null;

        /**
         * Whether ENT should attach the created surface to entity via engine.surface().attach(surface, entityId).
         * Default in JS implementation: true.
         */
        attachSurface?: boolean;

        components?: ComponentsMap;

        debug?: boolean;

        [k: string]: unknown;
    }

    // ------------------------------------
    // Entity handle returned by ENT.create / builder.create()
    // ------------------------------------

    export interface EntityHandle {
        // stored primitive ids
        entityId: number;
        surfaceId: number;
        bodyId: number;

        // raw engine handles (types depend on Java side)
        surface: unknown | null;
        body: unknown | null;

        // id helpers
        id(): number;
        surfaceHandleId(): number;
        bodyHandleId(): number;

        /**
         * Preferred physics access: returns PHYS.ref(bodyId) if available,
         * otherwise an id-based wrapper around engine.physics().
         */
        bodyRef(): {
            id(): number;
            position(v?: Vec3): unknown;
            warp(v: Vec3): unknown;
            velocity(v?: Vec3): unknown;
            yaw(yawRad: number): unknown;
            applyImpulse(imp: Vec3): unknown;
            applyCentralForce(f: Vec3): unknown;
            applyTorque(t: Vec3): unknown;
            angularVelocity(v?: Vec3): unknown;
            clearForces(): unknown;
            lockRotation(lock: boolean): unknown;
            collisionGroups(group: number, mask: number): unknown;
            remove(): unknown;
        };

        // transforms
        position(): unknown;
        position(v: Vec3): unknown;
        warp(pos: Vec3): unknown;
        velocity(): unknown;
        velocity(v: Vec3): unknown;
        yaw(yawRad: number): unknown;

        // forces
        applyImpulse(imp: Vec3): unknown;
        applyCentralForce(force: Vec3): unknown;
        applyTorque(torque: Vec3): unknown;
        angularVelocity(): unknown;
        angularVelocity(v: Vec3): unknown;
        clearForces(): unknown;

        // flags / collision
        lockRotation(lock?: boolean): unknown;
        collisionGroups(group: number, mask: number): unknown;

        // queries
        raycast(cfg: unknown): unknown;
        raycastDown(distance?: number, startOffsetY?: number): unknown;

        /**
         * Set one component on ECS entity: engine.entity().setComponent(entityId, name, data)
         */
        component(name: string, data: unknown): this;

        /**
         * Set multiple components at once.
         * - components(map)
         * - components(builderFn)
         */
        components(map: Record<string, unknown>): this;
        components(builder: (ctx: {
            entityId: number;
            surface: unknown | null;
            body: unknown | null;
            surfaceId: number;
            bodyId: number;
        }) => Record<string, unknown>): this;

        /**
         * Destroy entity: runs custom destroyers, removes physics body if any,
         * clears internal refs/ids.
         */
        destroy(): void;

        // JS coercion helpers (for Graal)
        valueOf(): number;
        toString(): string;
    }

    // ------------------------------------
    // Builder (ENT.$(...))
    // ------------------------------------

    export interface EntBuilder {
        merge(cfg: Partial<EntityCreateCfg>): this;

        name(v: string): this;
        debug(v?: boolean): this;

        surface(v: Partial<SurfaceCfg>): this;
        body(v: Partial<BodyCfg>): this;

        attachSurface(v?: boolean): this;

        component(name: string, dataOrFn: ComponentValue | ComponentBuilder): this;

        create(): EntityHandle;
    }

    // ------------------------------------
    // ENT API (builtin)
    // ------------------------------------

    export interface EntityApi {
        // creation
        create(cfg: EntityCreateCfg): EntityHandle;

        // builder
        $(presetName?: string): EntBuilder;

        player$(cfg?: Partial<EntityCreateCfg>): EntBuilder;
        capsule$(cfg?: Partial<EntityCreateCfg>): EntBuilder;
        box$(cfg?: Partial<EntityCreateCfg>): EntBuilder;
        sphere$(cfg?: Partial<EntityCreateCfg>): EntBuilder;

        // config
        preset(name: string, cfg: Partial<EntityCreateCfg>): this;
        bodyDefaults(cfg: Partial<BodyCfg>): this;
        presets(): string[];

        // utility
        idOf(h: unknown, kind?: "body" | "surface" | "entity" | string): number;
    }
}

declare function EntityFactory(engine: unknown, K?: unknown): KalitechEntity.EntityApi;

declare namespace EntityFactory {
    const META: KalitechEntity.BuiltinMeta;
}

export = EntityFactory;

declare global {
    const ENT: KalitechEntity.EntityApi;
}