// Input.d.ts

export interface RootKitMeta {
    name: string;
    globalName: string;
    version: string;
    engineMin: string;
}

export interface Vec2 {
    x: number;
    y: number;
}

export interface Delta2 {
    dx: number;
    dy: number;
}

export interface InputSnapshot {
    frameId: number;
    nanoTime: number;

    mouseX: number;
    mouseY: number;

    dx: number;
    dy: number;

    wheel: number;

    mouseMask: number;

    grabbed: boolean;
    cursorVisible: boolean;

    pressedKeyCodes: number[];
    justPressed: number[];
    justReleased: number[];
}

export interface InputApi {
    readonly META: RootKitMeta;

    consumeSnapshot(): InputSnapshot | any;

    keyDown(key: string | number): boolean;
    keyCode(name: string): number;

    mouseX(): number;
    mouseY(): number;

    cursorPosition(): Vec2;

    mouseDx(): number;
    mouseDy(): number;

    mouseDX(): number;
    mouseDY(): number;

    mouseDelta(): Delta2;
    consumeMouseDelta(): Delta2;

    wheelDelta(): number;
    consumeWheelDelta(): number;

    mouseDown(button: number): boolean;

    cursorVisible(): boolean;
    cursorVisible(visible: boolean): InputApi;

    grabMouse(grab: boolean): InputApi;
    grabbed(): boolean;

    endFrame(): InputApi;

    beginFrame(): InputSnapshot | any;
    poll(): InputSnapshot | any;

    lastSnapshot(): InputSnapshot | null | any;

    pressed(key: string | number): boolean;
    released(key: string | number): boolean;

    mousePos(): Vec2;
    delta(): Delta2;
}

declare const INP: InputApi;
export default INP;
export { INP };