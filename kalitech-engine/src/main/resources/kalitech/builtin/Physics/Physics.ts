// File: resources/kalitech/builtin/Physics.d.ts
// Author: Calista Verner

declare namespace KalitechPhysics {
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

    export interface PhysicsRayHit {
        bodyId: number;
        surfaceId: number;
        fraction: number;
        point: { x: number; y: number; z: number };
        normal: { x: number; y: number; z: number };
        [k: string]: unknown;
    }

    export interface PhysicsBodyHandle {
        id: number;
        surfaceId: number;
        [k: string]: unknown;
    }

    export interface PhysicsColliderHelpers {
        box(halfExtents: Vec3Like): ColliderBoxCfg;
        sphere(radius: number): ColliderSphereCfg;
        capsule(radius: number, height: number): ColliderCapsuleCfg;
        cylinder(radius: number, height: number): ColliderCylinderCfg;
        mesh(): ColliderMeshCfg;
        dynamicMesh(): ColliderDynamicMeshCfg;
    }

    export interface PhysicsApi {
        body(cfg: PhysicsBodyCfg): PhysicsBodyHandle;
        remove(handleOrId: BodyRef): void;
        raycast(cfg: RaycastCfg): PhysicsRayHit | null;

        position(handleOrId: BodyRef): { x: number; y: number; z: number };
        position(handleOrId: BodyRef, pos: Vec3Like): void;

        warp(handleOrId: BodyRef, pos: Vec3Like): void;

        velocity(handleOrId: BodyRef): { x: number; y: number; z: number };
        velocity(handleOrId: BodyRef, vel: Vec3Like): void;

        yaw(handleOrId: BodyRef, yawRad: number): void;
        applyImpulse(handleOrId: BodyRef, impulse: Vec3Like): void;
        lockRotation(handleOrId: BodyRef, lock: boolean): void;

        debug(enabled: boolean): void;
        gravity(g: Vec3Like): void;

        collider: PhysicsColliderHelpers;

        idOf(handleOrId: BodyRef): number;
        surfaceIdOf(surface: SurfaceRef): number;
        vec3(v: Vec3Like, fbX?: number, fbY?: number, fbZ?: number): { x: number; y: number; z: number };

        ensureBodyForSurface(
            surface: SurfaceRef,
            cfg?: Omit<PhysicsBodyCfg, "surface">
        ): PhysicsBodyHandle;
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