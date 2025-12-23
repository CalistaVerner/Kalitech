package org.foxesworld.kalitech.engine.api;

import org.graalvm.polyglot.HostAccess;

public interface AssetsApi {
    @HostAccess.Export String readText(String assetPath);
}