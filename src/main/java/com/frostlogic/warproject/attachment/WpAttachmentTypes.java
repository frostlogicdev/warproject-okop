package com.frostlogic.warproject.attachment;

import com.frostlogic.warproject.WarProject;
import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Deferred register for all player attachment types used by WarProject.
 * <p>
 * Attachments are serialized to NBT (player data) and restored on
 * {@code PlayerEvent.Clone} when {@code wasDeath=true}.
 * <p>
 * For nullable-by-nature attachments (FACTION, REGION, RP_NAME), we use
 * {@code Optional<T>} wrappers so that the Codec can properly handle
 * the "not set" case without NPE during serialization.
 * <p>
 * Design reference: §4.3
 * Requirements: 13.3, 21.1
 */
public final class WpAttachmentTypes {

    public static final DeferredRegister<AttachmentType<?>> REG =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, WarProject.MOD_ID);

    /** Current lifecycle state of the player (design §5.1). */
    public static final Supplier<AttachmentType<PlayerState>> PLAYER_STATE = REG.register(
            "player_state",
            () -> AttachmentType.builder(() -> PlayerState.NEW)
                    .serialize(PlayerState.CODEC)
                    .build()
    );

    /** Faction the player belongs to (empty if not yet chosen). */
    public static final Supplier<AttachmentType<Optional<FactionId>>> FACTION = REG.register(
            "faction",
            () -> AttachmentType.builder(Optional::<FactionId>empty)
                    .serialize(FactionId.CODEC.optionalFieldOf("value").codec())
                    .build()
    );

    /** Administrative role of the player. */
    public static final Supplier<AttachmentType<Role>> ROLE = REG.register(
            "role",
            () -> AttachmentType.builder(() -> Role.CANDIDATE)
                    .serialize(Role.CODEC)
                    .build()
    );

    /** Military rank within the role (string identifier from config). */
    public static final Supplier<AttachmentType<String>> RANK = REG.register(
            "rank",
            () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING)
                    .build()
    );

    /** Reference to the base region the player is currently inside (empty if outside all regions). */
    public static final Supplier<AttachmentType<Optional<RegionRef>>> REGION = REG.register(
            "region",
            () -> AttachmentType.builder(Optional::<RegionRef>empty)
                    .serialize(RegionRef.CODEC.optionalFieldOf("value").codec())
                    .build()
    );

    /** Whether the player is marked as a collaborator. */
    public static final Supplier<AttachmentType<Boolean>> COLLABORATOR = REG.register(
            "collaborator",
            () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build()
    );

    /** Whether the player's passport has been captured (prisoner). */
    public static final Supplier<AttachmentType<Boolean>> CAPTURED = REG.register(
            "captured",
            () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build()
    );

    /** Counter of consecutive ticks spent in enemy territory. */
    public static final Supplier<AttachmentType<EnemyTickCounter>> ENEMY_TICKS = REG.register(
            "enemy_ticks",
            () -> AttachmentType.builder(() -> EnemyTickCounter.ZERO)
                    .serialize(EnemyTickCounter.CODEC)
                    .build()
    );

    /** Roleplay name (first name + surname; empty if not yet set). */
    public static final Supplier<AttachmentType<Optional<RpName>>> RP_NAME = REG.register(
            "rp_name",
            () -> AttachmentType.builder(Optional::<RpName>empty)
                    .serialize(RpName.CODEC.optionalFieldOf("value").codec())
                    .build()
    );

    /** Whether the RP name has been locked (cannot be changed without admin override). */
    public static final Supplier<AttachmentType<Boolean>> RP_NAME_LOCKED = REG.register(
            "rp_name_locked",
            () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .build()
    );

    private WpAttachmentTypes() {
        // utility class — no instantiation
    }
}
