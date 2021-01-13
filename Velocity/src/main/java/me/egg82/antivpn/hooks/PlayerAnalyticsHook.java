package me.egg82.antivpn.hooks;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.ExtensionService;
import com.djrapitops.plan.extension.FormatType;
import com.djrapitops.plan.extension.annotation.BooleanProvider;
import com.djrapitops.plan.extension.annotation.NumberProvider;
import com.djrapitops.plan.extension.annotation.PluginInfo;
import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.egg82.antivpn.api.APIException;
import me.egg82.antivpn.api.VPNAPIProvider;
import me.egg82.antivpn.api.model.ip.AlgorithmMethod;
import me.egg82.antivpn.api.model.ip.IPManager;
import me.egg82.antivpn.api.model.player.PlayerManager;
import me.egg82.antivpn.config.ConfigUtil;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerAnalyticsHook implements PluginHook {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CapabilityService capabilities;

    public PlayerAnalyticsHook(@NonNull ProxyServer proxy) {
        capabilities = CapabilityService.getInstance();

        if (isCapabilityAvailable("DATA_EXTENSION_VALUES") && isCapabilityAvailable("DATA_EXTENSION_TABLES")) {
            try {
                ExtensionService.getInstance().register(new Data(proxy));
            } catch (NoClassDefFoundError ex) {
                // Plan not installed
                logger.error("Plan is not installed.", ex);
            } catch (IllegalStateException ex) {
                // Plan not enabled
                logger.error("Plan is not enabled.", ex);
            } catch (IllegalArgumentException ex) {
                // DataExtension impl error
                logger.error("DataExtension implementation exception.", ex);
            }
        }
    }

    public void cancel() { }

    private boolean isCapabilityAvailable(String capability) {
        try {
            return capabilities.hasCapability(capability);
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    @PluginInfo(
            name = "AntiVPN",
            iconName = "shield-alt",
            iconFamily = Family.SOLID,
            color = Color.BLUE
    )
    class Data implements DataExtension {
        private final ProxyServer proxy;
        private final CallEvents[] events = new CallEvents[] { CallEvents.SERVER_PERIODICAL, CallEvents.SERVER_EXTENSION_REGISTER, CallEvents.PLAYER_JOIN };

        private Data(@NonNull ProxyServer proxy) {
            this.proxy = proxy;
        }

        @NumberProvider(
                text = "VPN Users",
                description = "Number of online VPN users.",
                priority = 2,
                iconName = "user-shield",
                iconFamily = Family.SOLID,
                iconColor = Color.NONE,
                format = FormatType.NONE
        )
        public long getVpns() {
            IPManager ipManager = VPNAPIProvider.getInstance().getIpManager();

            Collection<Player> players = proxy.getAllPlayers();
            ExecutorService pool = Executors.newWorkStealingPool(Math.min(players.size() / 2, Runtime.getRuntime().availableProcessors() / 2));
            CountDownLatch latch = new CountDownLatch(players.size());
            AtomicLong results = new AtomicLong(0L);

            for (Player p : players) {
                pool.submit(() -> {
                    String ip = getIp(p);
                    if (ip == null || ip.isEmpty()) {
                        latch.countDown();
                        return;
                    }

                    if (ipManager.getCurrentAlgorithmMethod() == AlgorithmMethod.CONSESNSUS) {
                        try {
                            if (ipManager.consensus(ip, true)
                                    .exceptionally(this::handleException)
                                    .join() >= ipManager.getMinConsensusValue()) {
                                results.addAndGet(1L);
                            }
                        } catch (CompletionException ignored) {
                        } catch (Exception ex) {
                            if (ConfigUtil.getDebugOrFalse()) {
                                logger.error(ex.getMessage(), ex);
                            } else {
                                logger.error(ex.getMessage());
                            }
                        }
                    } else {
                        try {
                            if (Boolean.TRUE.equals(ipManager.cascade(ip, true)
                                    .exceptionally(this::handleException)
                                    .join())) {
                                results.addAndGet(1L);
                            }
                        } catch (CompletionException ignored) {
                        } catch (Exception ex) {
                            if (ConfigUtil.getDebugOrFalse()) {
                                logger.error(ex.getMessage(), ex);
                            } else {
                                logger.error(ex.getMessage());
                            }
                        }
                    }
                    latch.countDown();
                });
            }

            try {
                if (!latch.await(40L, TimeUnit.SECONDS)) {
                    logger.warn("Plan hook timed out before all results could be obtained.");
                }
            } catch (InterruptedException ex) {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.error(ex.getMessage(), ex);
                } else {
                    logger.error(ex.getMessage());
                }
                Thread.currentThread().interrupt();
            }
            pool.shutdownNow(); // Kill it with fire

            return results.get();
        }

        @NumberProvider(
                text = "MCLeaks Users",
                description = "Number of online MCLeaks users.",
                priority = 1,
                iconName = "users",
                iconFamily = Family.SOLID,
                iconColor = Color.NONE,
                format = FormatType.NONE
        )
        public long getMcLeaks() {
            PlayerManager playerManager = VPNAPIProvider.getInstance().getPlayerManager();

            Collection<Player> players = proxy.getAllPlayers();
            ExecutorService pool = Executors.newWorkStealingPool(Math.min(players.size() / 2, Runtime.getRuntime().availableProcessors() / 2));
            CountDownLatch latch = new CountDownLatch(players.size());
            AtomicLong results = new AtomicLong(0L);

            for (Player p : players) {
                pool.submit(() -> {
                    try {
                        if (Boolean.TRUE.equals(playerManager.checkMcLeaks(p.getUniqueId(), true)
                                .exceptionally(this::handleException)
                                .join())) {
                            results.addAndGet(1L);
                        }
                    } catch (CompletionException ignored) {
                    } catch (Exception ex) {
                        if (ConfigUtil.getDebugOrFalse()) {
                            logger.error(ex.getMessage(), ex);
                        } else {
                            logger.error(ex.getMessage());
                        }
                    }
                    latch.countDown();
                });
            }

            try {
                if (!latch.await(40L, TimeUnit.SECONDS)) {
                    logger.warn("Plan hook timed out before all results could be obtained.");
                }
            } catch (InterruptedException ex) {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.error(ex.getMessage(), ex);
                } else {
                    logger.error(ex.getMessage());
                }
                Thread.currentThread().interrupt();
            }
            pool.shutdownNow(); // Kill it with fire

            return results.get();
        }

        @BooleanProvider(
                text = "VPN",
                description = "Using a VPN or proxy.",
                iconName = "user-shield",
                iconFamily = Family.SOLID,
                iconColor = Color.NONE
        )
        public boolean getUsingVpn(@NonNull UUID playerID) {
            Optional<Player> player = proxy.getPlayer(playerID);
            if (!player.isPresent()) {
                return false;
            }

            String ip = getIp(player.get());
            if (ip == null || ip.isEmpty()) {
                return false;
            }

            IPManager ipManager = VPNAPIProvider.getInstance().getIpManager();

            if (ipManager.getCurrentAlgorithmMethod() == AlgorithmMethod.CONSESNSUS) {
                try {
                    return ipManager.consensus(ip, true)
                            .exceptionally(this::handleException)
                            .join() >= ipManager.getMinConsensusValue();
                } catch (CompletionException ignored) { }
                catch (Exception ex) {
                    if (ConfigUtil.getDebugOrFalse()) {
                        logger.error(ex.getMessage(), ex);
                    } else {
                        logger.error(ex.getMessage());
                    }
                }
            } else {
                try {
                    return ipManager.cascade(ip, true)
                            .exceptionally(this::handleException)
                            .join();
                } catch (CompletionException ignored) { }
                catch (Exception ex) {
                    if (ConfigUtil.getDebugOrFalse()) {
                        logger.error(ex.getMessage(), ex);
                    } else {
                        logger.error(ex.getMessage());
                    }
                }
            }

            return false;
        }

        @BooleanProvider(
                text = "MCLeaks",
                description = "Using an MCLeaks account.",
                iconName = "users",
                iconFamily = Family.SOLID,
                iconColor = Color.NONE
        )
        public boolean getMcLeaks(@NonNull UUID playerId) {
            PlayerManager playerManager = VPNAPIProvider.getInstance().getPlayerManager();

            try {
                return playerManager.checkMcLeaks(playerId, true)
                        .exceptionally(this::handleException)
                        .join();
            } catch (CompletionException ignored) { }
            catch (Exception ex) {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.error(ex.getMessage(), ex);
                } else {
                    logger.error(ex.getMessage());
                }
            }

            return false;
        }

        private @Nullable String getIp(@NonNull Player player) {
            InetSocketAddress address = player.getRemoteAddress();
            if (address == null) {
                return null;
            }
            InetAddress host = address.getAddress();
            if (host == null) {
                return null;
            }
            return host.getHostAddress();
        }

        private <T> @Nullable T handleException(@NonNull Throwable ex) {
            Throwable oldEx = null;
            if (ex instanceof CompletionException) {
                oldEx = ex;
                ex = ex.getCause();
            }

            if (ex instanceof APIException) {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.error("[Hard: " + ((APIException) ex).isHard() + "] " + ex.getMessage(), oldEx != null ? oldEx : ex);
                } else {
                    logger.error("[Hard: " + ((APIException) ex).isHard() + "] " + ex.getMessage());
                }
            } else {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.error(ex.getMessage(), oldEx != null ? oldEx : ex);
                } else {
                    logger.error(ex.getMessage());
                }
            }
            return null;
        }

        public @NonNull CallEvents[] callExtensionMethodsOn() { return events; }
    }
}
