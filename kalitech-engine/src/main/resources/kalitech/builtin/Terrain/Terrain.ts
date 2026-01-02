// FILE: resources/kalitech/builtin/Terrain.d.ts
// Author: Calista Verner

declare namespace KalitechTerrain {
    export interface BuiltinMeta {
        name: string;
        globalName: "TERR";
        version: string;
        description?: string;
        engineMin?: string;
    }

    export type Vec3 =
        | [number, number, number]
        | { x: number; y: number; z: number }
        | { 0: number; 1: number; 2: number };

    export type SurfaceRef =
        | number
        | { id?: number; surfaceId?: number }
        | unknown;

    export type BodyRef =
        | number
        | { id?: number; bodyId?: number }
        | unknown;

    export interface SurfaceHandle {
        id(): number;
        type(): string;
    }

    export interface BodyRefApi {
        id(): number;
        position(v?: Vec3): any;
        warp(pos: Vec3): any;
        velocity(v?: Vec3): any;
        yaw(yawRad: number): any;
        applyImpulse(impulse: Vec3): any;
        lockRotation(lock: boolean): any;
        setKinematic?(k: boolean): any;
        remove(): any;
    }

    export interface PhysicsResult {
        bodyId: number;
        body?: BodyRefApi;
        handle?: any;
    }

    export interface CreateResult {
        surface: SurfaceHandle;
        bodyId: number;
        body?: BodyRefApi;
    }

    export interface LodCfg {
        enabled?: boolean;
        [k: string]: unknown;
    }

    export interface CommonCreateCfg {
        name?: string;
        attach?: boolean;
        shadows?: boolean;
        material?: any;

        // transform (SurfaceApiImpl.applyTransform)
        pos?: Vec3;
        rot?: Vec3 | [number, number, number, number] | { x: number; y: number; z: number; w: number };
        scale?: Vec3 | number;

        lod?: LodCfg;

        /** If provided, TERR creates a physics body after surface creation. */
        physics?: Record<string, unknown>;

        [k: string]: unknown;
    }

    export interface HeightmapTerrainCfg extends CommonCreateCfg {
        heightmap: string;
        patchSize?: number;
        size?: number;
        heightScale?: number;
        xzScale?: number;
    }

    export interface HeightsTerrainCfg extends CommonCreateCfg {
        heights: ArrayLike<number>;
        size: number;
        patchSize?: number;
        xzScale?: number;
        yScale?: number;
    }

    export interface QuadCfg extends CommonCreateCfg {
        w?: number;
        h?: number;
    }

    export interface PlaneCfg extends CommonCreateCfg {
        w?: number;
        h?: number;
    }

    export interface TerrainApi {
        readonly META: BuiltinMeta;

        terrain(cfg: HeightmapTerrainCfg): SurfaceHandle | CreateResult;
        terrainHeights(cfg: HeightsTerrainCfg): SurfaceHandle | CreateResult;
        quad(cfg?: QuadCfg): SurfaceHandle | CreateResult;
        plane(cfg?: PlaneCfg): SurfaceHandle | CreateResult;

        material(surface: SurfaceRef, material: any): void;
        lod(surface: SurfaceRef, cfg?: LodCfg): void;
        scale(surface: SurfaceRef, xzScale: number, cfg?: { yScale?: number }): void;

        heightAt(surface: SurfaceRef, x: number, z: number, world?: boolean): number;
        normalAt(surface: SurfaceRef, x: number, z: number, world?: boolean): { x: number; y: number; z: number };

        physics(surface: SurfaceRef, cfg?: Record<string, unknown>): PhysicsResult;
        attach(surface: SurfaceRef, entityId: number): void;
        detach(surface: SurfaceRef): void;
    }
}

declare const TERR: KalitechTerrain.TerrainApi;