// FILE: resources/kalitech/builtin/Sound.d.ts
// Author: Calista Verner

declare namespace KalitechSound {
    // ------------------------------
    // Common types
    // ------------------------------

    export type Vec3 =
        | [number, number, number]
        | { x: number; y: number; z: number };

    export interface BuiltinMeta {
        name: string;
        globalName: "SND";
        version: string;
        description?: string;
        engineMin?: string;
    }

    // ------------------------------
    // Configs
    // ------------------------------

    /**
     * JS wrapper passes cfg straight into engine.sound().create(cfg).
     *
     * Java SoundApiImpl currently reads:
     *  - soundFile: string
     *  - volume: number
     *  - pitch: number
     *  - looping: boolean
     *  - is3D: boolean
     *  - x/y/z: numbers (only if is3D=true)
     *
     * Wrapper API ALSO supports:
     *  - positional(), pos(), direction(), velocity(), etc.
     * Those require corresponding engine.sound() methods to exist.
     */
    export interface SoundCreateCfg {
        soundFile: string;

        volume?: number;
        pitch?: number;
        looping?: boolean;

        /**
         * Java-side flag (SoundApiImpl): if true => positional + set initial translation from x/y/z.
         */
        is3D?: boolean;

        /**
         * Initial position only used by Java impl when is3D=true.
         * (Wrapper-level pos() always works if engine exposes setPosition)
         */
        x?: number;
        y?: number;
        z?: number;

        // forward-compatible / extra fields are allowed
        [k: string]: unknown;
    }

    // ------------------------------
    // Engine handle
    // ------------------------------

    /**
     * The node returned by engine.sound().create(cfg).
     * In Java it's com.jme3.audio.AudioNode.
     * We keep it as unknown to avoid coupling in TS.
     */
    export type SoundNodeHandle = unknown;

    // ------------------------------
    // Fluent instance wrapper
    // ------------------------------

    export interface SoundInstance {
        /** Internal: returns engine node handle (AudioNode). */
        __node(): SoundNodeHandle;

        play(): this;
        stop(): this;

        /**
         * Optional. Works only if engine.sound().pause exists.
         */
        pause(): this;

        volume(v: number): this;
        pitch(v: number): this;
        loop(v?: boolean): this;

        /**
         * Sets positional sound position (enables positional internally if engine supports it).
         */
        pos(x: number, y: number, z: number): this;
        pos(v: Vec3): this;

        /**
         * Optional. Works only if engine.sound().setPositional exists.
         */
        positional(v?: boolean): this;

        /**
         * Optional. Works only if engine.sound().setMaxDistance exists.
         */
        maxDistance(v: number): this;

        /**
         * Optional. Works only if engine.sound().setRefDistance exists.
         */
        refDistance(v: number): this;

        /**
         * Optional. Works only if engine.sound().setReverbEnabled exists.
         */
        reverb(v?: boolean): this;

        /**
         * Optional. Works only if engine.sound().setDirectional exists.
         */
        directional(v?: boolean): this;

        /**
         * Optional. Works only if engine.sound().setInnerAngle exists.
         */
        innerAngle(v: number): this;

        /**
         * Optional. Works only if engine.sound().setOuterAngle exists.
         */
        outerAngle(v: number): this;

        /**
         * Optional. Works only if engine.sound().setDirection exists.
         */
        direction(x: number, y: number, z: number): this;
        direction(v: Vec3): this;

        /**
         * Optional. Works only if engine.sound().setVelocity exists.
         */
        velocity(x: number, y: number, z: number): this;
        velocity(v: Vec3): this;

        /**
         * Optional. Works only if engine.sound().setVelocityFromTranslation exists.
         */
        velocityFromTranslation(v?: boolean): this;
    }

    // ------------------------------
    // Registry (builtin export)
    // ------------------------------

    export interface SoundRegistry {
        /**
         * Creates a sound instance from cfg (wraps engine.sound().create(cfg)).
         */
        create(cfg: SoundCreateCfg): SoundInstance;

        /**
         * Convenience: create(cfg).play()
         */
        createAndPlay(cfg: SoundCreateCfg): SoundInstance;
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