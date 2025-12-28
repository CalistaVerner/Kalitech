// Author: Calista Verner

declare namespace KalitechLog {
    export type LogArg = string | number | boolean | null | undefined | Error | object;

    export interface LoggerMethods {
        trace(...args: LogArg[]): string;
        debug(...args: LogArg[]): string;
        info(...args: LogArg[]): string;
        warn(...args: LogArg[]): string;
        error(...args: LogArg[]): string;
        fatal(...args: LogArg[]): string;
    }

    export interface ScopedLogger extends LoggerMethods {
        readonly scope: string;
    }

    export interface LogApi extends LoggerMethods {
        enabled(): boolean;

        child(scopeName: string): ScopedLogger;
        scope(scopeName: string): ScopedLogger;

        safeJson(v: unknown): string;
    }

    export interface BuiltinMeta {
        name: string;
        globalName?: string;
        version?: string;
        description?: string;
        engineMin?: string;
    }
}

declare function create(engine: unknown, K?: unknown): KalitechLog.LogApi;

declare namespace create {
    const META: KalitechLog.BuiltinMeta;
}

export = create;

declare global {
    const LOG: KalitechLog.LogApi;
}
