package com.server.core.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 플러그인 간 서비스 공유를 위한 중앙 레지스트리.
 *
 * 사용 예시:
 *   // Economy 플러그인에서 등록
 *   ServiceRegistry.register(EconomyProvider.class, new EconomyProviderImpl());
 *
 *   // Land 플러그인에서 사용
 *   Optional<EconomyProvider> eco = ServiceRegistry.get(EconomyProvider.class);
 *   eco.ifPresent(e -> e.subtractBalance(uuid, price));
 */
public class ServiceRegistry {

    private static final Map<Class<?>, Object> services = new HashMap<>();

    private ServiceRegistry() {}

    public static <T> void register(Class<T> type, T implementation) {
        services.put(type, implementation);
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> get(Class<T> type) {
        return Optional.ofNullable((T) services.get(type));
    }

    public static <T> boolean isRegistered(Class<T> type) {
        return services.containsKey(type);
    }

    public static void unregister(Class<?> type) {
        services.remove(type);
    }

    /** 서버 종료 시 전체 초기화 */
    public static void clear() {
        services.clear();
    }
}
