package demo;

import com.jme3.app.SimpleApplication;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.system.lwjgl.LwjglWindow;
import org.foxesworld.kalitech.engine.modules.chromium.ChromiumConfig;
import org.foxesworld.kalitech.engine.modules.chromium.ChromiumService;
import org.foxesworld.kalitech.engine.modules.chromium.ChromiumView;

public final class ChromiumSmokeTestNoInput extends SimpleApplication {

    private ChromiumService chromium;
    private ChromiumView view;

    private Geometry geom;
    private ChromiumConfig cfg;

    private boolean chromiumStarted;

    public static void main(String[] args) {
        ChromiumSmokeTestNoInput app = new ChromiumSmokeTestNoInput();

        AppSettings s = new AppSettings(true);
        s.setTitle("Chromium OSR Smoke Test");
        s.setResolution(1280, 720);
        s.setVSync(true);

        app.setSettings(s);
        app.setShowSettings(false);

        // Force a real window (not AwtPanelsContext)
        //app.setContextClass(LwjglWindow.class);
        app.start(JmeContext.Type.Display);
    }

    @Override
    public void simpleInitApp() {
        cam.setParallelProjection(true);
        flyCam.setEnabled(false);

        cfg = ChromiumConfig.builder()
                .size(1024, 640)
                .fps(60)
                .transparent(true)
                .disableGpu(true)
                .build();

        // placeholder quad, material will be assigned after Chromium is ready
        geom = new Geometry("chromium", new Quad(cfg.width, cfg.height));
        geom.setLocalTranslation(new Vector3f(20f, 20f, 0f));
        guiNode.attachChild(geom);
    }

    @Override
    public void simpleUpdate(float tpf) {
        // Start Chromium only after window is already alive
        if (!chromiumStarted) {
            chromiumStarted = true;
            startChromium();
        }

        if (chromium != null) {
            chromium.update();
        }
        if (view != null) {
            view.updateTexture();
        }
    }

    private void startChromium() {
        chromium = new ChromiumService(cfg);
        chromium.init();

        view = chromium.createView("about:blank");
        view.loadHtml(html(), "http://local/");

        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setTexture("ColorMap", view.texture());
        geom.setMaterial(mat);
    }

    @Override
    public void destroy() {
        try {
            if (view != null) view.close();
        } catch (Exception ignored) {
        }
        try {
            if (chromium != null) chromium.close();
        } catch (Exception ignored) {
        }
        super.destroy();
    }

    private static String html() {
        return """
                <!doctype html>
                <html>
                  <head>
                    <meta charset="utf-8"/>
                    <style>
                      html, body { margin:0; padding:0; background:transparent; }
                      .root { width:100vw; height:100vh; display:flex; align-items:center; justify-content:center; }
                      .card {
                        font-family: Arial, sans-serif;
                        color: white;
                        font-size: 34px;
                        padding: 24px 28px;
                        border-radius: 14px;
                        background: rgba(0,0,0,0.55);
                        border: 1px solid rgba(255,255,255,0.25);
                      }
                    </style>
                  </head>
                  <body>
                    <div class="root">
                      <div class="card">JCEF OSR -> jME Texture</div>
                    </div>
                  </body>
                </html>
                """;
    }
}