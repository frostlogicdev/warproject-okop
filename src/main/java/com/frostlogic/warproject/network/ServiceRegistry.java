package com.frostlogic.warproject.network;

import com.frostlogic.warproject.persistence.Database;
import com.frostlogic.warproject.persistence.dao.*;
import com.frostlogic.warproject.server.ServerEvents;
import com.frostlogic.warproject.server.auth.CaptchaService;
import com.frostlogic.warproject.server.auth.InMemoryLoginAttemptTracker;
import com.frostlogic.warproject.server.auth.LoginAttemptTracker;
import com.frostlogic.warproject.server.auth.WGuardService;
import com.frostlogic.warproject.server.award.AwardsService;
import com.frostlogic.warproject.server.captivity.CaptivityService;
import com.frostlogic.warproject.server.lifecycle.PlayerLifecycleService;
import com.frostlogic.warproject.server.militaryid.MilitaryIdService;
import com.frostlogic.warproject.server.subdivision.SubdivisionService;
import org.jetbrains.annotations.Nullable;

/**
 * Centralized service registry for network payload handlers.
 * <p>
 * Lazily initializes services from the shared {@link Database} instance.
 * All services are created once and cached for the server lifetime.
 * Cleared on server stop via {@link #clear()}.
 * <p>
 * This avoids the need for each handler to independently resolve dependencies.
 */
public final class ServiceRegistry {

    private static volatile WGuardService wguardInstance;
    private static volatile CaptchaService captchaInstance;
    private static volatile PlayerLifecycleService lifecycleInstance;
    private static volatile CaptivityService captivityInstance;
    private static volatile SubdivisionService subdivisionInstance;
    private static volatile LoginAttemptTracker trackerInstance;
    private static volatile AwardsService awardsInstance;
    private static volatile MilitaryIdService militaryIdInstance;

    private ServiceRegistry() {}

    @Nullable
    public static WGuardService wguard() {
        if (wguardInstance == null) {
            synchronized (ServiceRegistry.class) {
                if (wguardInstance == null) {
                    Database db = ServerEvents.getDatabase();
                    if (db == null) return null;
                    if (trackerInstance == null) {
                        trackerInstance = new InMemoryLoginAttemptTracker();
                    }
                    wguardInstance = new WGuardService(db,
                            new AccountsDao(), new PlayersDao(), new CooldownsDao(), trackerInstance);
                }
            }
        }
        return wguardInstance;
    }

    @Nullable
    public static CaptchaService captcha() {
        if (captchaInstance == null) {
            synchronized (ServiceRegistry.class) {
                if (captchaInstance == null) {
                    Database db = ServerEvents.getDatabase();
                    if (db == null) return null;
                    captchaInstance = new CaptchaService(db, new CooldownsDao());
                }
            }
        }
        return captchaInstance;
    }

    @Nullable
    public static PlayerLifecycleService lifecycle() {
        if (lifecycleInstance == null) {
            synchronized (ServiceRegistry.class) {
                if (lifecycleInstance == null) {
                    Database db = ServerEvents.getDatabase();
                    if (db == null) return null;
                    lifecycleInstance = new PlayerLifecycleService(db, new PlayersDao());
                }
            }
        }
        return lifecycleInstance;
    }

    @Nullable
    public static CaptivityService captivity() {
        if (captivityInstance == null) {
            synchronized (ServiceRegistry.class) {
                if (captivityInstance == null) {
                    Database db = ServerEvents.getDatabase();
                    if (db == null) return null;
                    captivityInstance = new CaptivityService(db,
                            new PassportsDao(), new PlayersDao(), new AuditLogDao());
                }
            }
        }
        return captivityInstance;
    }

    @Nullable
    public static SubdivisionService subdivision() {
        if (subdivisionInstance == null) {
            synchronized (ServiceRegistry.class) {
                if (subdivisionInstance == null) {
                    Database db = ServerEvents.getDatabase();
                    if (db == null) return null;
                    subdivisionInstance = new SubdivisionService(db,
                            new SubdivisionsDao(), new PlayersDao(), new AuditLogDao());
                }
            }
        }
        return subdivisionInstance;
    }

    /**
     * Returns the AwardsService instance, lazily initialized.
     * <p>
     * Also wires the MilitaryIdService reference for card synchronization
     * on first access.
     */
    @Nullable
    public static AwardsService awards() {
        if (awardsInstance == null) {
            synchronized (ServiceRegistry.class) {
                if (awardsInstance == null) {
                    Database db = ServerEvents.getDatabase();
                    if (db == null) return null;
                    awardsInstance = new AwardsService(db,
                            new AwardDefinitionsDao(), new PlayerAwardsDao(),
                            new AuditLogDao(), new PlayersDao());
                    // Wire MilitaryIdService for card synchronization (Req. 15.1, 15.5)
                    MilitaryIdService midService = militaryId();
                    if (midService != null) {
                        awardsInstance.setMilitaryIdService(midService);
                    }
                }
            }
        }
        return awardsInstance;
    }

    /**
     * Returns the MilitaryIdService instance, lazily initialized.
     */
    @Nullable
    public static MilitaryIdService militaryId() {
        if (militaryIdInstance == null) {
            synchronized (ServiceRegistry.class) {
                if (militaryIdInstance == null) {
                    Database db = ServerEvents.getDatabase();
                    if (db == null) return null;
                    militaryIdInstance = new MilitaryIdService(db, new PassportsDao());
                }
            }
        }
        return militaryIdInstance;
    }

    /**
     * Clears all cached service instances. Called on server stop.
     */
    public static void clear() {
        synchronized (ServiceRegistry.class) {
            wguardInstance = null;
            captchaInstance = null;
            lifecycleInstance = null;
            captivityInstance = null;
            subdivisionInstance = null;
            trackerInstance = null;
            awardsInstance = null;
            militaryIdInstance = null;
        }
    }
}
