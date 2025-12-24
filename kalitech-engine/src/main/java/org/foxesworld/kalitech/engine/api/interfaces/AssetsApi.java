package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;

public interface AssetsApi {
    @HostAccess.Export String readText(String assetPath);
}