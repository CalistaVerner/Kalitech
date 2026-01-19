// FILE: resources/kalitech/builtin/Sound.d.ts
// Author: Calista Verner

declare namespace KalitechSound {
    export type Vec3 = [number, number, number] | { x: number; y: number; z: number };

    export interface BuiltinMeta {
        moduleId: string;
        globalName: "SND";
        version: string;
        description?: string;
        engineMin?: string;
    }

    export interface SoundCreateCfg {
        src?: string;
        soundFile?: string;

        leftFile?: string;
        rightFile?: string;
        separation?: number;

        type?: "buffer" | "stream";

        volume?: number | [number, number];
        pitch?: number | [number, number];
        looping?: boolean;

        is3D?: boolean;

        pos?: Vec3;
        position?: Vec3;
        x?: number;
        y?: number;
        z?: number;

        [k: string]: unknown;
    }

    export type SoundNodeHandle = unknown;

    export interface SoundInstance {
        __node(): SoundNodeHandle;
        play(): this;
        stop(): this;
        pause(): this;
        volume(v: number): this;
        pitch(v: number): this;
        loop(v?: boolean): this;
        pos(x: number, y: number, z: number): this;
        pos(v: Vec3): this;
        positional(v?: boolean): this;
        maxDistance(v: number): this;
        refDistance(v: number): this;
        reverb(v?: boolean): this;
        directional(v?: boolean): this;
        innerAngle(v: number): this;
        outerAngle(v: number): this;
        direction(x: number, y: number, z: number): this;
        direction(v: Vec3): this;
        velocity(x: number, y: number, z: number): this;
        velocity(v: Vec3): this;
        velocityFromTranslation(v?: boolean): this;
    }

    export interface SoundRegistry {
        create(cfg: SoundCreateCfg): SoundInstance;
        createAndPlay(cfg: SoundCreateCfg): SoundInstance;

        loadBank(bankObj: unknown): this;

        clearBank(): this;

        listEvents(): string[];

        createEvent(eventKey: string, overrides?: SoundCreateCfg | null): SoundInstance;

        playEvent(eventKey: string, overrides?: SoundCreateCfg | null): SoundInstance;
    }
}

declare function SoundFactory(engine: unknown, K?: unknown): KalitechSound.SoundRegistry;

declare namespace SoundFactory {
    const META: KalitechSound.BuiltinMeta;
}

export = SoundFactory;

declare global {
    const SND: KalitechSound.SoundRegistry;
}