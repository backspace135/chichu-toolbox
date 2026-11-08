package rikka.shizuku.server;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class GrantIdentity {
    private GrantIdentity() {}

    public static boolean matches(Collection<String> saved, Collection<String> installed) {
        if (saved == null || installed == null) return false;
        for (String name : saved) if (installed.contains(name)) return true;
        return false;
    }

    public static Set<String> revocationTargets(Collection<String> installed, Collection<String> clients) {
        Set<String> targets = new LinkedHashSet<>(installed);
        targets.addAll(clients);
        return targets;
    }
}
