// File: resources/kalitech/builtin/Physics.d.ts
// Author: Calista Verner

declare namespace KalitechPhysics {
    // ------------------------------------
    // Basic types
    // ------------------------------------

    export type Vec3 =
        | [number, number, number]
        | { x: number; y: number; z: number };

    export interface Vec3LikeObject {
        x: number | (() => number);
        y: number | (() => number);
        z: number | (() => number);
    }

    export type Vec3Like = Vec3 | Vec3LikeObject;

    export type BodyRef =
        | number
        | PhysicsBodyHandle
        | { id?: number; bodyId?: number }
        | unknown;

    export type SurfaceRef =
        | number
        | { id?: number; surfaceId?: number }
        | unknown;

    export interface DampingCfg {
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

    export interface ColliderBoxCfg {
        type: "box";
        halfExtents: Vec3Like;
    }

    export interface ColliderSphereCfg {
        type: "sphere";
        radius: number;
    }

    export interface ColliderCapsuleCfg {
        type: "capsule";
        radius: number;
        height: number;
    }

    export interface ColliderCylinderCfg {
        type: "cylinder";
        radius?: number;
        height?: number;
        halfExtents?: Vec3Like;
    }

    export interface ColliderMeshCfg {
        type: "mesh";
    }

    export interface ColliderDynamicMeshCfg {
        type: "dynamicMesh";
    }

    export type ColliderCfg =
        | ColliderBoxCfg
        | ColliderSphereCfg
        | ColliderCapsuleCfg
        | ColliderCylinderCfg
        | ColliderMeshCfg
        | ColliderDynamicMeshCfg
        | Record<string, unknown>;

    export interface PhysicsBodyCfg {
        surface: SurfaceRef;
        mass?: number;
        friction?: number;
        restitution?: number;
        damping?: DampingCfg;
        kinematic?: boolean;
        lockRotation?: boolean;
        collider?: ColliderCfg;
    }

    export interface RaycastCfg {
        from: Vec3Like;
        to: Vec3Like;
    }

    export interface RaycastExCfg extends RaycastCfg {
        // future-proof / optional host flags
        ignoreBody?: BodyRef;
        mask?: number;
        group?: number;
        [k: string]: unknown;
    }

    export interface PhysicsRayHit {
        hit?: boolean; // some builds may expose hit boolean
        bodyId: number;
        surfaceId: number;
        fraction?: number;
        distance?: number;
        point: { x: number; y: number; z: number };
        normal: { x: number; y: number; z: number };
        [k: string]: unknown;
    }

    export interface PhysicsBodyHandle {
        id: number;
        surfaceId: number;
        [k: string]: unknown;
    }

    // ------------------------------------
    // Helpers
    // ------------------------------------

    export interface PhysicsColliderHelpers {
        box(halfExtents: Vec3Like): ColliderBoxCfg;
        sphere(radius: number): ColliderSphereCfg;
        capsule(radius: number, height: number): ColliderCapsuleCfg;
        cylinder(radius: number, height: number): ColliderCylinderCfg;
        mesh(): ColliderMeshCfg;
        dynamicMesh(): ColliderDynamicMeshCfg;
    }

    // Wrapper returned by PHYS.ref(...)
    export interface PhysicsBodyRef {
        id(): number;

        // transforms
        position(): { x: number; y: number; z: number };
        position(pos: Vec3Like): void;

        warp(pos: Vec3Like): void;

        velocity(): { x: number; y: number; z: number };
        velocity(vel: Vec3Like): void;

        yaw(yawRad: number): void;

        // forces
        applyImpulse(impulse: Vec3Like): void;
        applyCentralForce(force: Vec3Like): void;
        applyTorque(torque: Vec3Like): void;

        angularVelocity(): { x: number; y: number; z: number };
        angularVelocity(v: Vec3Like): void;

        clearForces(): void;

        // flags
        lockRotation(lock: boolean): void;
        collisionGroups(group: number, mask: number): void;

        // world queries (convenience passthrough)
        raycast(cfg: RaycastCfg): PhysicsRayHit | null;
        raycastEx(cfg: RaycastExCfg): PhysicsRayHit | null;
        raycastAll(cfg: RaycastCfg): PhysicsRayHit[]; // or unknown[] depending on host; wrapper intends array

        // lifecycle
        remove(): void;
    }

    // ------------------------------------
    // Main API
    // ------------------------------------

    export interface PhysicsApi {
        // core
        body(cfg: PhysicsBodyCfg): PhysicsBodyHandle;
        remove(handleOrId: BodyRef): void;

        // world queries
        raycast(cfg: RaycastCfg): PhysicsRayHit | null;
        raycastEx(cfg: RaycastExCfg): PhysicsRayHit | null;
        raycastAll(cfg: RaycastCfg): PhysicsRayHit[];

        // transforms
        position(handleOrId: BodyRef): { x: number; y: number; z: number };
        position(handleOrId: BodyRef, pos: Vec3Like): void;

        warp(handleOrId: BodyRef, pos: Vec3Like): void;

        velocity(handleOrId: BodyRef): { x: number; y: number; z: number };
        velocity(handleOrId: BodyRef, vel: Vec3Like): void;

        yaw(handleOrId: BodyRef, yawRad: number): void;

        // forces
        applyImpulse(handleOrId: BodyRef, impulse: Vec3Like): void;
        applyCentralForce(handleOrId: BodyRef, force: Vec3Like): void;
        applyTorque(handleOrId: BodyRef, torque: Vec3Like): void;

        angularVelocity(handleOrId: BodyRef): { x: number; y: number; z: number };
        angularVelocity(handleOrId: BodyRef, vel: Vec3Like): void;

        clearForces(handleOrId: BodyRef): void;

        // flags
        lockRotation(handleOrId: BodyRef, lock: boolean): void;
        collisionGroups(handleOrId: BodyRef, group: number, mask: number): void;

        // debug/world
        debug(enabled: boolean): void;
        gravity(g: Vec3Like): void;

        // helpers
        collider: PhysicsColliderHelpers;

        idOf(handleOrId: BodyRef): number;
        surfaceIdOf(surface: SurfaceRef): number;
        vec3(v: Vec3Like, fbX?: number, fbY?: number, fbZ?: number): { x: number; y: number; z: number };

        ensureBodyForSurface(
            surface: SurfaceRef,
            cfg?: Omit<PhysicsBodyCfg, "surface">
        ): PhysicsBodyHandle;

        // ✅ new: bind handle/id once and work with methods (no ids everywhere)
        ref(handleOrId: BodyRef): PhysicsBodyRef;
    }

    export interface BuiltinMeta {
        name: string;
        globalName: "PHYS";
        version: string;
        description?: string;
        engineMin?: string;
    }
}

declare function PhysicsFactory(engine: unknown, K?: unknown): KalitechPhysics.PhysicsApi;

declare namespace PhysicsFactory {
    const META: KalitechPhysics.BuiltinMeta;
}

export = PhysicsFactory;

declare global {
    const PHYS: KalitechPhysics.PhysicsApi;
}