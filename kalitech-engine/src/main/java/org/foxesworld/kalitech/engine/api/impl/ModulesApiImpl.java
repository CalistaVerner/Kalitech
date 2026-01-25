package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.ModulesApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiModuleInfo;
import org.foxesworld.kalitech.engine.api.module.ApiRegistry;
import org.graalvm.polyglot.HostAccess;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

@Deprecated
public final class ModulesApiImpl extends AbstractApiModule implements ModulesApi {

    private static final Method M_LIST =
            method(ModulesApiImpl.class, "list");
    private static final Method M_DESCRIBE =
            method(ModulesApiImpl.class, "describe", String.class);
    private static final Method M_DESCRIBE_ALL =
            method(ModulesApiImpl.class, "describeAll");

    public ModulesApiImpl() {
        super("modules", "Modules", "1.0.0");
    }

    private ApiRegistry registry() {
        if (engine == null) return null;
        return engine.getApiRegistry();
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED, ApiFlag.EDITOR_VISIBLE},
            cost = ApiCostHint.CHEAP
    )
    public String[] list() {
        return profiled(() ->
                apiCall(M_LIST, new Object[]{}, () -> {
                    ApiRegistry reg = registry();
                    return reg != null ? reg.keys() : new String[0];
                })
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED, ApiFlag.EDITOR_VISIBLE},
            cost = ApiCostHint.NORMAL
    )
    public Map<String, Object> describe(String id) {
        return profiled(() ->
                apiCall(M_DESCRIBE, new Object[]{id}, () -> {
                    ApiRegistry reg = registry();
                    if (reg == null) return null;
                    ApiModuleInfo info = reg.info(id);
                    return info != null ? info.toMap() : null;
                })
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED, ApiFlag.EDITOR_VISIBLE},
            cost = ApiCostHint.NORMAL
    )
    @SuppressWarnings("unchecked")
    public Map<String, Object>[] describeAll() {
        return profiled(() ->
                apiCall(M_DESCRIBE_ALL, new Object[]{}, () -> {
                    ApiRegistry reg = registry();
                    if (reg == null) return new Map[0];
                    List<ApiModuleInfo> infos = reg.infos();
                    Map<String, Object>[] out = new Map[infos.size()];
                    for (int i = 0; i < infos.size(); i++) {
                        out[i] = infos.get(i).toMap();
                    }
                    return out;
                })
        );
    }
}
